package org.hikyaku.mobile.shift.model

/**
 * A package with no shift yet (`packages.optimisation_id IS NULL`), offered while editing an
 * existing shift as something to add as a new stop. [receiverCustomerId] is the id already on the
 * package (`to_customer`) and is reused as-is — adding the stop doesn't create a new `customer` row.
 */
data class AddablePackage(
    val packageId: String,
    val trackingNumber: String,
    val receiverName: String,
    val receiverAddress: String,
    val receiverCustomerId: String,
    val longitude: Double,
    val latitude: Double,
)
