package org.hikyaku.mobile.shift.create.model

import kotlinx.serialization.Serializable
import org.hikyaku.mobile.geocode.model.AddressSuggestion

/**
 * A geocoded sender/receiver being composed for a new package, as persisted by [CreateShiftDraft].
 * Mirrors `CreateShiftViewModel.CustomerDraft`, dropping transient search/suggestion state.
 */
@Serializable
data class CustomerDraftSnapshot(
    val localId: String,
    val name: String = "",
    val phone: String = "",
    val countryIso: String = "",
    val addressQuery: String = "",
    val picked: AddressSuggestion? = null,
)

/**
 * A snapshot of the keyed-in values on the "New Shift" wizard, persisted so the form survives
 * process death (e.g. the OS killing the app while the screen is off). Only the fields a user
 * would notice as lost are kept — loading flags, fetched lists, and search-suggestion caches are
 * cheap to refetch and are left out. Package photos ([ByteArray]) aren't persisted either, since
 * they don't fit cleanly into settings-backed JSON storage; the user re-attaches those if a
 * restore happens mid-package-form.
 */
@Serializable
data class CreateShiftDraft(
    val orgId: String,
    val step: String,
    val selectedVehicleId: String? = null,
    val selectedWarehouseId: String? = null,
    val addingWarehouse: Boolean = false,
    val warehouseName: String = "",
    val warehouseQuery: String = "",
    val pickedWarehouse: AddressSuggestion? = null,
    val startDateMillis: Long? = null,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val resolvedWarehouseId: String? = null,
    val selectedPackageIds: Set<String> = emptySet(),
    val showAddPackageForm: Boolean = false,
    val packageWeight: String = "",
    val packageLength: String = "",
    val packageWidth: String = "",
    val packageHeight: String = "",
    val packageSender: CustomerDraftSnapshot = CustomerDraftSnapshot(localId = "sender"),
    val packageReceiver: CustomerDraftSnapshot = CustomerDraftSnapshot(localId = "receiver"),
    val packageDeliveryNotes: String = "",
    val packageArrivalDateMillis: Long? = null,
    val packageArrivalHour: Int = 12,
    val packageArrivalMinute: Int = 0,
)
