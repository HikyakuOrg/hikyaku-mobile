package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.StorageItem
import io.github.jan.supabase.storage.authenticatedStorageItem
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.shift.detail.model.Customer
import org.hikyaku.mobile.shift.detail.model.RouteStep
import org.hikyaku.mobile.shift.detail.model.ShiftMeta
import org.hikyaku.mobile.shift.detail.model.ShiftSolutionRoutes
import org.hikyaku.mobile.shift.detail.model.VrpRoute
import org.hikyaku.mobile.shift.detail.model.coordKey
import org.hikyaku.mobile.supabase.SupabaseBuckets
import org.hikyaku.mobile.supabase.SupabaseTables

/**
 * Loads the detail beneath a shift: its routes, the ordered stops/packages on a route, and
 * the proof-of-delivery photos for a package. Mirrors the web dashboard's shift-detail
 * queries (`getRouteSteps`) and storage access (`listPackageFiles` + signed URLs).
 */
class ShiftDetailRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /**
     * The dispatcher-set scheduled start time (ISO-8601) for [shiftId], or null if unscheduled.
     * Used by the auto-start safety net to decide whether the shift is eligible "now".
     */
    suspend fun fetchShiftSchedule(shiftId: String): Result<String?> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .select(Columns.raw("scheduled_start")) { filter { eq("id", shiftId) } }
            .decodeSingleOrNull<ScheduledStartRow>()
            ?.scheduledStart
    }

    /**
     * Recovers the recipient + vehicle detail for an ad-hoc [shiftId] from its stored optimisation
     * request. Ad-hoc route steps carry no package/customer link (see [ShiftMeta]); the mapping
     * lives in `vrp_optimization.request.meta.customerByJob` (job id → customer id) and
     * `request.jobs[].location` (job id → coordinate). We resolve the customers and the vehicle
     * type, then key each recipient by its job coordinate so the caller can match it onto a stop.
     * Returns an empty [ShiftMeta] (not a failure) when the request has no recoverable mapping, so
     * a package-backed shift simply falls back to its embedded assignment.
     */
    suspend fun fetchShiftMeta(shiftId: String): Result<ShiftMeta> = runCatching {
        val request = client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .select(Columns.raw("request")) { filter { eq("id", shiftId) } }
            .decodeSingleOrNull<OptimizationRequestRow>()
            ?.request
            ?: return@runCatching ShiftMeta()

        val meta = request["meta"] as? JsonObject
        val customerByJob: Map<Int, String> = (meta?.get("customerByJob") as? JsonObject)
            ?.mapNotNull { (jobId, customerId) ->
                val id = jobId.toIntOrNull() ?: return@mapNotNull null
                val cust = (customerId as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                id to cust
            }
            ?.toMap()
            .orEmpty()
        val locationByJob: Map<Int, List<Double>> = (request["jobs"] as? JsonArray)
            ?.mapNotNull { element ->
                val job = element as? JsonObject ?: return@mapNotNull null
                val id = (job["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val loc = (job["location"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                    ?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                id to loc
            }
            ?.toMap()
            .orEmpty()

        val customerIds = customerByJob.values.distinct()
        val customersById = if (customerIds.isEmpty()) {
            emptyMap()
        } else {
            client.postgrest.from(SupabaseTables.CUSTOMER)
                .select(Columns.raw(CUSTOMER_COLUMNS)) { filter { isIn("id", customerIds) } }
                .decodeList<Customer>()
                .associateBy { it.id }
        }
        val recipientsByCoord = buildMap {
            customerByJob.forEach { (jobId, customerId) ->
                val loc = locationByJob[jobId] ?: return@forEach
                val customer = customersById[customerId] ?: return@forEach
                put(coordKey(loc[0], loc[1]), customer)
            }
        }

        val vehicleTypeId = (meta?.get("vehicleType") as? JsonPrimitive)?.contentOrNull
        val vehicleType = vehicleTypeId?.let { id ->
            client.postgrest.from(SupabaseTables.VEHICLE_TYPE)
                .select(Columns.raw("vehicle_type, ors_vehicle_type")) { filter { eq("id", id) } }
                .decodeSingleOrNull<VehicleTypeRow>()
        }

        ShiftMeta(
            recipientsByCoord = recipientsByCoord,
            vehicleLabel = vehicleType?.vehicleType,
            orsProfile = vehicleType?.orsVehicleType,
        )
    }

    /** The routes belonging to [shiftId] (a `vrp_optimization` id), via its `vrp_solution`. */
    suspend fun fetchRoutes(shiftId: String): Result<List<VrpRoute>> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_SOLUTION)
            .select(Columns.raw("id, vrp_route(id, duration, cost)")) {
                filter { eq("optimization_id", shiftId) }
            }
            .decodeList<ShiftSolutionRoutes>()
            .flatMap { it.routes }
    }

    /**
     * The ordered stops of [routeId], each `job` step carrying its package assignment
     * (vehicle + recipient customer). `location` comes back as GeoJSON.
     */
    suspend fun fetchRouteSteps(routeId: String): Result<List<RouteStep>> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_ROUTE_STEP)
            .select(Columns.raw(ROUTE_STEP_COLUMNS.replace(Regex("\\s"), ""))) {
                filter { eq("route_id", routeId) }
                order("step_index", Order.ASCENDING)
            }
            .decodeList<RouteStep>()
    }

    /**
     * [StorageItem]s for every photo under `packages/{packageId}` in the private `packages`
     * bucket, or an empty list when the package has none. Returns failure only on a real
     * error; a missing/empty folder yields an empty list.
     */
    suspend fun fetchPackageImages(packageId: String): Result<List<StorageItem>> = runCatching {
        val bucket = client.storage.from(SupabaseBuckets.PACKAGES)
        val files = bucket.list(packageId)
            .map { it.name }
            .filter { it != ".emptyFolderPlaceholder" }
        files.map { name -> authenticatedStorageItem(SupabaseBuckets.PACKAGES, "$packageId/$name") }
    }

    /**
     * Tracking numbers for [packageIds], keyed by package id. Fetched separately from
     * [fetchRouteSteps] since `packages_with_latest_status` (used there) doesn't expose
     * `tracking_number`; queries the base `packages` table directly instead.
     */
    suspend fun fetchTrackingNumbers(packageIds: List<String>): Result<Map<String, String>> = runCatching {
        if (packageIds.isEmpty()) return@runCatching emptyMap()
        client.postgrest.from(SupabaseTables.PACKAGES)
            .select(Columns.raw("id, tracking_number")) { filter { isIn("id", packageIds) } }
            .decodeList<PackageTrackingRow>()
            .associate { it.id to it.trackingNumber }
    }

    /**
     * Current status for [packageIds], read fresh from `packages_with_latest_status`. Used to
     * reconcile the load-scanning checklist: deliberately narrower than [fetchRouteSteps] so
     * refreshing scan progress never refetches the route, map line, POIs or photos. Packages with
     * no status row are omitted rather than mapped to null.
     */
    suspend fun fetchCurrentStatuses(packageIds: List<String>): Result<Map<String, String>> = runCatching {
        if (packageIds.isEmpty()) return@runCatching emptyMap()
        client.postgrest.from(SupabaseTables.PACKAGES_WITH_LATEST_STATUS)
            .select(Columns.raw("id, current_status")) { filter { isIn("id", packageIds) } }
            .decodeList<PackageCurrentStatusRow>()
            .mapNotNull { row -> row.currentStatus?.let { row.id to it } }
            .toMap()
    }

    private companion object {
        // Recipient columns pulled when resolving ad-hoc shift customers from the request meta.
        val CUSTOMER_COLUMNS = "id, customer_name, customer_phone, customer_address, " +
            "customer_suburb, customer_state, customer_postcode"

        // Mirrors the web dashboard's getRouteSteps select, trimmed to the fields the
        // mobile detail screen renders.
        val ROUTE_STEP_COLUMNS = """
            *,
            package_assignment(
                package_id,
                vehicle:vehicles(
                    id,
                    vehicle_plate,
                    vehicle_type:vehicle_type!vehicles_vehicle_type_fkey(ors_vehicle_type)
                ),
                package:packages_with_latest_status!package_assignment_package_id_fkey(
                    current_status,
                    to_customer:customer!packages_to_customer_fkey(
                        id,
                        customer_name,
                        customer_phone,
                        customer_address,
                        customer_suburb,
                        customer_state,
                        customer_postcode
                    )
                )
            )
        """.trimIndent()
    }
}

@Serializable
private data class ScheduledStartRow(
    @SerialName("scheduled_start") val scheduledStart: String? = null,
)

/** The raw VROOM request snapshot stored on `vrp_optimization`; only `meta`/`jobs` are read. */
@Serializable
private data class OptimizationRequestRow(
    val request: JsonObject? = null,
)

@Serializable
private data class VehicleTypeRow(
    @SerialName("vehicle_type") val vehicleType: String? = null,
    @SerialName("ors_vehicle_type") val orsVehicleType: String? = null,
)

@Serializable
private data class PackageTrackingRow(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String,
)

@Serializable
private data class PackageCurrentStatusRow(
    val id: String,
    @SerialName("current_status") val currentStatus: String? = null,
)
