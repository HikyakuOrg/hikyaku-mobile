package org.hikyaku.mobile.vehicles

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.StorageItem
import io.github.jan.supabase.storage.authenticatedStorageItem
import io.github.jan.supabase.storage.storage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.supabase.SupabaseBuckets
import org.hikyaku.mobile.supabase.SupabaseTables
import org.hikyaku.mobile.vehicles.model.MaintenanceDraft
import org.hikyaku.mobile.vehicles.model.MaintenanceRecord
import org.hikyaku.mobile.vehicles.model.VehicleDetail
import org.hikyaku.mobile.vehicles.model.VehicleDraft
import org.hikyaku.mobile.vehicles.model.VehicleSummary
import org.hikyaku.mobile.vehicles.model.VehicleTypeOption
import org.hikyaku.mobile.vehicles.model.VehicleWarehouseOption

/** Fallback label for a vehicle with no model recorded. */
private const val UNNAMED_VEHICLE = "Vehicle"

/** Backs the vehicle overview list and the add-vehicle form. */
class VehicleRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /**
     * One page of [orgId]'s non-deleted vehicles, ordered by model (then id, for a stable cursor
     * when models repeat or are blank). [from]/[to] are inclusive row offsets (Postgrest `range`),
     * so callers fetch `pageSize + 1` rows to cheaply detect a next page.
     */
    suspend fun fetchVehicles(orgId: String, from: Long, to: Long): Result<List<VehicleSummary>> = runCatching {
        client.postgrest.from(SupabaseTables.VEHICLES)
            .select(
                Columns.raw(
                    "id, vehicle_model, vehicle_make, " +
                        "vehicle_type!vehicles_vehicle_type_fkey(vehicle_type)",
                ),
            ) {
                filter {
                    eq("organisation_id", orgId)
                    eq("is_deleted", false)
                }
                order("vehicle_model", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, to)
            }
            .decodeList<VehicleRow>()
            .map { it.toSummary() }
    }

    /** The available vehicle types (routing profiles), for the add-vehicle form's required picker. */
    suspend fun fetchVehicleTypes(): Result<List<VehicleTypeOption>> = runCatching {
        client.postgrest.from(SupabaseTables.VEHICLE_TYPE)
            .select(Columns.raw("id, vehicle_type")) {
                order("vehicle_type", Order.ASCENDING)
            }
            .decodeList<VehicleTypeRow>()
            .map { VehicleTypeOption(id = it.id, name = it.name) }
    }

    /** The org's existing warehouses, so a new vehicle can optionally be based at one. */
    suspend fun fetchWarehouses(orgId: String): Result<List<VehicleWarehouseOption>> = runCatching {
        client.postgrest.from(SupabaseTables.WAREHOUSE)
            .select(Columns.raw("id, warehouse_name, warehouse_address")) {
                filter { eq("organisation_id", orgId) }
            }
            .decodeList<WarehouseRow>()
            .map { VehicleWarehouseOption(id = it.id, name = it.name, address = it.address) }
    }

    /**
     * Persists [draft] as a new `vehicles` row, then uploads any attached photos to
     * `vehicles/{id}/photo_{index}.jpg`. Returns the new vehicle's id.
     *
     * [assignToSelf] should be true in a personal org, where the signed-in user is the only
     * possible driver: the instant-assignment engine only opens a shift for a driver+vehicle pair
     * it finds in `driver_vehicle_assignment`, so without this a personal org's vehicles are never
     * assignable, no matter how many are added.
     */
    suspend fun createVehicle(draft: VehicleDraft, assignToSelf: Boolean = false): Result<String> = runCatching {
        val id = newId()
        client.postgrest.from(SupabaseTables.VEHICLES).insert(
            VehicleInsert(
                id = id,
                vehiclePlate = draft.vehiclePlate,
                vehicleIdentificationNumber = draft.vehicleIdentificationNumber,
                vehicleMake = draft.vehicleMake,
                vehicleModel = draft.vehicleModel,
                vehicleYear = draft.vehicleYear,
                vehicleType = draft.vehicleTypeId,
                vehicleGrossLimits = draft.vehicleGrossLimits,
                warehouseId = draft.warehouseId,
                organisationId = draft.organisationId,
            ),
        )
        draft.images.forEachIndexed { index, bytes -> uploadVehiclePhoto(id, index, bytes) }
        if (assignToSelf) selfAssignDriver(draft.organisationId, draft.warehouseId, id)
        id
    }

    /**
     * Pairs the signed-in user with [vehicleId] in `driver_vehicle_assignment`, creating their
     * `drivers` row first if this is their first vehicle. Best-effort: swallows failures so a
     * hiccup here doesn't undo an otherwise-successful vehicle creation, matching the existing
     * `runCatching`-per-step style in this repository.
     */
    private suspend fun selfAssignDriver(organisationId: String, warehouseId: String, vehicleId: String) {
        runCatching {
            val userId = client.auth.currentUserOrNull()?.id ?: return@runCatching
            val hasDriver = client.postgrest.from(SupabaseTables.DRIVERS)
                .select(Columns.raw("id")) { filter { eq("id", userId) } }
                .decodeList<DriverIdRow>()
                .isNotEmpty()
            if (!hasDriver) {
                client.postgrest.from(SupabaseTables.DRIVERS).insert(
                    DriverInsert(id = userId, organisationId = organisationId, warehouseId = warehouseId),
                )
            }
            client.postgrest.from(SupabaseTables.DRIVER_VEHICLE_ASSIGNMENT).insert(
                DriverVehicleAssignmentInsert(driverId = userId, vehicleId = vehicleId),
            )
        }
    }

    /** The single `vehicles` row for [vehicleId], for the vehicle detail screen's header. */
    suspend fun fetchVehicle(vehicleId: String): Result<VehicleDetail> = runCatching {
        client.postgrest.from(SupabaseTables.VEHICLES)
            .select(
                Columns.raw(
                    "id, vehicle_plate, vehicle_identification_number, vehicle_make, vehicle_model, " +
                        "vehicle_year, vehicle_gross_limits, " +
                        "vehicle_type!vehicles_vehicle_type_fkey(vehicle_type), " +
                        "warehouse_id:warehouse!vehicles_warehouse_id_fkey(warehouse_name)",
                ),
            ) {
                filter { eq("id", vehicleId) }
            }
            .decodeSingle<VehicleDetailRow>()
            .toDetail()
    }

    /** [vehicleId]'s service history, from `vehicle_maintenance`, most recent service first. */
    suspend fun fetchMaintenanceRecords(vehicleId: String): Result<List<MaintenanceRecord>> = runCatching {
        client.postgrest.from(SupabaseTables.VEHICLE_MAINTENANCE)
            .select(Columns.raw("id, odometer, description, date_serviced")) {
                filter { eq("vehicle_id", vehicleId) }
                order("date_serviced", Order.DESCENDING)
            }
            .decodeList<MaintenanceRow>()
            .map { it.toRecord() }
    }

    /**
     * Persists [draft] as a new `vehicle_maintenance` row, attributed to the signed-in user, then
     * uploads any attached photos to `maintenance/{recordId}/photo_{index}.jpg`.
     */
    suspend fun addMaintenanceRecord(draft: MaintenanceDraft): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("No authenticated user.")
        val id = newId()
        client.postgrest.from(SupabaseTables.VEHICLE_MAINTENANCE).insert(
            MaintenanceInsert(
                id = id,
                organisationId = draft.organisationId,
                vehicleId = draft.vehicleId,
                userId = userId,
                odometer = draft.odometer,
                description = draft.description,
                dateServiced = draft.dateServiced,
            ),
        )
        draft.images.forEachIndexed { index, bytes -> uploadMaintenancePhoto(id, index, bytes) }
    }

    /**
     * [StorageItem]s for every photo under `maintenance/{maintenanceId}` in the private
     * `maintenance` bucket, or an empty list when the record has none. Returns failure only on a
     * real error; a missing/empty folder yields an empty list.
     */
    suspend fun fetchMaintenanceImages(maintenanceId: String): Result<List<StorageItem>> = runCatching {
        val bucket = client.storage.from(SupabaseBuckets.MAINTENANCE)
        val files = bucket.list(maintenanceId)
            .map { it.name }
            .filter { it != ".emptyFolderPlaceholder" }
        files.map { name -> authenticatedStorageItem(SupabaseBuckets.MAINTENANCE, "$maintenanceId/$name") }
    }

    private suspend fun uploadMaintenancePhoto(maintenanceId: String, index: Int, bytes: ByteArray) {
        val path = "$maintenanceId/photo_$index.jpg"
        client.storage.from(SupabaseBuckets.MAINTENANCE).upload(path, bytes) { upsert = true }
    }

    /**
     * [StorageItem]s for every photo under `vehicles/{vehicleId}` in the private `vehicles` bucket,
     * or an empty list when the vehicle has none. Returns failure only on a real error; a
     * missing/empty folder yields an empty list.
     */
    suspend fun fetchVehicleImages(vehicleId: String): Result<List<StorageItem>> = runCatching {
        val bucket = client.storage.from(SupabaseBuckets.VEHICLES)
        val files = bucket.list(vehicleId)
            .map { it.name }
            .filter { it != ".emptyFolderPlaceholder" }
        files.map { name -> authenticatedStorageItem(SupabaseBuckets.VEHICLES, "$vehicleId/$name") }
    }

    private suspend fun uploadVehiclePhoto(vehicleId: String, index: Int, bytes: ByteArray) {
        val path = "$vehicleId/photo_$index.jpg"
        client.storage.from(SupabaseBuckets.VEHICLES).upload(path, bytes) { upsert = true }
    }

    private companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun newId(): String = Uuid.random().toString()
    }
}

@Serializable
private data class VehicleRow(
    val id: String,
    @SerialName("vehicle_model") val model: String? = null,
    @SerialName("vehicle_make") val make: String? = null,
    // Embedded via vehicles_vehicle_type_fkey; carries the vehicle_type's display name.
    @SerialName("vehicle_type") val vehicleType: VehicleTypeEmbed? = null,
) {
    fun toSummary(): VehicleSummary = VehicleSummary(
        id = id,
        vehicleModel = model?.takeIf { it.isNotBlank() } ?: UNNAMED_VEHICLE,
        vehicleMake = make?.takeIf { it.isNotBlank() },
        vehicleTypeName = vehicleType?.vehicleType?.takeIf { it.isNotBlank() },
    )
}

@Serializable
private data class VehicleTypeEmbed(
    @SerialName("vehicle_type") val vehicleType: String? = null,
)

@Serializable
private data class VehicleTypeRow(
    val id: String,
    @SerialName("vehicle_type") val name: String,
)

@Serializable
private data class WarehouseRow(
    val id: String,
    @SerialName("warehouse_name") val name: String,
    @SerialName("warehouse_address") val address: String,
)

@Serializable
private data class DriverIdRow(val id: String)

@Serializable
private data class DriverInsert(
    val id: String,
    @SerialName("organisation_id") val organisationId: String,
    @SerialName("warehouse_id") val warehouseId: String,
)

@Serializable
private data class DriverVehicleAssignmentInsert(
    @SerialName("driver_id") val driverId: String,
    @SerialName("vehicle_id") val vehicleId: String,
)

@Serializable
private data class VehicleInsert(
    val id: String,
    @SerialName("vehicle_plate") val vehiclePlate: String?,
    @SerialName("vehicle_identification_number") val vehicleIdentificationNumber: String?,
    @SerialName("vehicle_make") val vehicleMake: String?,
    @SerialName("vehicle_model") val vehicleModel: String?,
    @SerialName("vehicle_year") val vehicleYear: Int,
    @SerialName("vehicle_type") val vehicleType: String,
    @SerialName("vehicle_gross_limits") val vehicleGrossLimits: Double,
    @SerialName("warehouse_id") val warehouseId: String?,
    @SerialName("organisation_id") val organisationId: String,
)

@Serializable
private data class VehicleDetailRow(
    val id: String,
    @SerialName("vehicle_plate") val plate: String? = null,
    @SerialName("vehicle_identification_number") val vin: String? = null,
    @SerialName("vehicle_make") val make: String? = null,
    @SerialName("vehicle_model") val model: String? = null,
    @SerialName("vehicle_year") val year: Double? = null,
    @SerialName("vehicle_gross_limits") val grossLimits: Double? = null,
    // Embedded via vehicles_vehicle_type_fkey / vehicles_warehouse_id_fkey.
    @SerialName("vehicle_type") val vehicleType: VehicleTypeEmbed? = null,
    @SerialName("warehouse_id") val warehouse: WarehouseEmbed? = null,
) {
    fun toDetail(): VehicleDetail = VehicleDetail(
        id = id,
        vehiclePlate = plate?.takeIf { it.isNotBlank() },
        vehicleIdentificationNumber = vin?.takeIf { it.isNotBlank() },
        vehicleMake = make?.takeIf { it.isNotBlank() },
        vehicleModel = model?.takeIf { it.isNotBlank() } ?: UNNAMED_VEHICLE,
        vehicleYear = year?.toInt(),
        vehicleGrossLimits = grossLimits,
        vehicleTypeName = vehicleType?.vehicleType?.takeIf { it.isNotBlank() },
        warehouseName = warehouse?.name?.takeIf { it.isNotBlank() },
    )
}

@Serializable
private data class WarehouseEmbed(
    @SerialName("warehouse_name") val name: String? = null,
)

@Serializable
private data class MaintenanceRow(
    val id: String,
    val odometer: Double,
    val description: String,
    @SerialName("date_serviced") val dateServiced: String,
) {
    fun toRecord(): MaintenanceRecord = MaintenanceRecord(
        id = id,
        odometer = odometer,
        description = description,
        dateServiced = dateServiced,
    )
}

@Serializable
private data class MaintenanceInsert(
    val id: String,
    @SerialName("organisation_id") val organisationId: String,
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("user_id") val userId: String?,
    val odometer: Double,
    val description: String,
    @SerialName("date_serviced") val dateServiced: String,
)
