package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.shift.detail.model.RouteStep
import org.hikyaku.mobile.shift.model.AddablePackage
import org.hikyaku.mobile.supabase.SupabaseTables
import org.maplibre.spatialk.geojson.Point

/**
 * The edit side of an (ad-hoc) shift: rescheduling its start, and adding or removing stops. Mirrors
 * the web dashboard's in-place route adjustment (`adjustRoute`) — direct table writes rather than a
 * re-optimisation — so the same lock rules apply: delivered/in-transit stops must not be removed
 * (enforced by the caller, which knows each stop's status).
 *
 * Row Level Security must permit the signed-in user to UPDATE `vrp_optimization` and
 * INSERT/UPDATE/DELETE `vrp_route_step` for their organisation's shifts (the web action relies on
 * the same `packages.edit`-granted policies).
 */
class ShiftEditRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /** Sets the shift's scheduled start to [isoStart] (ISO-8601). */
    suspend fun reschedule(shiftId: String, isoStart: String): Result<Unit> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .update(ScheduledStartUpdate(isoStart)) { filter { eq("id", shiftId) } }
        Unit
    }

    /**
     * Removes the job step [step] from [routeId] and renumbers the remaining steps so their
     * `step_index` stays contiguous. Callers must have already checked the stop is deletable
     * (not delivered/in transit).
     */
    suspend fun removeStop(routeId: String, step: RouteStep, allSteps: List<RouteStep>): Result<Unit> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_ROUTE_STEP)
            .delete { filter { eq("id", step.id); eq("route_id", routeId) } }
        val remaining = allSteps.filterNot { it.id == step.id }.sortedBy { it.stepIndex }
        renumber(remaining.map { it.id })
        Unit
    }

    /**
     * The org's packages with no shift yet (`optimisation_id IS NULL`), offered as stops that can be
     * added to an in-progress or upcoming shift. Ordered newest first. Packages without a usable
     * geocoded receiver address are excluded, since their coordinates are needed to place the new
     * route step.
     */
    suspend fun fetchAddablePackages(orgId: String): Result<List<AddablePackage>> = runCatching {
        client.postgrest.from(SupabaseTables.PACKAGES)
            .select(
                Columns.raw(
                    "id, tracking_number, to_customer, " +
                        "customer:customer!packages_to_customer_fkey(" +
                        "customer_name, customer_address, customer_location)",
                ),
            ) {
                filter {
                    eq("organisation_id", orgId)
                    exact("optimisation_id", null)
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<AddablePackageRow>()
            .mapNotNull { it.toAddable() }
    }

    /**
     * Adds [packageId] as a new delivery stop at ([longitude], [latitude]) to [routeId], inserted
     * just before the end (return) step, then renumbers. Also records the new stop in the shift's
     * stored optimisation request (`meta.customerByJob` + `jobs`) so its recipient ([customerId], the
     * package's `to_customer`) resolves on the detail screen, and marks the package as taken
     * (`optimisation_id = shiftId`) so it drops out of [fetchAddablePackages] and the create-shift
     * wizard's picker. [allSteps] are the route's current steps (for ordering + the solution id).
     */
    suspend fun addStop(
        shiftId: String,
        routeId: String,
        packageId: String,
        customerId: String,
        longitude: Double,
        latitude: Double,
        allSteps: List<RouteStep>,
    ): Result<Unit> = runCatching {
        val solutionId = allSteps.firstNotNullOfOrNull { it.solutionId }
            ?: error("Route has no solution id; cannot add a stop.")
        // Insert at a temporary out-of-range index to avoid the UNIQUE(route_id, step_index)
        // constraint; a re-fetch + renumber() then places it just before the end step.
        val tempIndex = (allSteps.maxOfOrNull { it.stepIndex } ?: 0) + 1000
        client.postgrest.from(SupabaseTables.VRP_ROUTE_STEP)
            .insert(
                RouteStepInsert(
                    routeId = routeId,
                    solutionId = solutionId,
                    stepIndex = tempIndex,
                    type = "job",
                    location = "SRID=4326;POINT($longitude $latitude)",
                ),
            )

        // Re-fetch the (now larger) step set; the new job sorts last among jobs (its temp index is
        // the largest), so ordering start → jobs → end places it immediately before the return leg.
        val steps = client.postgrest.from(SupabaseTables.VRP_ROUTE_STEP)
            .select(Columns.raw("id, type, step_index")) {
                filter { eq("route_id", routeId) }
                order("step_index", Order.ASCENDING)
            }
            .decodeList<MiniStep>()
        val ordered = buildList {
            steps.filter { it.type.equals("start", ignoreCase = true) }.forEach { add(it.id) }
            steps.filter { it.type.equals("job", ignoreCase = true) }.forEach { add(it.id) }
            steps.filter { it.type.equals("end", ignoreCase = true) }.forEach { add(it.id) }
        }
        renumber(ordered)
        appendCustomerToRequest(shiftId, customerId, longitude, latitude)
        client.postgrest.from(SupabaseTables.PACKAGES)
            .update(PackageOptimisationUpdate(shiftId)) { filter { eq("id", packageId) } }
        Unit
    }

    /**
     * Renumbers the given steps (in the desired final order) to contiguous `step_index` 0..n-1.
     * Two-phase to sidestep the UNIQUE(route_id, step_index) constraint: first park every row at a
     * distinct negative index, then assign the final indices.
     */
    private suspend fun renumber(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { i, id -> setStepIndex(id, -(i + 1)) }
        orderedIds.forEachIndexed { i, id -> setStepIndex(id, i) }
    }

    private suspend fun setStepIndex(stepId: Long, index: Int) {
        client.postgrest.from(SupabaseTables.VRP_ROUTE_STEP)
            .update(StepIndexUpdate(index)) { filter { eq("id", stepId) } }
    }

    /**
     * Appends the added stop's `(jobId → customerId)` mapping and job coordinate to the shift's
     * stored optimisation request, so [ShiftDetailRepository.fetchShiftMeta] resolves the recipient.
     * Best-effort: a shift without a recoverable request just leaves the new stop unnamed.
     */
    private suspend fun appendCustomerToRequest(
        shiftId: String,
        customerId: String,
        longitude: Double,
        latitude: Double,
    ) {
        val request = client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .select(Columns.raw("request")) { filter { eq("id", shiftId) } }
            .decodeSingleOrNull<EditRequestRow>()
            ?.request
            ?: return

        val jobs = (request["jobs"] as? JsonArray) ?: JsonArray(emptyList())
        val meta = (request["meta"] as? JsonObject) ?: JsonObject(emptyMap())
        val customerByJob = (meta["customerByJob"] as? JsonObject) ?: JsonObject(emptyMap())
        val nextJobId = (jobs.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull }
            .maxOrNull() ?: 0) + 1

        val newJobs = JsonArray(
            jobs + buildJsonObject {
                put("id", nextJobId)
                put("location", buildJsonArray { add(longitude); add(latitude) })
            },
        )
        val newCustomerByJob = JsonObject(customerByJob + (nextJobId.toString() to JsonPrimitive(customerId)))
        val newMeta = JsonObject(meta + ("customerByJob" to newCustomerByJob))
        val newRequest = JsonObject(request + ("jobs" to newJobs) + ("meta" to newMeta))

        client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .update(RequestUpdate(newRequest)) { filter { eq("id", shiftId) } }
    }
}

@Serializable
private data class ScheduledStartUpdate(
    @SerialName("scheduled_start") val scheduledStart: String,
)

@Serializable
private data class PackageOptimisationUpdate(
    @SerialName("optimisation_id") val optimisationId: String,
)

@Serializable
private data class AddablePackageRow(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("to_customer") val toCustomer: String,
    val customer: AddablePackageCustomerEmbed? = null,
) {
    /** Null when the receiver has no usable geocoded location — nothing to place a route step at. */
    fun toAddable(): AddablePackage? {
        val location = customer?.location ?: return null
        return AddablePackage(
            packageId = id,
            trackingNumber = trackingNumber,
            receiverName = customer.name?.takeIf { it.isNotBlank() } ?: "Unknown recipient",
            receiverAddress = customer.address.orEmpty(),
            receiverCustomerId = toCustomer,
            longitude = location.longitude,
            latitude = location.latitude,
        )
    }
}

@Serializable
private data class AddablePackageCustomerEmbed(
    @SerialName("customer_name") val name: String? = null,
    @SerialName("customer_address") val address: String? = null,
    @SerialName("customer_location") val location: Point? = null,
)

@Serializable
private data class StepIndexUpdate(
    @SerialName("step_index") val stepIndex: Int,
)

@Serializable
private data class RouteStepInsert(
    @SerialName("route_id") val routeId: String,
    @SerialName("solution_id") val solutionId: String,
    @SerialName("step_index") val stepIndex: Int,
    val type: String,
    /** EWKT (`SRID=4326;POINT(lng lat)`), cast to the geography column on insert. */
    val location: String,
    @SerialName("package_id") val packageId: String? = null,
)

@Serializable
private data class MiniStep(
    val id: Long,
    val type: String,
    @SerialName("step_index") val stepIndex: Int,
)

@Serializable
private data class EditRequestRow(val request: JsonObject? = null)

@Serializable
private data class RequestUpdate(val request: JsonObject)
