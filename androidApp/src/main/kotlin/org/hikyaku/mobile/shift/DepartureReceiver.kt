package org.hikyaku.mobile.shift

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hikyaku.mobile.shift.departure.AutoStartCoordinator
import org.hikyaku.mobile.shift.departure.model.PendingDeparture
import org.hikyaku.mobile.shift.departure.toDetectedActivityType

/**
 * Receives the geofence and activity-transition broadcasts armed by
 * [org.hikyaku.mobile.shift.departure.DepartureWatcher]. It folds each signal into the persisted
 * [PendingDeparture] via [AutoStartCoordinator]; once the driver has been seen at the warehouse,
 * has left it, and is moving by the assigned vehicle's activity (all within the scheduled window),
 * the coordinator auto-starts the shift and this receiver notifies the driver.
 *
 * Fires even when the app is backgrounded or its process was killed, so the work runs under
 * [goAsync] and reads everything it needs from disk.
 */
class DepartureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geoEvent = GeofencingEvent.fromIntent(intent)
        val activityResult = if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult.extractResult(intent)
        } else {
            null
        }
        if (geoEvent == null && activityResult == null) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val outcome = AutoStartCoordinator().onSignal { current ->
                    applySignals(current, geoEvent, activityResult)
                }
                if (outcome == AutoStartCoordinator.Outcome.SHIFT_STARTED) {
                    notifyAutoStarted(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun applySignals(
        current: PendingDeparture,
        geoEvent: GeofencingEvent?,
        activityResult: ActivityTransitionResult?,
    ): PendingDeparture {
        var result = current
        if (geoEvent != null && !geoEvent.hasError()) {
            when (geoEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER,
                Geofence.GEOFENCE_TRANSITION_DWELL,
                -> result = result.copy(seenAtWarehouse = true)

                Geofence.GEOFENCE_TRANSITION_EXIT ->
                    result = result.copy(exitedGeofence = true)
            }
        }
        if (activityResult != null) {
            val wanted = current.activity.toDetectedActivityType()
            val entered = activityResult.transitionEvents.any { event ->
                event.activityType == wanted &&
                    event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            }
            if (entered) result = result.copy(movingDetected = true)
        }
        return result
    }

    private fun notifyAutoStarted(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Shift started automatically")
            .setContentText("You left the warehouse, so the first stop was marked in transit.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Shift auto-start",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }
    }

    private companion object {
        const val CHANNEL_ID = "shift_auto_start"
        const val NOTIFICATION_ID = 1002
    }
}
