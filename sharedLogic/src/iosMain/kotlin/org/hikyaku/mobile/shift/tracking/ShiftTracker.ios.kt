package org.hikyaku.mobile.shift.tracking

import org.hikyaku.mobile.shift.session.model.ShiftSession

/**
 * iOS stub. A real implementation should drive `CLLocationManager` with
 * `allowsBackgroundLocationUpdates`; until then the ViewModel streams location in-process and the
 * shift simply isn't tracked in the background on iOS.
 */
actual class ShiftTracker actual constructor() {
    actual val handlesLocationStreaming: Boolean = false
    actual fun start(session: ShiftSession) {}
    actual fun stop() {}
}
