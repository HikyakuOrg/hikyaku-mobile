package org.hikyaku.mobile.shift.tracking

import org.hikyaku.mobile.shift.session.model.ShiftSession

/** Desktop has no background tracking; the ViewModel streams location in-process instead. */
actual class ShiftTracker actual constructor() {
    actual val handlesLocationStreaming: Boolean = false
    actual fun start(session: ShiftSession) {}
    actual fun stop() {}
}
