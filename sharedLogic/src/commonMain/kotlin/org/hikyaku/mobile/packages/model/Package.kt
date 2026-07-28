package org.hikyaku.mobile.packages.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.shift.create.model.ShiftCustomerInput

/** A row from `packages`, as shown in the package overview list. */
@Serializable
data class PackageSummary(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("created_at") val createdAt: String,
) {
    /** Date portion (YYYY-MM-DD) of the ISO [createdAt] timestamp. */
    val createdDate: String get() = createdAt.take(10)
}

/**
 * Everything shown on the package detail screen, aggregated across the joined `packages`,
 * `customer` (sender + receiver), `warehouse`, `package_dimensions`, `package_delivery_window`
 * and `package_timeline`/`package_status` tables. Any part may be absent for an in-progress
 * package, so most fields are nullable.
 */
data class PackageDetail(
    val id: String,
    val trackingNumber: String,
    val createdAt: String,
    /** Human label of the most recent [timeline] entry, e.g. "In Transit"; null if never set. */
    val currentStatus: String?,
    /** Machine enum of the current status (DELIVERED/FAILED/PENDING/ASSIGNED/ONBOARD_FOR_DELIVERY/IN_TRANSIT), for styling. */
    val currentStatusEnum: String?,
    val deliveryNotes: String?,
    val sender: PackageParty,
    val receiver: PackageParty,
    val warehouseName: String?,
    val warehouseAddress: String?,
    val dimensions: PackageDimensions?,
    val deliveryWindow: PackageDeliveryWindow?,
    /** Status history, most recent first. */
    val timeline: List<PackageTimelineEntry>,
) {
    /** Date portion (YYYY-MM-DD) of the ISO [createdAt] timestamp. */
    val createdDate: String get() = createdAt.take(10)
}

/** A sender or receiver on a package, from the joined `customer` row. */
data class PackageParty(
    val name: String?,
    val phone: String?,
    /** Single-line address label built from the customer's address parts, or null if none. */
    val address: String?,
)

/** Physical measurements of a package, from `package_dimensions`. */
data class PackageDimensions(
    val weightKg: Double,
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
) {
    /** Volume in cubic centimetres (L × W × H). */
    val volumeCm3: Double get() = lengthCm * widthCm * heightCm
}

/** Scheduled vs. actual departure/arrival timestamps, from `package_delivery_window`. */
data class PackageDeliveryWindow(
    val scheduledDeparture: String?,
    val actualDeparture: String?,
    val scheduledArrival: String?,
    val actualArrival: String?,
)

/** One status change in a package's history, from `package_timeline` joined to `package_status`. */
data class PackageTimelineEntry(
    /** Human label, e.g. "In Transit". */
    val status: String,
    /** Machine enum, e.g. "IN_TRANSIT", for styling. */
    val statusEnum: String,
    /** ISO timestamp the status was recorded. */
    val createdAt: String,
)

/**
 * Everything needed to persist a new package: its physical dimensions, sender/receiver (persisted
 * to `customer`, reusing a returning customer where possible), the starting [warehouseId], optional
 * [deliveryNotes], the [scheduledArrival] delivery window, and any [images] to upload to the
 * `packages` storage bucket.
 */
data class PackageDraft(
    val organisationId: String,
    val sender: ShiftCustomerInput,
    val receiver: ShiftCustomerInput,
    val warehouseId: String,
    val weightKg: Double,
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val deliveryNotes: String?,
    val scheduledArrival: String,
    val images: List<ByteArray>,
)
