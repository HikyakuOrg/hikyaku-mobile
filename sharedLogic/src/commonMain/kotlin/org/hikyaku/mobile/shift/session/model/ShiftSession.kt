package org.hikyaku.mobile.shift.session.model

import kotlinx.serialization.Serializable

/** The lifecycle of a running shift, persisted locally so it survives process death. */
enum class ShiftPhase {
    /** No package is in transit yet (a session in this phase is not normally persisted). */
    NOT_STARTED,

    /** The shift is running and stops are still being delivered. */
    IN_PROGRESS,

    /** Every stop has been delivered; the driver is heading back to the depot. */
    RETURNING_TO_DEPOT,

    /** Deliveries are done and the driver is back at the depot. The session can be cleared. */
    COMPLETE,
}

/**
 * The locally-persisted state machine for a running shift. It is the source of truth for the
 * bits that can't be reconstructed from the backend after a kill (which shift/route is active,
 * the phase, the in-transit stop and the optimistic status overlay), so on relaunch the app can
 * return to the last known shift and resume tracking.
 *
 * Authoritative package statuses still come from the backend; [statusOverrides] only layers the
 * optimistic values set just before a kill. [depotLat]/[depotLng] are denormalised here so the
 * Android foreground service can compute depot proximity after a `START_STICKY` restart without
 * re-fetching the route's steps.
 */
@Serializable
data class ShiftSession(
    val shiftId: String,
    val routeId: String,
    val orgSlug: String,
    val phase: ShiftPhase,
    /** The package currently being driven to, or null when none is in transit. */
    val inTransitPackageId: String? = null,
    /** Optimistic status per packageId (`IN_TRANSIT`/`DELIVERED`). */
    val statusOverrides: Map<String, String> = emptyMap(),
    /** True once every stop has been delivered. */
    val deliveriesComplete: Boolean = false,
    /** Depot latitude, cached for the headless service's proximity check. */
    val depotLat: Double? = null,
    /** Depot longitude, cached for the headless service's proximity check. */
    val depotLng: Double? = null,
    /** Wall-clock time (ISO-8601) tracking began, so a completed shift can look up its own breadcrumb trail. */
    val startedAt: String? = null,
    /** Wall-clock time (ISO-8601) the shift reached [ShiftPhase.COMPLETE], for the same lookup. */
    val endedAt: String? = null,
) {
    /** True while this session represents an active shift the app should resume. */
    val isActive: Boolean get() = phase == ShiftPhase.IN_PROGRESS || phase == ShiftPhase.RETURNING_TO_DEPOT
}
