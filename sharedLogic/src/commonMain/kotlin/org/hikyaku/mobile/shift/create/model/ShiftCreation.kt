package org.hikyaku.mobile.shift.create.model

import org.hikyaku.mobile.geocode.model.AddressSuggestion

/**
 * A vehicle the user can pick, scoped to the current org. [label] is `vehicles.vehicle_model`.
 * [vehicleTypeId] is the vehicle's `vehicle_type.id`; the adhoc optimiser resolves the routing
 * profile itself from the submitted `vehicleId`, so this is only used client-side to reject vehicles
 * with no type recorded before submitting (nullable when the vehicle has no type recorded, in which
 * case it can't run a shift).
 */
data class VehicleOption(
    val id: String,
    val label: String,
    val vehicleTypeId: String?,
)

/** A persisted starting location ("home base"). [lat]/[lng] are needed as the route depot. */
data class WarehouseOption(val id: String, val name: String, val address: String, val lat: Double, val lng: Double)

/**
 * A previously-saved customer surfaced while the user types a name, so an earlier delivery's
 * phone + geocoded [address] can be reused. [address] is null only for records missing a usable
 * location (those aren't suggested).
 */
data class CustomerSuggestion(
    val name: String,
    val phoneE164: String?,
    val address: AddressSuggestion?,
)

/**
 * One delivery in the shift, in visit order. [phoneE164] is pre-validated/null; [address] carries
 * the geocoded `[lng, lat]` used both as the recipient's location and as a routing stop.
 */
data class ShiftCustomerInput(
    val name: String,
    val phoneE164: String?,
    val address: AddressSuggestion,
)

/**
 * A `packages` row the wizard can attach to the shift, either picked from the org's existing
 * unassigned packages (`optimisation_id IS NULL`) at the chosen warehouse, or one just created
 * inline. [receiverName]/[receiverAddress] come from the package's `to_customer` embed, so the
 * picker can show who each package is going to without a separate lookup.
 */
data class SelectablePackage(
    val id: String,
    val trackingNumber: String,
    val receiverName: String,
    val receiverAddress: String,
)

/**
 * Everything the backend `POST /api/v1/optimisation/adhoc` endpoint needs to optimise and persist a
 * shift: the [vehicleId], start depot [warehouseId] (`startingLocationId`), [startDateTime], and the
 * [packageIds] to deliver. Every package must already exist (created fresh or picked from the org's
 * unassigned packages) at [warehouseId] — the backend links them to the new `vrp_optimization` rather
 * than creating packages itself. `driverId` isn't carried here — [CreateShiftRepository.submitShift]
 * fills it in from the caller's own session, since the shift is always created for the driver
 * submitting it.
 */
data class ShiftSubmission(
    val orgId: String,
    val orgSlug: String,
    val warehouseId: String,            // startingLocationId — warehouse.id (start/end depot)
    val vehicleId: String,              // vehicles.id (also resolves the routing profile server-side)
    val startDateTime: String,          // ISO-8601 timestamp the vehicle sets off
    val packageIds: List<String>,
)
