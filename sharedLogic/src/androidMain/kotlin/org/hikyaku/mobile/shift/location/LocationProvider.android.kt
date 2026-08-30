package org.hikyaku.mobile.shift.location

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.hikyaku.mobile.AppContextHolder
import org.hikyaku.mobile.shift.location.model.DeviceLocation

private const val MIN_TIME_MS = 5_000L
private const val MIN_DISTANCE_M = 10f
private const val CURRENT_LOCATION_TIMEOUT_MS = 5_000L

/**
 * Emits locations from the fused location provider (Google Play services), which fuses GPS,
 * network and sensors for better accuracy and battery use than the raw `LocationManager`.
 * Requires a location permission to have been granted by the caller; a [SecurityException]
 * otherwise closes the flow.
 */
actual class LocationProvider {
    @SuppressLint("MissingPermission")
    actual fun locationUpdates(): Flow<DeviceLocation> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(AppContextHolder.context)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MIN_TIME_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    DeviceLocation(
                        lat = location.latitude,
                        lng = location.longitude,
                        speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                    ),
                )
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    actual suspend fun currentLocation(): DeviceLocation? {
        val client = LocationServices.getFusedLocationProviderClient(AppContextHolder.context)
        val cancellationSource = CancellationTokenSource()
        return try {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token).await()
            }?.let { location ->
                DeviceLocation(
                    lat = location.latitude,
                    lng = location.longitude,
                    speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                )
            }
        } catch (e: SecurityException) {
            null
        } finally {
            cancellationSource.cancel()
        }
    }
}
