package org.hikyaku.mobile.packages

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.StorageItem
import io.github.jan.supabase.storage.authenticatedStorageItem
import io.github.jan.supabase.storage.storage
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
import org.hikyaku.mobile.api.generated.models.CreatePackageResultDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import org.hikyaku.mobile.packages.model.PackageDeliveryWindow
import org.hikyaku.mobile.packages.model.PackageDetail
import org.hikyaku.mobile.packages.model.PackageDimensions
import org.hikyaku.mobile.packages.model.PackageDraft
import org.hikyaku.mobile.packages.model.PackageParty
import org.hikyaku.mobile.packages.model.PackageSummary
import org.hikyaku.mobile.packages.model.PackageTimelineEntry
import org.hikyaku.mobile.shift.create.PackageConflictException
import org.hikyaku.mobile.shift.create.model.CustomerSuggestion
import org.hikyaku.mobile.shift.create.model.ShiftCustomerInput
import org.hikyaku.mobile.shift.create.model.WarehouseOption
import org.hikyaku.mobile.supabase.SupabaseBuckets
import org.hikyaku.mobile.supabase.SupabaseTables
import org.maplibre.spatialk.geojson.Point

/**
 * Backs the package overview list and the add-package form. Packages carry their sender and
 * receiver as `customer` rows (reusing a returning customer keyed by phone, same de-duplication as
 * [org.hikyaku.mobile.shift.create.CreateShiftRepository]), a required starting [SupabaseTables.WAREHOUSE],
 * physical dimensions in `package_dimensions`, a delivery window in `package_delivery_window`, and
 * optional photos uploaded to the private `packages` storage bucket under `{packageId}/...` and
 * recorded in `package_proof_of_delivery`. `packages.tracking_number` is left unset on insert — a
 * `BEFORE INSERT` trigger (`packages_set_tracking_number`) generates it server-side.
 *
 * Reads still go through PostgREST; [createPackage] goes through `POST /api/v1/packages` so the
 * write is one transaction and the package can be assigned to a shift on the spot. The API call is
 * authenticated with the caller's Supabase access token and scoped with `X-Organisation-Slug`,
 * matching [org.hikyaku.mobile.shift.create.CreateShiftRepository].
 */
class PackageRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /**
     * One page of [orgId]'s packages, newest first. [from]/[to] are inclusive row offsets
     * (Postgrest `range`), so callers fetch `pageSize + 1` rows to cheaply detect a next page.
     */
    suspend fun fetchPackages(orgId: String, from: Long, to: Long): Result<List<PackageSummary>> = runCatching {
        client.postgrest.from(SupabaseTables.PACKAGES)
            .select(Columns.raw("id, tracking_number, created_at")) {
                filter { eq("organisation_id", orgId) }
                order("created_at", Order.DESCENDING)
                range(from, to)
            }
            .decodeList<PackageSummary>()
    }

    /**
     * The full detail of the package with [trackingNumber] (unique), joining in its sender and
     * receiver customers, starting warehouse, physical dimensions and delivery window, plus its
     * status history from `package_timeline`. RLS scopes the lookup to the caller's org.
     */
    suspend fun fetchPackageDetail(trackingNumber: String): Result<PackageDetail> = runCatching {
        val row = client.postgrest.from(SupabaseTables.PACKAGES)
            .select(Columns.raw(DETAIL_COLUMNS)) {
                filter { eq("tracking_number", trackingNumber) }
            }
            .decodeSingle<PackageDetailRow>()
        val timeline = fetchTimeline(row.id)
        row.toDetail(timeline)
    }

    /** [packageId]'s status history from `package_timeline`, most recent first. */
    private suspend fun fetchTimeline(packageId: String): List<PackageTimelineEntry> =
        client.postgrest.from(SupabaseTables.PACKAGE_TIMELINE)
            .select(Columns.raw("created_at, package_status(status, enums)")) {
                filter { eq("package_id", packageId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<TimelineRow>()
            .mapNotNull { it.toEntry() }

    /**
     * [StorageItem]s for every proof-of-delivery photo under `{packageId}/` in the private
     * `packages` bucket, or an empty list when there are none. Returns failure only on a real
     * error; a missing/empty folder yields an empty list. Mirrors [fetchWarehouses]-style access.
     */
    suspend fun fetchPackageImages(packageId: String): Result<List<StorageItem>> = runCatching {
        val bucket = client.storage.from(SupabaseBuckets.PACKAGES)
        val files = bucket.list(packageId)
            .map { it.name }
            .filter { it != ".emptyFolderPlaceholder" }
        files.map { name -> authenticatedStorageItem(SupabaseBuckets.PACKAGES, "$packageId/$name") }
    }

    /**
     * How many of [orgId]'s packages at [warehouseId] are unassigned (`optimisation_id IS NULL`) —
     * the set a warehouse-wide optimisation run would pick up. Mirrors the same filter as
     * [org.hikyaku.mobile.shift.create.CreateShiftRepository.fetchAvailablePackages], but only
     * selects `id` since the caller just needs a count.
     */
    suspend fun countUnassignedPackages(orgId: String, warehouseId: String): Result<Int> = runCatching {
        client.postgrest.from(SupabaseTables.PACKAGES)
            .select(Columns.raw("id")) {
                filter {
                    eq("organisation_id", orgId)
                    eq("warehouse_id", warehouseId)
                    exact("optimisation_id", null)
                }
            }
            .decodeList<IdRow>()
            .size
    }

    /** The org's existing warehouses, so the user can pick a starting location for the package. */
    suspend fun fetchWarehouses(orgId: String): Result<List<WarehouseOption>> = runCatching {
        client.postgrest.from(SupabaseTables.WAREHOUSE)
            .select(Columns.raw("id, warehouse_name, warehouse_address, warehouse_location")) {
                filter { eq("organisation_id", orgId) }
            }
            .decodeList<WarehouseRow>()
            .map { it.toOption() }
    }

    /** Geocodes-and-persists a new warehouse from a chosen [address]. */
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
     * Existing customers of [orgId] whose name matches [query] (case-insensitive substring), so a
     * returning sender/receiver's phone + address can be reused. Mirrors
     * [org.hikyaku.mobile.shift.create.CreateShiftRepository.searchCustomers].
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
                }
                limit(30)
            }
            .decodeList<CustomerRow>()
            .mapNotNull { it.toSuggestion() }
            .distinctBy { it.name.lowercase() + "|" + it.address?.label }
            .take(5)
    }

    /**
     * Persists [draft] through `POST /api/v1/packages`: the sender and receiver customers are
     * resolved (or created) here first, then one call writes the `packages` row, its dimensions,
     * its delivery window and the opening `PENDING` timeline entry in a single server-side
     * transaction — and, unless [PackageDraft.autoAssign] is false, assigns the package to a shift
     * before returning. That replaces five non-atomic PostgREST inserts that could (and did) leave a
     * half-written package behind on a mid-sequence failure.
     *
     * Returns the whole [CreatePackageResultDto], not just an id: `assignment.outcome` is what the
     * caller renders ("stop 7, ETA 14:20" / "queued"), and it is only ever available here. The call
     * answers 201 even when assignment failed — a package is never lost because it couldn't be
     * routed — so a non-success status is a real error, and 409 specifically means a
     * tracking-number collision with a different payload.
     *
     * Photos still go straight to Supabase Storage rather than through the API: they already flow
     * under working RLS, the API has no multipart parser, and proxying the bytes through it would
     * add latency for nothing. The upload just moves *after* the create, keyed on the returned
     * package id, so the row the POD insert references already exists.
     */
    suspend fun createPackage(draft: PackageDraft): Result<CreatePackageResultDto> = runCatching {
        val fromCustomerId = ensureCustomer(draft.organisationId, draft.sender)
        val toCustomerId = ensureCustomer(draft.organisationId, draft.receiver)

        val response = httpClient.post(ApiEndpoints.packages(apiUrl())) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
            header(ApiHeaders.ORGANISATION_SLUG, draft.orgSlug)
            contentType(ContentType.Application.Json)
            setBody(
                draft.toCreatePackageDto(
                    // Minted client-side, which is what makes a retried create replay the same
                    // package instead of writing a second one.
                    id = newId(),
                    fromCustomerId = fromCustomerId,
                    toCustomerId = toCustomerId,
                ),
            )
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(300)
            if (response.status.value == 409) throw PackageConflictException(body)
            error("Couldn't create the package (${response.status.value}): $body")
        }
        val result = response.body<CreatePackageResultDto>()
        // Use the id the server reports, not the one sent: an idempotent replay answers with the
        // original package, and the photos belong under that one.
        draft.images.forEachIndexed { index, bytes -> uploadImage(result.`package`.id, index, bytes) }
        result
    }

    private suspend fun uploadImage(packageId: String, index: Int, bytes: ByteArray) {
        val path = "$packageId/photo_$index.jpg"
        client.storage.from(SupabaseBuckets.PACKAGES).upload(path, bytes) { upsert = true }
        client.postgrest.from(SupabaseTables.PACKAGE_PROOF_OF_DELIVERY)
            .insert(PodInsert(packageId = packageId, podTypeId = POD_TYPE_PHOTO, fileUrl = path))
    }

    /**
     * Returns the customer id for [customer], reusing a returning customer rather than inserting a
     * duplicate. `customer` has a unique constraint on `(organisation_id, customer_phone)`, so a
     * phone match is looked up first; phone-less customers can't collide on that constraint, so
     * they're always inserted.
     */
    private suspend fun ensureCustomer(orgId: String, customer: ShiftCustomerInput): String {
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
        // pod_type lookup id for "Photo" (see the pod_type table).
        const val POD_TYPE_PHOTO = 2

        /**
         * PostgREST select for [fetchPackageDetail]. Both parties come from `customer` via distinct
         * FKs, so each embed names its constraint to disambiguate; dimensions and delivery window
         * embed one-to-one (their `package_id` is unique).
         */
        const val DETAIL_COLUMNS =
            "id, tracking_number, created_at, delivery_notes, " +
                "sender:customer!packages_from_customer_fkey(" +
                "customer_name, customer_phone, customer_address, customer_suburb, " +
                "customer_state, customer_postcode, customer_country), " +
                "receiver:customer!packages_to_customer_fkey(" +
                "customer_name, customer_phone, customer_address, customer_suburb, " +
                "customer_state, customer_postcode, customer_country), " +
                "warehouse:warehouse!packages_warehouse_id_fkey(warehouse_name, warehouse_address), " +
                "package_dimensions(weight_kg, length_cm, width_cm, height_cm), " +
                "package_delivery_window(scheduled_departure, actual_departure, " +
                "scheduled_arrival, actual_arrival)"

        fun pointEwkt(lng: Double, lat: Double): String = "SRID=4326;POINT($lng $lat)"

        @OptIn(ExperimentalUuidApi::class)
        fun newId(): String = Uuid.random().toString()
    }
}

// ---------------------------------------------------------------------------
// Wire models
// ---------------------------------------------------------------------------

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class PackageDetailRow(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("delivery_notes") val deliveryNotes: String? = null,
    val sender: PartyRow? = null,
    val receiver: PartyRow? = null,
    val warehouse: WarehouseNameRow? = null,
    @SerialName("package_dimensions") val dimensions: DimensionsRow? = null,
    @SerialName("package_delivery_window") val deliveryWindow: DeliveryWindowRow? = null,
) {
    fun toDetail(timeline: List<PackageTimelineEntry>): PackageDetail = PackageDetail(
        id = id,
        trackingNumber = trackingNumber,
        createdAt = createdAt,
        currentStatus = timeline.firstOrNull()?.status,
        currentStatusEnum = timeline.firstOrNull()?.statusEnum,
        deliveryNotes = deliveryNotes?.takeIf { it.isNotBlank() },
        sender = sender?.toParty() ?: PackageParty(null, null, null),
        receiver = receiver?.toParty() ?: PackageParty(null, null, null),
        warehouseName = warehouse?.name,
        warehouseAddress = warehouse?.address,
        dimensions = dimensions?.toDimensions(),
        deliveryWindow = deliveryWindow?.toWindow(),
        timeline = timeline,
    )
}

@Serializable
private data class PartyRow(
    @SerialName("customer_name") val name: String? = null,
    @SerialName("customer_phone") val phone: String? = null,
    @SerialName("customer_address") val address: String? = null,
    @SerialName("customer_suburb") val suburb: String? = null,
    @SerialName("customer_state") val state: String? = null,
    @SerialName("customer_postcode") val postcode: String? = null,
    @SerialName("customer_country") val country: String? = null,
) {
    fun toParty(): PackageParty {
        // Prefer the full street address, then append the parts it doesn't already contain.
        val extra = listOfNotNull(suburb, state, postcode, country)
            .filter { part -> address?.contains(part, ignoreCase = true) != true }
        val label = (listOfNotNull(address?.takeIf { it.isNotBlank() }) + extra)
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        return PackageParty(
            name = name?.takeIf { it.isNotBlank() },
            phone = phone?.takeIf { it.isNotBlank() },
            address = label,
        )
    }
}

@Serializable
private data class WarehouseNameRow(
    @SerialName("warehouse_name") val name: String? = null,
    @SerialName("warehouse_address") val address: String? = null,
)

@Serializable
private data class DimensionsRow(
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("length_cm") val lengthCm: Double,
    @SerialName("width_cm") val widthCm: Double,
    @SerialName("height_cm") val heightCm: Double,
) {
    fun toDimensions() = PackageDimensions(weightKg, lengthCm, widthCm, heightCm)
}

@Serializable
private data class DeliveryWindowRow(
    @SerialName("scheduled_departure") val scheduledDeparture: String? = null,
    @SerialName("actual_departure") val actualDeparture: String? = null,
    @SerialName("scheduled_arrival") val scheduledArrival: String? = null,
    @SerialName("actual_arrival") val actualArrival: String? = null,
) {
    fun toWindow() = PackageDeliveryWindow(scheduledDeparture, actualDeparture, scheduledArrival, actualArrival)
}

@Serializable
private data class TimelineRow(
    @SerialName("created_at") val createdAt: String,
    @SerialName("package_status") val status: StatusRow? = null,
) {
    fun toEntry(): PackageTimelineEntry? =
        status?.let { PackageTimelineEntry(status = it.status, statusEnum = it.enums, createdAt = createdAt) }
}

@Serializable
private data class StatusRow(
    val status: String,
    val enums: String,
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
    /** Null when the row lacks a name or a usable geocoded address. */
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

// The `packages` / `package_dimensions` / `package_delivery_window` / `package_timeline` inserts
// that used to live here are gone: `POST /api/v1/packages` writes all four in one transaction, and
// its request body is the generated CreatePackageDto (see PackageDraftMapping.kt).

@Serializable
private data class PodInsert(
    @SerialName("package_id") val packageId: String,
    @SerialName("pod_type_id") val podTypeId: Int,
    @SerialName("file_url") val fileUrl: String,
)
