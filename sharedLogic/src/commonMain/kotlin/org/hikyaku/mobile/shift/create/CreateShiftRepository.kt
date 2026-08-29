package org.hikyaku.mobile.shift.create

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.api.generated.models.AdhocOptimisationDto
import org.hikyaku.mobile.api.generated.models.AdhocOptimisationResultDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import org.hikyaku.mobile.shift.create.model.CustomerSuggestion
import org.hikyaku.mobile.shift.create.model.SelectablePackage
import org.hikyaku.mobile.shift.create.model.ShiftCustomerInput
import org.hikyaku.mobile.shift.create.model.ShiftSubmission
import org.hikyaku.mobile.shift.create.model.VehicleOption
import org.hikyaku.mobile.shift.create.model.WarehouseOption
import org.hikyaku.mobile.supabase.SupabaseTables
import org.maplibre.spatialk.geojson.Point

/**
 * Backs the personal-org "create shift" flow. It writes the reusable home-base [warehouse], then
 * hands the shift off to the backend `POST /api/v1/optimisation/adhoc` endpoint, which runs the
 * optimiser and persists the VROOM-schema solution server-side (solution, route, steps,
 * assignments) and returns the `vrp_optimization` id.
 *
 * The wizard attaches existing or newly-created `packages` rows (see
 * [org.hikyaku.mobile.packages.PackageRepository.createPackage]) rather than composing customers
 * itself — the adhoc request carries their ids alongside the driver, the vehicle (which also
 * resolves the routing profile server-side), the start/end warehouse and the departure time, and
 * the backend links each package's `optimisation_id` to the new run. RLS: the writes here require
 * the personal-org owner to hold the org's `warehouse.*` permission, which
 * `handle_new_organisation` grants the creator. The
 * optimisation call is authenticated with the caller's Supabase access token
 * (`Authorization: Bearer <jwt>`) and scoped with the `X-Organisation-Slug` header.
 */
class CreateShiftRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /**
     * The current org's vehicles (excluding soft-deleted), for the driver to pick which one runs the
     * shift. The label is `vehicle_model`; the adhoc optimiser's routing profile is resolved from the
     * vehicle's `vehicle_type.id`, embedded via the `vehicles_vehicle_type_fkey` foreign key.
     */
    suspend fun fetchVehicles(orgId: String): Result<List<VehicleOption>> = runCatching {
        client.postgrest.from(SupabaseTables.VEHICLES)
            .select(
                Columns.raw(
                    "id, vehicle_model, vehicle_plate, " +
                        "vehicle_type!vehicles_vehicle_type_fkey(id)",
                ),
            ) {
                filter {
                    eq("organisation_id", orgId)
                    eq("is_deleted", false)
                }
            }
            .decodeList<VehicleRow>()
            .map { it.toOption() }
    }

    /** The personal org's existing warehouses, so the user can re-select their home base. */
    suspend fun fetchWarehouses(orgId: String): Result<List<WarehouseOption>> = runCatching {
        client.postgrest.from(SupabaseTables.WAREHOUSE)
            .select(Columns.raw("id, warehouse_name, warehouse_address, warehouse_location")) {
                filter { eq("organisation_id", orgId) }
            }
            .decodeList<WarehouseRow>()
            .map { it.toOption() }
    }

    /**
     * The org's unassigned packages (`optimisation_id IS NULL`) sitting at [warehouseId], so the
     * wizard can offer them as ready-to-attach stops — a package must already be at the shift's
     * chosen depot for the single-vehicle route to make sense. Ordered newest first.
     */
    suspend fun fetchAvailablePackages(orgId: String, warehouseId: String): Result<List<SelectablePackage>> =
        runCatching {
            client.postgrest.from(SupabaseTables.PACKAGES)
                .select(
                    Columns.raw(
                        "id, tracking_number, " +
                            "to_customer:customer!packages_to_customer_fkey(customer_name, customer_address)",
                    ),
                ) {
                    filter {
                        eq("organisation_id", orgId)
                        eq("warehouse_id", warehouseId)
                        exact("optimisation_id", null)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<AvailablePackageRow>()
                .map { it.toSelectable() }
        }

    /**
     * Existing customers of [orgId] whose name matches [query] (case-insensitive substring), so the
     * user can reuse a returning customer's phone + address. The `customer` table accumulates a new
     * row per delivery, so results are de-duplicated by name + address and capped; the reusable
     * sender row and any records without a usable geocoded address are excluded.
     */
    suspend fun searchCustomers(orgId: String, query: String): Result<List<CustomerSuggestion>> = runCatching {
        client.postgrest.from(SupabaseTables.CUSTOMER)
            .select(
                Columns.raw(
                    "customer_name, customer_phone, customer_address, customer_suburb, " +
                        "customer_state, customer_postcode, customer_country, customer_location, " +
                        "geocode_confidence, pelias_gid",
                ),
            ) {
                filter {
                    eq("organisation_id", orgId)
                    ilike("customer_name", "%$query%")
                    neq("customer_name", SENDER_NAME)
                }
                limit(30)
            }
            .decodeList<CustomerRow>()
            .mapNotNull { it.toSuggestion() }
            .distinctBy { it.name.lowercase() + "|" + it.address?.label }
            .take(5)
    }

    /** Geocodes-and-persists a new home base from a chosen [address]. */
    suspend fun createWarehouse(orgId: String, name: String, address: AddressSuggestion): Result<WarehouseOption> =
        runCatching {
            val id = newId()
            client.postgrest.from(SupabaseTables.WAREHOUSE).insert(
                WarehouseInsert(
                    id = id,
                    warehouseName = name,
                    warehouseAddress = address.label,
                    warehouseLocation = pointEwkt(address.lon, address.lat),
                    warehouseCity = address.suburb ?: "",
                    warehouseState = address.state ?: "",
                    warehouseCountry = address.country ?: "",
                    warehouseZipcode = address.postcode ?: "",
                    organisationId = orgId,
                ),
            )
            WarehouseOption(id = id, name = name, address = address.label, lat = address.lat, lng = address.lon)
        }

    /**
     * Materialises the shift by handing it to the backend adhoc optimiser: the driver, vehicle,
     * start depot, departure time and the wizard's chosen package ids are POSTed to
     * `/api/v1/optimisation/adhoc`. Every package already exists (created fresh via
     * [org.hikyaku.mobile.packages.PackageRepository.createPackage] or picked from
     * [fetchAvailablePackages]), so the client's job is just to name them — the backend links each
     * package's `optimisation_id` to the new run. `driverId` is always the caller's own id
     * (`drivers.id` == `auth.users.id`) — the mobile app never lets a driver create a shift for
     * someone else. Returns the `vrp_optimization` id the endpoint reports — the shift's
     * server-side identity.
     */
    suspend fun submitShift(submission: ShiftSubmission): Result<String> = runCatching {
        val driverId = client.auth.currentUserOrNull()?.id ?: error("Session expired. Please sign in again.")

        val response = httpClient.post(ApiEndpoints.optimisationAdhoc(apiUrl())) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
            header(ApiHeaders.ORGANISATION_SLUG, submission.orgSlug)
            contentType(ContentType.Application.Json)
            setBody(
                AdhocOptimisationDto(
                    driverId = driverId,
                    vehicleId = submission.vehicleId,
                    startDateTime = submission.startDateTime,
                    startingLocationId = submission.warehouseId,
                    packages = submission.packageIds,
                ),
            )
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(300)
            if (response.status.value == 409) throw PackageConflictException(body)
            error("Optimisation failed (${response.status.value}): $body")
        }
        // The endpoint returns { id, routeId, unassignedPackageIds }; the id is the shift's identity.
        response.body<AdhocOptimisationResultDto>().id
    }

    /**
     * Public entry point for the add-stop flow: resolves (or creates) the recipient customer for
     * [customer] in [orgId] and returns its id together with the geocoded coordinates the caller
     * needs to place the new route step. Reuses the same returning-customer de-duplication as shift
     * creation.
     */
    suspend fun resolveCustomerForStop(orgId: String, customer: ShiftCustomerInput): Result<ResolvedStopCustomer> =
        runCatching {
            val id = ensureDeliveryCustomer(orgId, customer)
            ResolvedStopCustomer(
                customerId = id,
                longitude = customer.address.lon,
                latitude = customer.address.lat,
            )
        }

    private suspend fun ensureDeliveryCustomer(orgId: String, customer: ShiftCustomerInput): String {
        val phone = customer.phoneE164
        if (phone != null) {
            val existing = client.postgrest.from(SupabaseTables.CUSTOMER)
                .select(Columns.raw("id")) {
                    filter {
                        eq("organisation_id", orgId)
                        eq("customer_phone", phone)
                    }
                }
                .decodeList<IdRow>()
                .firstOrNull()
            if (existing != null) return existing.id
        }

        val customerId = newId()
        client.postgrest.from(SupabaseTables.CUSTOMER).insert(
            CustomerInsert(
                id = customerId,
                customerName = customer.name,
                customerPhone = phone,
                customerAddress = customer.address.label,
                customerSuburb = customer.address.suburb,
                customerState = customer.address.state,
                customerPostcode = customer.address.postcode,
                customerCountry = customer.address.country,
                customerLocation = pointEwkt(customer.address.lon, customer.address.lat),
                geocodeConfidence = customer.address.confidence,
                peliasGid = customer.address.gid,
                organisationId = orgId,
            ),
        )
        return customerId
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    private companion object {
        const val SENDER_NAME = "My Deliveries"

        fun pointEwkt(lng: Double, lat: Double): String = "SRID=4326;POINT($lng $lat)"

        @OptIn(ExperimentalUuidApi::class)
        fun newId(): String = Uuid.random().toString()
    }
}

/**
 * A 409 from a package-facing endpoint, where the conflict is always "this package is already
 * spoken for" rather than a transport failure:
 * - [CreateShiftRepository.submitShift] — the adhoc optimiser rejects a submitted package that has
 *   since been claimed by another shift's optimisation (the wizard's available-packages snapshot
 *   went stale between the Packages step and submission).
 * - [org.hikyaku.mobile.packages.PackageRepository.createPackage] — the tracking number already
 *   belongs to a package with a different payload.
 */
class PackageConflictException(message: String) : Exception(message)

/** The resolved recipient for an added stop: its customer id and geocoded coordinates. */
data class ResolvedStopCustomer(
    val customerId: String,
    val longitude: Double,
    val latitude: Double,
)

// ---------------------------------------------------------------------------
// Wire models
// ---------------------------------------------------------------------------

/** Fallback dropdown label for a vehicle with neither a model nor a plate recorded. */
private const val UNNAMED_VEHICLE = "Vehicle"

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class VehicleRow(
    val id: String,
    @SerialName("vehicle_model") val model: String? = null,
    @SerialName("vehicle_plate") val plate: String? = null,
    // Embedded via vehicles_vehicle_type_fkey; carries the vehicle_type.id sent to the optimiser.
    @SerialName("vehicle_type") val vehicleType: VehicleTypeEmbed? = null,
) {
    fun toOption(): VehicleOption = VehicleOption(
        id = id,
        label = model?.takeIf { it.isNotBlank() }
            ?: plate?.takeIf { it.isNotBlank() }
            ?: UNNAMED_VEHICLE,
        vehicleTypeId = vehicleType?.id?.takeIf { it.isNotBlank() },
    )
}

@Serializable
private data class VehicleTypeEmbed(
    val id: String? = null,
)

@Serializable
private data class WarehouseRow(
    val id: String,
    @SerialName("warehouse_name") val name: String,
    @SerialName("warehouse_address") val address: String,
    // PostGIS geometry, returned by PostgREST as a GeoJSON `Point` (`[lng, lat]`).
    @SerialName("warehouse_location") val location: Point? = null,
) {
    fun toOption(): WarehouseOption {
        val lng = location?.longitude ?: 0.0
        val lat = location?.latitude ?: 0.0
        return WarehouseOption(id = id, name = name, address = address, lat = lat, lng = lng)
    }
}

@Serializable
private data class CustomerRow(
    @SerialName("customer_name") val name: String? = null,
    @SerialName("customer_phone") val phone: String? = null,
    @SerialName("customer_address") val address: String? = null,
    @SerialName("customer_suburb") val suburb: String? = null,
    @SerialName("customer_state") val state: String? = null,
    @SerialName("customer_postcode") val postcode: String? = null,
    @SerialName("customer_country") val country: String? = null,
    @SerialName("customer_location") val location: Point? = null,
    @SerialName("geocode_confidence") val confidence: Double? = null,
    @SerialName("pelias_gid") val gid: String? = null,
) {
    /** Null when the row lacks a name or a usable geocoded address (e.g. the sender record). */
    fun toSuggestion(): CustomerSuggestion? {
        val name = name?.takeIf { it.isNotBlank() } ?: return null
        val label = address?.takeIf { it.isNotBlank() } ?: return null
        val lon = location?.longitude ?: return null
        val lat = location.latitude
        return CustomerSuggestion(
            name = name,
            phoneE164 = phone,
            address = AddressSuggestion(
                label = label,
                street = null,
                suburb = suburb,
                state = state,
                country = country,
                postcode = postcode,
                lat = lat,
                lon = lon,
                gid = gid,
                confidence = confidence,
            ),
        )
    }
}

@Serializable
private data class AvailablePackageRow(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("to_customer") val toCustomer: ToCustomerEmbed? = null,
) {
    fun toSelectable(): SelectablePackage = SelectablePackage(
        id = id,
        trackingNumber = trackingNumber,
        receiverName = toCustomer?.name.orEmpty(),
        receiverAddress = toCustomer?.address.orEmpty(),
    )
}

@Serializable
private data class ToCustomerEmbed(
    @SerialName("customer_name") val name: String? = null,
    @SerialName("customer_address") val address: String? = null,
)

// The `POST /api/v1/optimisation/adhoc` request/response types are the generated
// AdhocOptimisationDto / AdhocOptimisationResultDto (org.hikyaku.mobile.api.generated.models).

@Serializable
private data class WarehouseInsert(
    val id: String,
    @SerialName("warehouse_name") val warehouseName: String,
    @SerialName("warehouse_address") val warehouseAddress: String,
    @SerialName("warehouse_location") val warehouseLocation: String,
    @SerialName("warehouse_city") val warehouseCity: String,
    @SerialName("warehouse_state") val warehouseState: String,
    @SerialName("warehouse_country") val warehouseCountry: String,
    @SerialName("warehouse_zipcode") val warehouseZipcode: String,
    @SerialName("organisation_id") val organisationId: String,
)

@Serializable
private data class CustomerInsert(
    val id: String,
    @SerialName("customer_name") val customerName: String?,
    @SerialName("customer_phone") val customerPhone: String?,
    @SerialName("customer_address") val customerAddress: String?,
    @SerialName("customer_suburb") val customerSuburb: String?,
    @SerialName("customer_state") val customerState: String?,
    @SerialName("customer_postcode") val customerPostcode: String?,
    @SerialName("customer_country") val customerCountry: String?,
    @SerialName("customer_location") val customerLocation: String,
    @SerialName("geocode_confidence") val geocodeConfidence: Double?,
    @SerialName("pelias_gid") val peliasGid: String?,
    @SerialName("organisation_id") val organisationId: String,
)
