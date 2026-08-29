package org.hikyaku.mobile.packages.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.package_error_choose_arrival
import hikyaku.sharedui.generated.resources.package_error_choose_saved_warehouse
import hikyaku.sharedui.generated.resources.package_error_choose_warehouse
import hikyaku.sharedui.generated.resources.package_error_invalid_dimensions
import hikyaku.sharedui.generated.resources.package_error_invalid_weight
import hikyaku.sharedui.generated.resources.package_error_name_warehouse
import hikyaku.sharedui.generated.resources.package_error_receiver_needs_address
import hikyaku.sharedui.generated.resources.package_error_receiver_needs_name
import hikyaku.sharedui.generated.resources.package_error_receiver_needs_phone
import hikyaku.sharedui.generated.resources.package_error_save_warehouse_failed
import hikyaku.sharedui.generated.resources.package_error_sender_needs_address
import hikyaku.sharedui.generated.resources.package_error_sender_needs_name
import hikyaku.sharedui.generated.resources.package_error_sender_needs_phone
import hikyaku.sharedui.generated.resources.package_error_submit_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.geocode.GeocodeRepository
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.packages.PackageRepository
import org.hikyaku.mobile.packages.model.PackageDraft
import org.hikyaku.mobile.phone.PhoneNumbers
import org.hikyaku.mobile.shift.create.CustomerDraft
import org.hikyaku.mobile.shift.create.model.CustomerSuggestion
import org.hikyaku.mobile.shift.create.model.ShiftCustomerInput
import org.hikyaku.mobile.shift.create.model.WarehouseOption
import org.hikyaku.mobile.util.combineDateAndTimeToIsoUtc
import org.hikyaku.mobile.warehouse.canAddWarehouse
import org.jetbrains.compose.resources.getString

/** Which party a customer-field edit applies to. */
private enum class Party { SENDER, RECEIVER }

data class AddPackageUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    /**
     * What the backend did with the package, set once it has been created. Non-null is what puts
     * the confirmation panel on screen — the form doesn't close until the driver has seen whether
     * the package landed on a shift.
     */
    val assignment: PackageAssignmentDisplay? = null,
    // Dimensions
    val weight: String = "",
    val length: String = "",
    val width: String = "",
    val height: String = "",
    // Images
    val images: List<ByteArray> = emptyList(),
    // Sender / receiver
    val sender: CustomerDraft = CustomerDraft(localId = "sender"),
    val receiver: CustomerDraft = CustomerDraft(localId = "receiver"),
    // Delivery notes
    val deliveryNotes: String = "",
    // Warehouse
    val warehouses: List<WarehouseOption> = emptyList(),
    val selectedWarehouseId: String? = null,
    val addingWarehouse: Boolean = false,
    /** False once a personal org has reached [org.hikyaku.mobile.warehouse.PERSONAL_ORG_WAREHOUSE_LIMIT]. */
    val canAddWarehouse: Boolean = true,
    val warehouseName: String = "",
    val warehouseQuery: String = "",
    val warehouseSuggestions: List<AddressSuggestion> = emptyList(),
    val warehouseSearching: Boolean = false,
    val pickedWarehouse: AddressSuggestion? = null,
    // Scheduled arrival
    val arrivalDateMillis: Long? = null,
    val arrivalHour: Int = 12,
    val arrivalMinute: Int = 0,
)

/**
 * Drives the add-package form: loads the org's saved warehouses, geocodes the warehouse/sender/
 * receiver addresses via [GeocodeRepository] (debounced, mirroring
 * [org.hikyaku.mobile.shift.create.CreateShiftViewModel]), validates every field, and on submit
 * persists everything through [PackageRepository].
 */
class AddPackageViewModel(
    private val orgId: String,
    private val orgSlug: String,
    private val isPersonalOrg: Boolean,
    private val repository: PackageRepository = PackageRepository(),
    private val geocodeRepository: GeocodeRepository = GeocodeRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AddPackageUiState())
    val state: StateFlow<AddPackageUiState> = _state.asStateFlow()

    private val defaultCountryIso = PhoneNumbers.defaultCountryIso()
    private var warehouseGeocodeJob: Job? = null
    private val customerGeocodeJobs = mutableMapOf<Party, Job>()
    private val customerNameJobs = mutableMapOf<Party, Job>()

    init {
        _state.value = _state.value.copy(
            sender = CustomerDraft(localId = "sender", countryIso = defaultCountryIso),
            receiver = CustomerDraft(localId = "receiver", countryIso = defaultCountryIso),
        )
        loadWarehouses()
    }

    private fun loadWarehouses() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val warehouses = repository.fetchWarehouses(orgId).getOrDefault(emptyList())
            val canAdd = canAddWarehouse(isPersonalOrg, warehouses.size)
            _state.value = _state.value.copy(
                isLoading = false,
                warehouses = warehouses,
                selectedWarehouseId = warehouses.singleOrNull()?.id,
                addingWarehouse = warehouses.isEmpty() && canAdd,
                canAddWarehouse = canAdd,
            )
        }
    }

    // ----- Dimensions -----

    fun setWeight(value: String) {
        _state.value = _state.value.copy(weight = value)
    }

    fun setLength(value: String) {
        _state.value = _state.value.copy(length = value)
    }

    fun setWidth(value: String) {
        _state.value = _state.value.copy(width = value)
    }

    fun setHeight(value: String) {
        _state.value = _state.value.copy(height = value)
    }

    // ----- Images -----

    fun addImages(images: List<ByteArray>) {
        if (images.isEmpty()) return
        _state.value = _state.value.copy(images = _state.value.images + images)
    }

    fun removeImage(index: Int) {
        _state.value = _state.value.copy(images = _state.value.images.filterIndexed { i, _ -> i != index })
    }

    // ----- Sender / receiver -----

    fun setSenderName(name: String) = setCustomerName(Party.SENDER, name)
    fun setReceiverName(name: String) = setCustomerName(Party.RECEIVER, name)

    fun setSenderPhone(phone: String) = updateCustomer(Party.SENDER) { it.copy(phone = phone) }
    fun setReceiverPhone(phone: String) = updateCustomer(Party.RECEIVER) { it.copy(phone = phone) }

    fun setSenderCountry(countryIso: String) = updateCustomer(Party.SENDER) { it.copy(countryIso = countryIso) }
    fun setReceiverCountry(countryIso: String) = updateCustomer(Party.RECEIVER) { it.copy(countryIso = countryIso) }

    fun onSenderQueryChange(text: String) = onCustomerQueryChange(Party.SENDER, text)
    fun onReceiverQueryChange(text: String) = onCustomerQueryChange(Party.RECEIVER, text)

    fun pickSenderAddress(suggestion: AddressSuggestion) = pickCustomerAddress(Party.SENDER, suggestion)
    fun pickReceiverAddress(suggestion: AddressSuggestion) = pickCustomerAddress(Party.RECEIVER, suggestion)

    fun pickSenderSuggestion(suggestion: CustomerSuggestion) = pickCustomerSuggestion(Party.SENDER, suggestion)
    fun pickReceiverSuggestion(suggestion: CustomerSuggestion) = pickCustomerSuggestion(Party.RECEIVER, suggestion)

    private fun setCustomerName(party: Party, name: String) {
        updateCustomer(party) { it.copy(name = name) }
        customerNameJobs.remove(party)?.cancel()
        if (name.trim().length < MIN_QUERY) {
            updateCustomer(party) { it.copy(nameSuggestions = emptyList(), nameSearching = false) }
            return
        }
        customerNameJobs[party] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            updateCustomer(party) { it.copy(nameSearching = true) }
            val results = repository.searchCustomers(orgId, name.trim()).getOrDefault(emptyList())
            if (customerOf(party).name == name) {
                updateCustomer(party) { it.copy(nameSuggestions = results, nameSearching = false) }
            }
        }
    }

    private fun pickCustomerSuggestion(party: Party, suggestion: CustomerSuggestion) {
        customerNameJobs.remove(party)?.cancel()
        customerGeocodeJobs.remove(party)?.cancel()
        val parsed = suggestion.phoneE164?.let { PhoneNumbers.parseE164(it) }
        updateCustomer(party) {
            it.copy(
                name = suggestion.name,
                nameSuggestions = emptyList(),
                nameSearching = false,
                phone = parsed?.nationalNumber ?: it.phone,
                countryIso = parsed?.countryIso ?: it.countryIso,
                addressQuery = suggestion.address?.label ?: it.addressQuery,
                picked = suggestion.address ?: it.picked,
                suggestions = emptyList(),
                searching = false,
            )
        }
    }

    private fun onCustomerQueryChange(party: Party, text: String) {
        updateCustomer(party) { it.copy(addressQuery = text, picked = null) }
        customerGeocodeJobs.remove(party)?.cancel()
        if (text.length < MIN_QUERY) {
            updateCustomer(party) { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        updateCustomer(party) { it.copy(searching = true) }
        customerGeocodeJobs[party] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val result = geocodeRepository.autocomplete(text)
            if (customerOf(party).addressQuery == text) {
                // On a transient failure keep whatever suggestions are already shown rather than
                // clobbering them with an empty list; only stop the spinner.
                result
                    .onSuccess { updateCustomer(party) { c -> c.copy(suggestions = it, searching = false) } }
                    .onFailure { updateCustomer(party) { c -> c.copy(searching = false) } }
            }
        }
    }

    private fun pickCustomerAddress(party: Party, suggestion: AddressSuggestion) {
        customerGeocodeJobs.remove(party)?.cancel()
        updateCustomer(party) {
            it.copy(picked = suggestion, addressQuery = suggestion.label, suggestions = emptyList(), searching = false)
        }
    }

    private fun customerOf(party: Party): CustomerDraft =
        if (party == Party.SENDER) _state.value.sender else _state.value.receiver

    private fun updateCustomer(party: Party, transform: (CustomerDraft) -> CustomerDraft) {
        _state.value = when (party) {
            Party.SENDER -> _state.value.copy(sender = transform(_state.value.sender))
            Party.RECEIVER -> _state.value.copy(receiver = transform(_state.value.receiver))
        }
    }

    // ----- Delivery notes -----

    fun setDeliveryNotes(notes: String) {
        _state.value = _state.value.copy(deliveryNotes = notes)
    }

    // ----- Warehouse -----

    fun selectWarehouse(id: String) {
        _state.value = _state.value.copy(selectedWarehouseId = id, addingWarehouse = false)
    }

    fun startAddWarehouse() {
        if (!_state.value.canAddWarehouse) return
        _state.value = _state.value.copy(addingWarehouse = true, selectedWarehouseId = null)
    }

    fun setWarehouseName(name: String) {
        _state.value = _state.value.copy(warehouseName = name)
    }

    fun onWarehouseQueryChange(text: String) {
        _state.value = _state.value.copy(warehouseQuery = text, pickedWarehouse = null)
        warehouseGeocodeJob?.cancel()
        if (text.length < MIN_QUERY) {
            _state.value = _state.value.copy(warehouseSuggestions = emptyList(), warehouseSearching = false)
            return
        }
        _state.value = _state.value.copy(warehouseSearching = true)
        warehouseGeocodeJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val result = geocodeRepository.autocomplete(text)
            if (_state.value.warehouseQuery == text) {
                // Keep existing suggestions on a transient failure; don't blank the dropdown.
                result
                    .onSuccess { _state.value = _state.value.copy(warehouseSuggestions = it, warehouseSearching = false) }
                    .onFailure { _state.value = _state.value.copy(warehouseSearching = false) }
            }
        }
    }

    fun pickWarehouseAddress(suggestion: AddressSuggestion) {
        warehouseGeocodeJob?.cancel()
        _state.value = _state.value.copy(
            pickedWarehouse = suggestion,
            warehouseQuery = suggestion.label,
            warehouseSuggestions = emptyList(),
            warehouseSearching = false,
            warehouseName = _state.value.warehouseName.ifBlank { suggestion.suburb ?: suggestion.label },
        )
    }

    // ----- Scheduled arrival -----

    fun setArrivalDate(millis: Long?) {
        _state.value = _state.value.copy(arrivalDateMillis = millis)
    }

    fun setArrivalTime(hour: Int, minute: Int) {
        _state.value = _state.value.copy(arrivalHour = hour, arrivalMinute = minute)
    }

    // ----- Submit -----

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        viewModelScope.launch {
            val problem = validationProblem(s)
            if (problem != null) return@launch setError(problem)

            _state.value = _state.value.copy(isSubmitting = true, error = null)
            val warehouse = resolveWarehouse(s)
            if (warehouse == null) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = getString(Res.string.package_error_save_warehouse_failed),
                )
                return@launch
            }
            // validationProblem already refused a submit with no arrival date.
            val arrivalDateMillis = checkNotNull(s.arrivalDateMillis) {
                "validationProblem should have rejected a package with no arrival date."
            }
            val draft = PackageDraft(
                organisationId = orgId,
                orgSlug = orgSlug,
                sender = customerInput(s.sender),
                receiver = customerInput(s.receiver),
                warehouseId = warehouse.id,
                weightKg = s.weight.trim().toDouble(),
                lengthCm = s.length.trim().toDouble(),
                widthCm = s.width.trim().toDouble(),
                heightCm = s.height.trim().toDouble(),
                deliveryNotes = s.deliveryNotes.trim(),
                scheduledArrival = combineDateAndTimeToIsoUtc(arrivalDateMillis, s.arrivalHour, s.arrivalMinute),
                images = s.images,
                // Default (true): a package added here should be on a shift before the driver has
                // put their phone away. Only the create-shift wizard opts out.
            )
            repository.createPackage(draft)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        assignment = result.assignment.toDisplay(),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = it.message ?: getString(Res.string.package_error_submit_failed),
                    )
                }
        }
    }

    /** Closes the confirmation panel and leaves the form; the package is already created. */
    fun dismissAssignment() {
        _state.value = _state.value.copy(assignment = null, done = true)
    }

    /**
     * [validationProblem] refuses a party with no picked address before this runs, so the address is
     * always present here — the check names the broken invariant if that ordering ever changes.
     */
    private fun customerInput(draft: CustomerDraft): ShiftCustomerInput = ShiftCustomerInput(
        name = draft.name.trim(),
        phoneE164 = customerE164(draft),
        address = checkNotNull(draft.picked) {
            "validationProblem should have rejected ${draft.localId} with no geocoded address."
        },
    )

    private suspend fun resolveWarehouse(s: AddPackageUiState): WarehouseOption? {
        if (!s.addingWarehouse) return s.warehouses.firstOrNull { it.id == s.selectedWarehouseId }
        val picked = s.pickedWarehouse ?: return null
        return repository.createWarehouse(orgId, s.warehouseName.trim(), picked).getOrNull()
    }

    private suspend fun validationProblem(s: AddPackageUiState): String? {
        val weight = s.weight.trim().toDoubleOrNull()
        val length = s.length.trim().toDoubleOrNull()
        val width = s.width.trim().toDoubleOrNull()
        val height = s.height.trim().toDoubleOrNull()
        return when {
            weight == null || weight <= 0 -> getString(Res.string.package_error_invalid_weight)
            length == null || length <= 0 || width == null || width <= 0 || height == null || height <= 0 ->
                getString(Res.string.package_error_invalid_dimensions)
            s.sender.name.isBlank() -> getString(Res.string.package_error_sender_needs_name)
            customerE164(s.sender) == null -> getString(Res.string.package_error_sender_needs_phone)
            s.sender.picked == null -> getString(Res.string.package_error_sender_needs_address)
            s.receiver.name.isBlank() -> getString(Res.string.package_error_receiver_needs_name)
            customerE164(s.receiver) == null -> getString(Res.string.package_error_receiver_needs_phone)
            s.receiver.picked == null -> getString(Res.string.package_error_receiver_needs_address)
            s.addingWarehouse && s.pickedWarehouse == null -> getString(Res.string.package_error_choose_warehouse)
            s.addingWarehouse && s.warehouseName.isBlank() -> getString(Res.string.package_error_name_warehouse)
            !s.addingWarehouse && s.selectedWarehouseId == null ->
                getString(Res.string.package_error_choose_saved_warehouse)
            s.arrivalDateMillis == null -> getString(Res.string.package_error_choose_arrival)
            else -> null
        }
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    /** The draft's phone as canonical E.164, or null if invalid for the selected country. */
    private fun customerE164(draft: CustomerDraft): String? =
        if (PhoneNumbers.isValid(draft.phone, draft.countryIso)) {
            PhoneNumbers.toE164(draft.phone, draft.countryIso)
        } else {
            null
        }

    private companion object {
        const val MIN_QUERY = 3
        const val DEBOUNCE_MS = 300L
    }
}
