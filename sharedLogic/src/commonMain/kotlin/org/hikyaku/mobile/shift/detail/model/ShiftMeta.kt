package org.hikyaku.mobile.shift.detail.model

/**
 * Recipient + vehicle detail recovered from an ad-hoc shift's stored optimisation request.
 *
 * Shifts created through the mobile create-shift flow use the backend's ad-hoc optimiser, which
 * writes `vrp_route_step` rows with `package_id = null` (no `packages`/`package_assignment`/
 * `customer` link). The customer↔job and vehicle mapping instead lives in
 * `vrp_optimization.request.meta`, so the detail screen resolves recipients and the vehicle from
 * there and matches them onto the ordered stops by coordinate.
 */
data class ShiftMeta(
    /** Recipient customer keyed by the [coordKey] of its job-stop coordinate. */
    val recipientsByCoord: Map<String, Customer> = emptyMap(),
    /** Human label of the vehicle type used (e.g. "Car", "Bicycle"), or null if unknown. */
    val vehicleLabel: String? = null,
    /** ORS routing profile of the vehicle (e.g. "driving-car"), or null. */
    val orsProfile: String? = null,
)

/**
 * A stable string key for a `[lng, lat]` coordinate, rounded to ~1 m, so a route step's stored
 * location matches the job location it originated from despite float formatting differences.
 */
fun coordKey(lng: Double, lat: Double): String {
    fun round(v: Double): Long = kotlin.math.round(v * 1e5).toLong()
    return "${round(lng)},${round(lat)}"
}
