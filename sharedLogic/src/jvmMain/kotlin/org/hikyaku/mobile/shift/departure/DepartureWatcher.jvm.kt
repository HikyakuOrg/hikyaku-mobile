package org.hikyaku.mobile.shift.departure

import org.hikyaku.mobile.shift.departure.model.PendingDeparture

/** Desktop has no auto-start safety net; arming is a no-op. */
actual class DepartureWatcher actual constructor() {
    actual fun arm(pending: PendingDeparture) {}
    actual fun disarm() {}
}
