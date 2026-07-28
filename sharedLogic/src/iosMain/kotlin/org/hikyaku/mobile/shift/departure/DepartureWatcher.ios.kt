package org.hikyaku.mobile.shift.departure

import org.hikyaku.mobile.shift.departure.model.PendingDeparture

/**
 * iOS stub. A real implementation would use `CLLocationManager` region monitoring plus
 * `CMMotionActivityManager`; until then the auto-start safety net is Android-only and arming is a
 * no-op here.
 */
actual class DepartureWatcher actual constructor() {
    actual fun arm(pending: PendingDeparture) {}
    actual fun disarm() {}
}
