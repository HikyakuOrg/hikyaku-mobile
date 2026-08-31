package org.hikyaku.mobile.warehouse

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.supabase.SupabaseTables
import org.hikyaku.mobile.warehouse.model.WarehouseOption
import org.maplibre.spatialk.geojson.Point

/**
 * The single place that reads/writes `warehouse` rows: the standalone Warehouse screen, the
 * add-package form's starting-location picker, and the one-click package optimiser (which needs a
 * warehouse to run against) all go through this. RLS requires the caller to hold the org's
 * `warehouse.*` permission, granted to a personal org's creator by `handle_new_organisation`. A
 * personal org is limited to one warehouse, enforced server-side by the
 * `warehouse_personal_org_limit` trigger — see [canAddWarehouse].
 */
class WarehouseRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /** [orgId]'s warehouses, ordered by name. */
    suspend fun fetchWarehouses(orgId: String): Result<List<WarehouseOption>> = runCatching {
        client.postgrest.from(SupabaseTables.WAREHOUSE)
            .select(Columns.raw("id, warehouse_name, warehouse_address, warehouse_location")) {
                filter { eq("organisation_id", orgId) }
                order("warehouse_name", Order.ASCENDING)
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

    private companion object {
        fun pointEwkt(lng: Double, lat: Double): String = "SRID=4326;POINT($lng $lat)"

        @OptIn(ExperimentalUuidApi::class)
        fun newId(): String = Uuid.random().toString()
    }
}

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
