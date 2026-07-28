package org.hikyaku.mobile.shift.tracking

import android.content.Intent
import android.os.Build
import org.hikyaku.mobile.AppContextHolder
import org.hikyaku.mobile.shift.session.model.ShiftSession

/**
 * Controls the Android foreground service that does background shift tracking. The service class
 * lives in the app module, so it's addressed by fully-qualified name to avoid a module cycle; the
 * service reads the active session straight from [org.hikyaku.mobile.shift.session.ShiftSessionStore],
 * so nothing needs to be passed through the [Intent].
 */
actual class ShiftTracker actual constructor() {
    actual val handlesLocationStreaming: Boolean = true

    actual fun start(session: ShiftSession) {
        val context = AppContextHolder.context
        val intent = Intent().setClassName(context.packageName, SERVICE_CLASS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun stop() {
        val context = AppContextHolder.context
        context.stopService(Intent().setClassName(context.packageName, SERVICE_CLASS))
    }

    private companion object {
        const val SERVICE_CLASS = "org.hikyaku.mobile.shift.ShiftTrackingService"
    }
}
