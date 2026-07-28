package org.hikyaku.mobile.vehicles.model

/** A row from `vehicles`, as shown in the vehicle overview list. */
data class VehicleSummary(
    val id: String,
    val vehicleModel: String,
    val vehicleMake: String?,
    val vehicleTypeName: String?,
) {
    /** "Make • Type" subtitle, omitting whichever part is missing. */
    val subtitle: String get() = listOfNotNull(vehicleMake, vehicleTypeName).joinToString(" • ")
}

/** A selectable vehicle type (routing profile), from `vehicle_type`. */
data class VehicleTypeOption(val id: String, val name: String)

/** A warehouse a new vehicle can be based at, from `warehouse`. */
data class VehicleWarehouseOption(val id: String, val name: String, val address: String)

/** Everything needed to persist a new `vehicles` row. */
data class VehicleDraft(
    val organisationId: String,
    val vehiclePlate: String?,
    val vehicleIdentificationNumber: String?,
    val vehicleMake: String?,
    val vehicleModel: String?,
    val vehicleYear: Int,
    val vehicleTypeId: String,
    val vehicleGrossLimits: Double,
    val warehouseId: String,
    /** Photos to upload to the `vehicles` storage bucket under the new vehicle's id. */
    val images: List<ByteArray> = emptyList(),
)

/** A single `vehicles` row, as shown on the vehicle detail screen's header. */
data class VehicleDetail(
    val id: String,
    val vehiclePlate: String?,
    val vehicleIdentificationNumber: String?,
    val vehicleMake: String?,
    val vehicleModel: String,
    val vehicleYear: Int?,
    val vehicleGrossLimits: Double?,
    val vehicleTypeName: String?,
    val warehouseName: String?,
)

/** A row from `vehicle_maintenance`, shown on the vehicle detail screen's service history. */
data class MaintenanceRecord(
    val id: String,
    val odometer: Double,
    val description: String,
    /** ISO `YYYY-MM-DD`. */
    val dateServiced: String,
)

/** Everything needed to persist a new `vehicle_maintenance` row. `user_id` is set from the session. */
data class MaintenanceDraft(
    val organisationId: String,
    val vehicleId: String,
    val odometer: Double,
    val description: String,
    /** ISO `YYYY-MM-DD`. */
    val dateServiced: String,
    /** Photos to upload to the `maintenance` storage bucket under the new record's id. */
    val images: List<ByteArray> = emptyList(),
)
