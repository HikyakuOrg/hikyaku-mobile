package org.hikyaku.mobile.shift.detail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.maplibre.spatialk.geojson.Point

/**
 * One stop on a route (`vrp_route_step`), ordered by [stepIndex]. [type] is `start`,
 * `job`, or `end`; only `job` steps carry a delivery via [assignment]. [location] is the
 * stop's coordinates, used to draw the route line and markers. It decodes directly from the
 * GeoJSON `Point` that PostGIS `geometry`/`geography` columns are returned as by PostgREST
 * (`{"type":"Point","coordinates":[lng,lat]}`); read `longitude`/`latitude` off it.
 */
@Serializable
data class RouteStep(
    val id: Long,
    @SerialName("step_index") val stepIndex: Int,
    val type: String,
    val location: Point? = null,
    val arrival: Int? = null,
    @SerialName("solution_id") val solutionId: String? = null,
    @SerialName("package_assignment") val assignment: PackageAssignment? = null,
) {
    val isJob: Boolean get() = type.equals("job", ignoreCase = true)
}

/** The driver/vehicle/package attached to a `job` step via `package_assignment`. */
@Serializable
data class PackageAssignment(
    @SerialName("package_id") val packageId: String? = null,
    val vehicle: Vehicle? = null,
    @SerialName("package") val packageInfo: PackageInfo? = null,
)

@Serializable
data class Vehicle(
    val id: String? = null,
    @SerialName("vehicle_plate") val plate: String? = null,
    @SerialName("vehicle_type") val vehicleType: VehicleType? = null,
)

@Serializable
data class VehicleType(
    @SerialName("ors_vehicle_type") val orsVehicleType: String? = null,
)

/** A package row from the `packages_with_latest_status` view. */
@Serializable
data class PackageInfo(
    @SerialName("current_status") val currentStatus: String? = null,
    @SerialName("to_customer") val toCustomer: Customer? = null,
)

/** The delivery recipient (`customer`). */
@Serializable
data class Customer(
    val id: String? = null,
    @SerialName("customer_name") val name: String? = null,
    @SerialName("customer_phone") val phone: String? = null,
    @SerialName("customer_address") val address: String? = null,
    @SerialName("customer_suburb") val suburb: String? = null,
    @SerialName("customer_state") val state: String? = null,
    @SerialName("customer_postcode") val postcode: String? = null,
) {
    /**
     * Single-line address: just the street/venue line from [address] (its part before the
     * first comma) plus [suburb] and [state]/[postcode]. [address] is often a full geocoded
     * string that already repeats the suburb/state/postcode, so only its first segment is used
     * to avoid showing that twice.
     */
    val fullAddress: String
        get() {
            val line = address?.substringBefore(',')?.trim().orEmpty()
            val stateAndPostcode = listOfNotNull(state?.takeIf { it.isNotBlank() }, postcode?.takeIf { it.isNotBlank() })
                .joinToString(" ")
            return listOfNotNull(
                line.takeIf { it.isNotBlank() },
                suburb?.takeIf { it.isNotBlank() },
                stateAndPostcode.takeIf { it.isNotBlank() },
            ).joinToString(", ")
        }
}
