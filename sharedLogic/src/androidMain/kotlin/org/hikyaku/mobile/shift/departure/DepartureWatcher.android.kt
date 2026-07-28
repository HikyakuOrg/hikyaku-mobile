package org.hikyaku.mobile.shift.departure

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.hikyaku.mobile.AppContextHolder
import org.hikyaku.mobile.shift.departure.model.DepartureActivity
import org.hikyaku.mobile.shift.departure.model.PendingDeparture
import org.hikyaku.mobile.shift.session.WAREHOUSE_DEPARTURE_RADIUS_METERS

/** Maps the abstract [DepartureActivity] to the Play services [DetectedActivity] constant. */
fun DepartureActivity.toDetectedActivityType(): Int = when (this) {
    DepartureActivity.IN_VEHICLE -> DetectedActivity.IN_VEHICLE
    DepartureActivity.ON_BICYCLE -> DetectedActivity.ON_BICYCLE
}

/**
 * Android auto-start watcher. Registers a warehouse geofence (ENTER/DWELL/EXIT) and an
 * activity-transition request for the assigned vehicle's activity, both delivering to
 * [org.hikyaku.mobile.shift.DepartureReceiver] via a broadcast [PendingIntent] so they fire even
 * when the app is backgrounded or its process has been killed. Requires location (incl. background)
 * and activity-recognition permissions to have been granted by the caller.
 */
actual class DepartureWatcher actual constructor() {
    private val context get() = AppContextHolder.context
    private val store = PendingDepartureStore()

    @SuppressLint("MissingPermission")
    actual fun arm(pending: PendingDeparture) {
        store.save(pending)

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(
                pending.depotLat,
                pending.depotLng,
                WAREHOUSE_DEPARTURE_RADIUS_METERS.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_DWELL or
                    Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()
        val geofencingRequest = GeofencingRequest.Builder()
            // Capture presence if the driver is already inside when we arm; never EXIT, so a stale
            // "already outside" state can't fire a spurious departure.
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL,
            )
            .addGeofence(geofence)
            .build()

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(pending.activity.toDetectedActivityType())
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
        )

        runCatching {
            LocationServices.getGeofencingClient(context)
                .addGeofences(geofencingRequest, geofencePendingIntent())
        }
        runCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(
                    ActivityTransitionRequest(transitions),
                    activityPendingIntent(),
                )
        }
    }

    actual fun disarm() {
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(listOf(GEOFENCE_ID)) }
        runCatching {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(activityPendingIntent())
        }
        store.clear()
    }

    private fun geofencePendingIntent(): PendingIntent = broadcast(REQUEST_GEOFENCE)

    private fun activityPendingIntent(): PendingIntent = broadcast(REQUEST_ACTIVITY)

    private fun broadcast(requestCode: Int): PendingIntent {
        // Addressed by class name to avoid a dependency on the app module (the receiver lives there).
        val intent = Intent().setClassName(context.packageName, RECEIVER_CLASS)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private companion object {
        const val RECEIVER_CLASS = "org.hikyaku.mobile.shift.DepartureReceiver"
        const val GEOFENCE_ID = "warehouse_departure"
        const val LOITERING_DELAY_MS = 30_000
        const val REQUEST_GEOFENCE = 2001
        const val REQUEST_ACTIVITY = 2002
    }
}
