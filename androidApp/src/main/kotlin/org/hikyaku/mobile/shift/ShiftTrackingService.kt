package org.hikyaku.mobile.shift

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.hikyaku.mobile.shift.detail.haversineMeters
import org.hikyaku.mobile.shift.location.LocationProvider
import org.hikyaku.mobile.shift.session.ShiftSessionStore
import org.hikyaku.mobile.shift.session.WAREHOUSE_RADIUS_METERS
import org.hikyaku.mobile.shift.session.model.ShiftPhase
import kotlin.time.Clock

/**
 * Foreground service that keeps a shift tracked while the app is backgrounded: it streams the
 * driver's location to the backend and, once deliveries are done, completes the shift when the
 * driver returns to the depot.
 *
 * It is the single uploader of location on Android (the ViewModel does not stream while this
 * runs). It reads the active shift from [ShiftSessionStore] rather than the launch [Intent], so
 * a `START_STICKY` restart after a process kill resumes tracking with no extra wiring — this is
 * the background half of resume-on-kill.
 */
class ShiftTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val locationProvider = LocationProvider()
    private val actionsRepository = ShiftActionsRepository()
    private val sessionStore = ShiftSessionStore()
    private var collectJob: Job? = null

    /** Tracks whether the notification has already been switched to its "returning" copy, so a
     *  location update doesn't re-post it on every fix once deliveries are complete. */
    private var notifiedReturning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = sessionStore.load()
        notifiedReturning = session?.deliveriesComplete == true
        // Must move to the foreground promptly, before doing any location work.
        startForegroundNotification(returning = notifiedReturning)

        if (session == null || !session.isActive) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        if (collectJob == null) {
            collectJob = scope.launch { trackLocation() }
        }
        // Re-read the session from disk after a kill so the OS restart resumes the shift.
        return START_STICKY
    }

    private suspend fun trackLocation() {
        locationProvider.locationUpdates().collect { loc ->
            // A transient failure (e.g. auth not yet restored after a cold restart) is tolerated;
            // the breadcrumb history can miss a few early fixes.
            runCatching { actionsRepository.updateLocation(loc.lat, loc.lng, loc.speed) }

            val session = sessionStore.load() ?: return@collect
            // All packages are delivered but the driver hasn't reached the depot yet — swap the
            // notification copy so it no longer reads "Shift in progress" once deliveries are done.
            if (session.deliveriesComplete && !notifiedReturning) {
                updateNotification(returning = true)
                notifiedReturning = true
            }
            val depotLat = session.depotLat
            val depotLng = session.depotLng
            if (session.deliveriesComplete && depotLat != null && depotLng != null) {
                val meters = haversineMeters(loc.lat, loc.lng, depotLat, depotLng)
                if (meters <= WAREHOUSE_RADIUS_METERS) {
                    // Publishes COMPLETE to any open screen; then the service is done.
                    sessionStore.save(session.copy(phase = ShiftPhase.COMPLETE, endedAt = Clock.System.now().toString()))
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    private fun startForegroundNotification(returning: Boolean) {
        createChannel()
        val notification = buildNotification(returning)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(returning: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(returning))
    }

    private fun buildNotification(returning: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (returning) "Returning to depot" else "Shift in progress")
            .setContentText(
                if (returning) {
                    "Tracking your location until you're back at the depot."
                } else {
                    "Tracking your location for the active shift."
                },
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Shift tracking",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "shift_tracking"
        const val NOTIFICATION_ID = 1001
    }
}
