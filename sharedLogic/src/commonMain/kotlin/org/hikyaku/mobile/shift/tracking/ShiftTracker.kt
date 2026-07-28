package org.hikyaku.mobile.shift.tracking

import org.hikyaku.mobile.shift.session.model.ShiftSession

/**
 * Platform seam for keeping a shift tracked while the app is backgrounded.
 *
 * On Android [start] launches a foreground service that owns location collection, upload and the
 * depot/completion check, so it survives the app being backgrounded and is restarted by the OS
 * after a kill. There [handlesLocationStreaming] is true, and the ViewModel must NOT also stream
 * location itself (that would double-upload).
 *
 * On iOS/desktop [handlesLocationStreaming] is false and start/stop are no-ops; those platforms
 * keep the existing in-ViewModel streaming with no background guarantees (unchanged behaviour).
 */
expect class ShiftTracker() {
    /** True when this platform handles location streaming via [start] (so the ViewModel shouldn't). */
    val handlesLocationStreaming: Boolean

    /** Begins background tracking for [session]. Idempotent: starting again just re-reads state. */
    fun start(session: ShiftSession)

    /** Stops background tracking and removes any ongoing notification. */
    fun stop()
}
