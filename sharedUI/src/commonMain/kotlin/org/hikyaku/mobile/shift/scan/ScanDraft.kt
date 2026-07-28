package org.hikyaku.mobile.shift.scan

/**
 * The scan-packages overlay's state; non-null on [org.hikyaku.mobile.shift.ShiftDetailViewModel.UiState]
 * while the overlay is open. Mirrors the `AddStopDraft` idiom (a nullable draft that exists only
 * while its surface is showing), split into its own file since `ShiftDetailViewModel.kt` is
 * already large.
 */
data class ScanDraft(
    /** Tracking number of the insert currently in flight, or null when idle. */
    val submitting: String? = null,
    /** Result of the most recent scan attempt, shown as a transient banner. */
    val feedback: ScanFeedback? = null,
    /** True while the scanned set is being re-read from the server. */
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val flashlightOn: Boolean = false,
    /** The manual tracking-number escape hatch; also the only input path on desktop. */
    val manualEntry: String = "",
    val manualExpanded: Boolean = false,
)

/** Outcome of a single scan (camera or manual entry), surfaced as feedback in the overlay. */
sealed interface ScanFeedback {
    data class Accepted(val trackingNumber: String) : ScanFeedback
    data class AlreadyScanned(val trackingNumber: String) : ScanFeedback
    data class NotOnThisShift(val trackingNumber: String) : ScanFeedback
    data class Unrecognised(val raw: String) : ScanFeedback

    /** The route's tracking numbers haven't finished loading yet; try again shortly. */
    data object NotReady : ScanFeedback

    /** The insert failed; [packageId] lets an inline Retry resubmit without re-scanning. */
    data class Failed(val trackingNumber: String, val packageId: String, val message: String) : ScanFeedback
}
