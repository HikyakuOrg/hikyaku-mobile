package org.hikyaku.mobile.shift.session

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Status strings shared by the running-shift code paths (the UI overlay and the service). */
object ShiftStatus {
    const val IN_TRANSIT = "IN_TRANSIT"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
    const val PENDING = "PENDING"
    const val ASSIGNED = "ASSIGNED"
    /** Set by scanning a package's QR code while loading the van, before the shift starts. */
    const val ONBOARD_FOR_DELIVERY = "ONBOARD_FOR_DELIVERY"

    /** Statuses meaning a package is at least physically on the van — onboard, or further along. */
    private val SCAN_SATISFYING = setOf(ONBOARD_FOR_DELIVERY, IN_TRANSIT, DELIVERED)

    /**
     * True once [status] is at or past "loaded into the van". Deliberately "at or past" rather
     * than exact equality, so a package that has already moved on to `IN_TRANSIT`/`DELIVERED`
     * still counts as scanned instead of reverting the load checklist to unscanned.
     */
    fun satisfiesScan(status: String?): Boolean =
        status != null && SCAN_SATISFYING.any { it.equals(status, ignoreCase = true) }
}

/** How close (metres) the driver must be to the depot for the shift to count as complete. */
const val WAREHOUSE_RADIUS_METERS = 120.0

/**
 * Radius (metres) of the warehouse-departure geofence that arms the auto-start safety net. Larger
 * than [WAREHOUSE_RADIUS_METERS] so leaving it is an unambiguous "the driver has set off" signal.
 * Non-configurable for now.
 */
const val WAREHOUSE_DEPARTURE_RADIUS_METERS = 200.0

/** How early before the scheduled start the auto-start safety net may arm/fire. */
val AUTO_START_WINDOW_BEFORE = 1.hours

/** How long after the scheduled start the auto-start safety net stays eligible. */
val AUTO_START_WINDOW_AFTER = 6.hours

/**
 * True when [now] falls within the auto-start eligibility window around an ISO-8601
 * [scheduledStart]. Returns false when [scheduledStart] is null or unparseable, keeping the
 * auto-start feature inert unless the dispatcher has actually scheduled the shift — this is what
 * stops a shift assigned for a future day from triggering when the driver merely drives home.
 */
fun isWithinAutoStartWindow(
    scheduledStart: String?,
    now: Instant = Clock.System.now(),
): Boolean {
    val start = scheduledStart?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    return now >= start - AUTO_START_WINDOW_BEFORE && now <= start + AUTO_START_WINDOW_AFTER
}
