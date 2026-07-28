package org.hikyaku.mobile.shift.departure.model

import kotlinx.serialization.Serializable

/** The activity-transition type the driver is expected to begin moving by when departing. */
enum class DepartureActivity { IN_VEHICLE, ON_BICYCLE }

/**
 * The locally-persisted context for the auto-start-on-warehouse-departure safety net, written when
 * the watcher is armed and read by the headless receiver when a geofence/activity signal fires. It
 * holds everything needed to start the shift without re-fetching the route, plus the running flags
 * that accumulate across the (independently delivered) detection signals.
 *
 * Auto-start only fires once [departureConfirmed] — the driver was seen *inside* the warehouse and
 * then *left* it while moving by [activity] — which is why merely driving home (never starting from
 * inside the warehouse) cannot trigger it.
 */
@Serializable
data class PendingDeparture(
    val shiftId: String,
    val routeId: String,
    val orgSlug: String,
    val firstPackageId: String,
    /**
     * Every package on the route, so the headless auto-start check can verify the load-scanning
     * gate without re-fetching the route. Defaults to empty so a record persisted by a previous
     * build still decodes; an empty list is treated as "gate not applicable" (see
     * [org.hikyaku.mobile.shift.departure.AutoStartCoordinator]), preserving old behaviour for it.
     */
    val packageIds: List<String> = emptyList(),
    val depotLat: Double,
    val depotLng: Double,
    /** ISO-8601 scheduled shift start; the auto-start window is evaluated against it. */
    val scheduledStart: String,
    val activity: DepartureActivity,
    /** Set once the driver is confirmed inside the warehouse geofence (ENTER/DWELL). */
    val seenAtWarehouse: Boolean = false,
    /** Set once the driver has left the warehouse geofence (EXIT). */
    val exitedGeofence: Boolean = false,
    /** Set once an ENTER transition into [activity] is detected. */
    val movingDetected: Boolean = false,
) {
    /** All non-time conditions are satisfied: present-at-warehouse, then departed while moving. */
    val departureConfirmed: Boolean get() = seenAtWarehouse && exitedGeofence && movingDetected
}
