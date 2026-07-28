package org.hikyaku.mobile.supabase

/**
 * Postgrest table/view names queried via `client.postgrest.from(...)`, in one place so a rename
 * on the backend doesn't require hunting through every repository.
 */
object SupabaseTables {
    const val ORGANISATIONS = "organisations"
    const val VRP_OPTIMIZATION = "vrp_optimization"
    const val VRP_SOLUTION = "vrp_solution"
    const val VRP_ROUTE = "vrp_route"
    const val VRP_ROUTE_STEP = "vrp_route_step"
    const val PACKAGES = "packages"
    const val PACKAGES_WITH_LATEST_STATUS = "packages_with_latest_status"
    const val PACKAGE_TIMELINE = "package_timeline"
    const val PACKAGE_ASSIGNMENT = "package_assignment"
    const val PACKAGE_DELIVERY_WINDOW = "package_delivery_window"
    const val PACKAGE_DIMENSIONS = "package_dimensions"
    const val PACKAGE_PROOF_OF_DELIVERY = "package_proof_of_delivery"
    const val PACKAGE_STATUS = "package_status"
    const val DRIVER_CURRENT_LOCATION = "driver_current_location"
    const val DRIVER_LOCATION_HISTORY = "driver_location_history"
    const val DRIVERS = "drivers"
    const val VEHICLES = "vehicles"
    const val VEHICLE_TYPE = "vehicle_type"
    const val VEHICLE_MAINTENANCE = "vehicle_maintenance"
    const val WAREHOUSE = "warehouse"
    const val CUSTOMER = "customer"
}

/** Storage bucket names used via `client.storage.from(...)`. */
object SupabaseBuckets {
    /** Private bucket holding proof-of-delivery photos, keyed by `{packageId}/...`. */
    const val PACKAGES = "packages"

    /** Public bucket holding profile pictures, keyed by `{userId}.{ext}` (RLS restricts writes to the owning user). */
    const val AVATAR = "avatar"

    /** Private bucket holding maintenance record photos, keyed by `{maintenanceRecordId}/...`. */
    const val MAINTENANCE = "maintenance"

    /** Private bucket holding vehicle photos, keyed by `{vehicleId}/...`. */
    const val VEHICLES = "vehicles"
}
