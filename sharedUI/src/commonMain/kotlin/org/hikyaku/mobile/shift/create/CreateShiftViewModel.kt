package org.hikyaku.mobile.shift.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.create_shift_error_choose_date
import hikyaku.sharedui.generated.resources.create_shift_error_choose_location
import hikyaku.sharedui.generated.resources.create_shift_error_choose_saved_location
import hikyaku.sharedui.generated.resources.create_shift_error_choose_vehicle
import hikyaku.sharedui.generated.resources.create_shift_error_load_vehicle_types
import hikyaku.sharedui.generated.resources.create_shift_error_name_location
import hikyaku.sharedui.generated.resources.create_shift_error_no_packages
import hikyaku.sharedui.generated.resources.create_shift_error_package_conflict
import hikyaku.sharedui.generated.resources.create_shift_error_save_location_failed
import hikyaku.sharedui.generated.resources.create_shift_error_submit_failed
import hikyaku.sharedui.generated.resources.package_error_choose_arrival
import hikyaku.sharedui.generated.resources.package_error_invalid_dimensions
import hikyaku.sharedui.generated.resources.package_error_invalid_weight
import hikyaku.sharedui.generated.resources.package_error_receiver_needs_address
import hikyaku.sharedui.generated.resources.package_error_receiver_needs_name
import hikyaku.sharedui.generated.resources.package_error_receiver_needs_phone
import hikyaku.sharedui.generated.resources.package_error_sender_needs_address
import hikyaku.sharedui.generated.resources.package_error_sender_needs_name
import hikyaku.sharedui.generated.resources.package_error_sender_needs_phone
import hikyaku.sharedui.generated.resources.package_error_submit_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.hikyaku.mobile.geocode.GeocodeRepository
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.packages.PackageRepository
import org.hikyaku.mobile.packages.model.PackageDraft
import org.hikyaku.mobile.phone.PhoneNumbers
import org.hikyaku.mobile.shift.create.model.CreateShiftDraft
import org.hikyaku.mobile.shift.create.model.CustomerDraftSnapshot
import org.hikyaku.mobile.shift.create.model.CustomerSuggestion
import org.hikyaku.mobile.shift.create.model.SelectablePackage
import org.hikyaku.mobile.shift.create.model.ShiftCustomerInput
import org.hikyaku.mobile.shift.create.model.ShiftSubmission
import org.hikyaku.mobile.shift.create.model.VehicleOption
import org.hikyaku.mobile.shift.create.model.WarehouseOption
import org.hikyaku.mobile.util.combineDateAndTimeToIsoUtc
import org.hikyaku.mobile.warehouse.canAddWarehouse
import org.jetbrains.compose.resources.getString

/** Steps of the create-shift wizard. */
enum class CreateShiftStep { Details, Packages, Review }

/** Which party a package-form edit applies to. */
private enum class Party { SENDER, RECEIVER }

/**
 * A sender/receiver being composed for a new package. [localId] is a stable UI key; [picked] is the
 * geocoded address. [phone] is the national number as typed; [countryIso] is the selected region
 * (drives dial code + validation). The two combine into the E.164 string persisted as
 * `customer_phone`.
 */
data class CustomerDraft(
    val localId: String,
    val name: String = "",
    val nameSuggestions: List<CustomerSuggestion> = emptyList(),
    val nameSearching: Boolean = false,
    val phone: String = "",
    val countryIso: String = "",
    val addressQuery: String = "",
    val suggestions: List<AddressSuggestion> = emptyList(),
    val searching: Boolean = false,
    val picked: AddressSuggestion? = null,
)

data class CreateShiftUiState(
    val step: CreateShiftStep = CreateShiftStep.Details,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    // Details
    val vehicles: List<VehicleOption> = emptyList(),
    val selectedVehicleId: String? = null,
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
    val startDateMillis: Long? = null,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    /** The warehouse actually persisted for this shift, resolved once Details is confirmed. */
    val resolvedWarehouseId: String? = null,
    // Packages
    val packagesLoading: Boolean = false,
    val availablePackages: List<SelectablePackage> = emptyList(),
    val selectedPackageIds: Set<String> = emptySet(),
    val showAddPackageForm: Boolean = false,
    val isCreatingPackage: Boolean = false,
    val packageWeight: String = "",
    val packageLength: String = "",
    val packageWidth: String = "",
    val packageHeight: String = "",
    val packageImages: List<ByteArray> = emptyList(),
    val packageSender: CustomerDraft = CustomerDraft(localId = "sender"),
    val packageReceiver: CustomerDraft = CustomerDraft(localId = "receiver"),
    val packageDeliveryNotes: String = "",
    val packageArrivalDateMillis: Long? = null,
    val packageArrivalHour: Int = 12,
    val packageArrivalMinute: Int = 0,
)

/**
 * Drives the personal-org "create shift" wizard: loads vehicle types + saved warehouses, resolves
 * (or creates) the starting warehouse as soon as Details is confirmed, then lets the user attach one
 * or more `packages` to the shift — picked from the org's unassigned packages at that warehouse, or
 * composed fresh (sender, receiver, dimensions, arrival) and persisted immediately via
 * [PackageRepository]. On approval, the chosen package ids are handed to [CreateShiftRepository] to
 * submit for optimisation.
 */
class CreateShiftViewModel(
    private val orgId: String,
    private val orgSlug: String,
    private val isPersonalOrg: Boolean,
    private val repository: CreateShiftRepository = CreateShiftRepository(),
    private val geocodeRepository: GeocodeRepository = GeocodeRepository(),
    private val packageRepository: PackageRepository = PackageRepository(),
    private val draftStore: CreateShiftDraftStore = CreateShiftDraftStore(),
) : ViewModel() {

    private val _state = MutableStateFlow(CreateShiftUiState())
    val state: StateFlow<CreateShiftUiState> = _state.asStateFlow()

    // Device region, used to pre-select the phone country for a new package's sender/receiver.
    private val defaultCountryIso = PhoneNumbers.defaultCountryIso()
    private var warehouseGeocodeJob: Job? = null
    private val packageGeocodeJobs = mutableMapOf<Party, Job>()
    private val packageNameJobs = mutableMapOf<Party, Job>()

    init {
        val draft = draftStore.load(orgId)
        if (draft != null) applyDraft(draft)
        loadInitial(draft)
        observeDraftPersistence()
    }

    /** Reapplies a previously persisted draft's keyed-in values onto the initial (blank) state. */
    private fun applyDraft(draft: CreateShiftDraft) {
        _state.value = _state.value.copy(
            step = runCatching { CreateShiftStep.valueOf(draft.step) }.getOrDefault(CreateShiftStep.Details),
            selectedVehicleId = draft.selectedVehicleId,
            selectedWarehouseId = draft.selectedWarehouseId,
            addingWarehouse = draft.addingWarehouse,
            warehouseName = draft.warehouseName,
            warehouseQuery = draft.warehouseQuery,
            pickedWarehouse = draft.pickedWarehouse,
            startDateMillis = draft.startDateMillis,
            startHour = draft.startHour,
            startMinute = draft.startMinute,
            resolvedWarehouseId = draft.resolvedWarehouseId,
            selectedPackageIds = draft.selectedPackageIds,
            showAddPackageForm = draft.showAddPackageForm,
            packageWeight = draft.packageWeight,
            packageLength = draft.packageLength,
            packageWidth = draft.packageWidth,
            packageHeight = draft.packageHeight,
            packageSender = draft.packageSender.toCustomerDraft(defaultCountryIso),
            packageReceiver = draft.packageReceiver.toCustomerDraft(defaultCountryIso),
            packageDeliveryNotes = draft.packageDeliveryNotes,
            packageArrivalDateMillis = draft.packageArrivalDateMillis,
            packageArrivalHour = draft.packageArrivalHour,
            packageArrivalMinute = draft.packageArrivalMinute,
        )
    }

    /**
     * Persists the keyed-in form fields on every (debounced) change, so the draft survives process
     * death — e.g. the OS killing the app in the background while the screen is off. Cleared on a
     * successful [submit] or an explicit [discardDraft].
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeDraftPersistence() {
        viewModelScope.launch {
            state.debounce(DRAFT_DEBOUNCE_MS).collect { s ->
                if (s.done) return@collect
                draftStore.save(s.toDraft(orgId))
            }
        }
    }

    /** Clears the persisted draft, e.g. when the user explicitly cancels the wizard. */
    fun discardDraft() {
        draftStore.clear()
    }

    private fun loadInitial(restoredDraft: CreateShiftDraft?) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val vehicles = repository.fetchVehicles(orgId).getOrElse {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message ?: getString(Res.string.create_shift_error_load_vehicle_types),
                )
                return@launch
            }
            val warehouses = repository.fetchWarehouses(orgId).getOrDefault(emptyList())
            val selectedVehicleId = if (restoredDraft != null) {
                restoredDraft.selectedVehicleId?.takeIf { id -> vehicles.any { it.id == id } }
            } else {
                vehicles.firstOrNull()?.id
            }
            val selectedWarehouseId = if (restoredDraft != null) {
                restoredDraft.selectedWarehouseId?.takeIf { id -> warehouses.any { it.id == id } }
            } else {
                warehouses.singleOrNull()?.id
            }
            val canAdd = canAddWarehouse(isPersonalOrg, warehouses.size)
            _state.value = _state.value.copy(
                isLoading = false,
                vehicles = vehicles,
                selectedVehicleId = selectedVehicleId,
                warehouses = warehouses,
                selectedWarehouseId = selectedWarehouseId,
                addingWarehouse = (restoredDraft?.addingWarehouse ?: warehouses.isEmpty()) && canAdd,
                canAddWarehouse = canAdd,
            )
            // Resume mid-flow: the draft may have already moved past Details, so the picked
            // packages for its resolved warehouse need to be reloaded (availablePackages isn't
            // itself persisted).
            restoredDraft?.resolvedWarehouseId?.let { loadPackages(it) }
        }
    }

    /**
     * Re-fetches vehicles, e.g. after the user returns from the "add vehicle" screen. Keeps the
     * current selection if it's still present, otherwise falls back to the first vehicle.
     */
    fun refreshVehicles() {
        viewModelScope.launch {
            val vehicles = repository.fetchVehicles(orgId).getOrElse {
                _state.value = _state.value.copy(error = it.message ?: getString(Res.string.create_shift_error_load_vehicle_types))
                return@launch
            }
            val s = _state.value
            _state.value = s.copy(
                vehicles = vehicles,
                selectedVehicleId = vehicles.firstOrNull { it.id == s.selectedVehicleId }?.id ?: vehicles.firstOrNull()?.id,
            )
        }
    }

    // ----- Details -----

    fun selectVehicle(id: String) {
        _state.value = _state.value.copy(selectedVehicleId = id)
    }

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

    fun setStartDate(millis: Long?) {
        _state.value = _state.value.copy(startDateMillis = millis)
    }

    fun setStartTime(hour: Int, minute: Int) {
        _state.value = _state.value.copy(startHour = hour, startMinute = minute)
    }

    // ----- Packages: picking an existing one -----

    fun togglePackageSelection(id: String) {
        val selected = _state.value.selectedPackageIds
        _state.value = _state.value.copy(
            selectedPackageIds = if (id in selected) selected - id else selected + id,
        )
    }

    // ----- Packages: composing a new one -----

    fun startAddPackage() {
        _state.value = _state.value.copy(
            showAddPackageForm = true,
            packageWeight = "",
            packageLength = "",
            packageWidth = "",
            packageHeight = "",
            packageImages = emptyList(),
            packageSender = CustomerDraft(localId = "sender", countryIso = defaultCountryIso),
            packageReceiver = CustomerDraft(localId = "receiver", countryIso = defaultCountryIso),
            packageDeliveryNotes = "",
            packageArrivalDateMillis = null,
            packageArrivalHour = 12,
            packageArrivalMinute = 0,
        )
    }

    fun cancelAddPackage() {
        packageGeocodeJobs.values.forEach { it.cancel() }
        packageGeocodeJobs.clear()
        packageNameJobs.values.forEach { it.cancel() }
        packageNameJobs.clear()
        _state.value = _state.value.copy(showAddPackageForm = false)
    }

    fun setPackageWeight(value: String) {
        _state.value = _state.value.copy(packageWeight = value)
    }

    fun setPackageLength(value: String) {
        _state.value = _state.value.copy(packageLength = value)
    }

    fun setPackageWidth(value: String) {
        _state.value = _state.value.copy(packageWidth = value)
    }

    fun setPackageHeight(value: String) {
        _state.value = _state.value.copy(packageHeight = value)
    }

    fun addPackageImages(images: List<ByteArray>) {
        if (images.isEmpty()) return
        _state.value = _state.value.copy(packageImages = _state.value.packageImages + images)
    }

    fun removePackageImage(index: Int) {
        _state.value = _state.value.copy(packageImages = _state.value.packageImages.filterIndexed { i, _ -> i != index })
    }

    fun setPackageDeliveryNotes(notes: String) {
        _state.value = _state.value.copy(packageDeliveryNotes = notes)
    }

    fun setPackageArrivalDate(millis: Long?) {
        _state.value = _state.value.copy(packageArrivalDateMillis = millis)
    }

    fun setPackageArrivalTime(hour: Int, minute: Int) {
        _state.value = _state.value.copy(packageArrivalHour = hour, packageArrivalMinute = minute)
    }

    fun setPackageSenderName(name: String) = setPackageCustomerName(Party.SENDER, name)
    fun setPackageReceiverName(name: String) = setPackageCustomerName(Party.RECEIVER, name)

    fun setPackageSenderPhone(phone: String) = updatePackageCustomer(Party.SENDER) { it.copy(phone = phone) }
    fun setPackageReceiverPhone(phone: String) = updatePackageCustomer(Party.RECEIVER) { it.copy(phone = phone) }

    fun setPackageSenderCountry(countryIso: String) = updatePackageCustomer(Party.SENDER) { it.copy(countryIso = countryIso) }
    fun setPackageReceiverCountry(countryIso: String) = updatePackageCustomer(Party.RECEIVER) { it.copy(countryIso = countryIso) }

    fun onPackageSenderQueryChange(text: String) = onPackageAddressQueryChange(Party.SENDER, text)
    fun onPackageReceiverQueryChange(text: String) = onPackageAddressQueryChange(Party.RECEIVER, text)

    fun pickPackageSenderAddress(suggestion: AddressSuggestion) = pickPackageAddress(Party.SENDER, suggestion)
    fun pickPackageReceiverAddress(suggestion: AddressSuggestion) = pickPackageAddress(Party.RECEIVER, suggestion)

    fun pickPackageSenderSuggestion(suggestion: CustomerSuggestion) = pickPackageCustomerSuggestion(Party.SENDER, suggestion)
    fun pickPackageReceiverSuggestion(suggestion: CustomerSuggestion) = pickPackageCustomerSuggestion(Party.RECEIVER, suggestion)

    private fun setPackageCustomerName(party: Party, name: String) {
        updatePackageCustomer(party) { it.copy(name = name) }
        packageNameJobs.remove(party)?.cancel()
        if (name.trim().length < MIN_QUERY) {
            updatePackageCustomer(party) { it.copy(nameSuggestions = emptyList(), nameSearching = false) }
            return
        }
        packageNameJobs[party] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            updatePackageCustomer(party) { it.copy(nameSearching = true) }
            val results = packageRepository.searchCustomers(orgId, name.trim()).getOrDefault(emptyList())
            if (packageCustomerOf(party).name == name) {
                updatePackageCustomer(party) { it.copy(nameSuggestions = results, nameSearching = false) }
            }
        }
    }

    private fun pickPackageCustomerSuggestion(party: Party, suggestion: CustomerSuggestion) {
        packageNameJobs.remove(party)?.cancel()
        packageGeocodeJobs.remove(party)?.cancel()
        val parsed = suggestion.phoneE164?.let { PhoneNumbers.parseE164(it) }
        updatePackageCustomer(party) {
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

    private fun onPackageAddressQueryChange(party: Party, text: String) {
        updatePackageCustomer(party) { it.copy(addressQuery = text, picked = null) }
        packageGeocodeJobs.remove(party)?.cancel()
        if (text.length < MIN_QUERY) {
            updatePackageCustomer(party) { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        updatePackageCustomer(party) { it.copy(searching = true) }
        packageGeocodeJobs[party] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val result = geocodeRepository.autocomplete(text)
            if (packageCustomerOf(party).addressQuery == text) {
                // Keep existing suggestions on a transient failure; don't blank the dropdown.
                result
                    .onSuccess { updatePackageCustomer(party) { c -> c.copy(suggestions = it, searching = false) } }
                    .onFailure { updatePackageCustomer(party) { c -> c.copy(searching = false) } }
            }
        }
    }

    private fun pickPackageAddress(party: Party, suggestion: AddressSuggestion) {
        packageGeocodeJobs.remove(party)?.cancel()
        updatePackageCustomer(party) {
            it.copy(picked = suggestion, addressQuery = suggestion.label, suggestions = emptyList(), searching = false)
        }
    }

    private fun packageCustomerOf(party: Party): CustomerDraft =
        if (party == Party.SENDER) _state.value.packageSender else _state.value.packageReceiver

    private fun updatePackageCustomer(party: Party, transform: (CustomerDraft) -> CustomerDraft) {
        _state.value = when (party) {
            Party.SENDER -> _state.value.copy(packageSender = transform(_state.value.packageSender))
            Party.RECEIVER -> _state.value.copy(packageReceiver = transform(_state.value.packageReceiver))
        }
    }

    /**
     * Persists the composed package immediately (same write path as the standalone add-package
     * screen), then selects it and refreshes the available-package list so its tracking number
     * shows up consistently in both the picker and the review step. The Packages step is only
     * reachable once [next] has resolved a warehouse, so [CreateShiftUiState.resolvedWarehouseId]
     * is always set here.
     */
    fun confirmAddPackage() {
        val s = _state.value
        if (s.isCreatingPackage) return
        viewModelScope.launch {
            val problem = newPackageProblem(s)
            if (problem != null) return@launch setError(problem)

            _state.value = _state.value.copy(isCreatingPackage = true, error = null)
            val draft = PackageDraft(
                organisationId = orgId,
                sender = packageCustomerInput(s.packageSender),
                receiver = packageCustomerInput(s.packageReceiver),
                warehouseId = s.resolvedWarehouseId!!,
                weightKg = s.packageWeight.trim().toDouble(),
                lengthCm = s.packageLength.trim().toDouble(),
                widthCm = s.packageWidth.trim().toDouble(),
                heightCm = s.packageHeight.trim().toDouble(),
                deliveryNotes = s.packageDeliveryNotes.trim(),
                scheduledArrival = combineDateAndTimeToIsoUtc(
                    s.packageArrivalDateMillis!!,
                    s.packageArrivalHour,
                    s.packageArrivalMinute,
                ),
                images = s.packageImages,
            )
            packageRepository.createPackage(draft)
                .onSuccess { newId ->
                    _state.value = _state.value.copy(
                        isCreatingPackage = false,
                        showAddPackageForm = false,
                        selectedPackageIds = _state.value.selectedPackageIds + newId,
                    )
                    loadPackages(s.resolvedWarehouseId)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isCreatingPackage = false,
                        error = it.message ?: getString(Res.string.package_error_submit_failed),
                    )
                }
        }
    }

    private fun packageCustomerInput(draft: CustomerDraft): ShiftCustomerInput = ShiftCustomerInput(
        name = draft.name.trim(),
        phoneE164 = customerE164(draft),
        address = draft.picked!!,
    )

    private suspend fun newPackageProblem(s: CreateShiftUiState): String? {
        val weight = s.packageWeight.trim().toDoubleOrNull()
        val length = s.packageLength.trim().toDoubleOrNull()
        val width = s.packageWidth.trim().toDoubleOrNull()
        val height = s.packageHeight.trim().toDoubleOrNull()
        return when {
            weight == null || weight <= 0 -> getString(Res.string.package_error_invalid_weight)
            length == null || length <= 0 || width == null || width <= 0 || height == null || height <= 0 ->
                getString(Res.string.package_error_invalid_dimensions)
            s.packageSender.name.isBlank() -> getString(Res.string.package_error_sender_needs_name)
            customerE164(s.packageSender) == null -> getString(Res.string.package_error_sender_needs_phone)
            s.packageSender.picked == null -> getString(Res.string.package_error_sender_needs_address)
            s.packageReceiver.name.isBlank() -> getString(Res.string.package_error_receiver_needs_name)
            customerE164(s.packageReceiver) == null -> getString(Res.string.package_error_receiver_needs_phone)
            s.packageReceiver.picked == null -> getString(Res.string.package_error_receiver_needs_address)
            s.packageArrivalDateMillis == null -> getString(Res.string.package_error_choose_arrival)
            else -> null
        }
    }

    private fun loadPackages(warehouseId: String) {
        _state.value = _state.value.copy(packagesLoading = true)
        viewModelScope.launch {
            val packages = repository.fetchAvailablePackages(orgId, warehouseId).getOrDefault(emptyList())
            _state.value = _state.value.copy(availablePackages = packages, packagesLoading = false)
        }
    }

    // ----- Navigation between steps -----

    fun next() {
        val s = _state.value
        when (s.step) {
            CreateShiftStep.Details -> viewModelScope.launch {
                val problem = detailsProblem(s)
                if (problem != null) return@launch setError(problem)

                _state.value = s.copy(isLoading = true, error = null)
                val resolved = resolveWarehouse(s)
                if (resolved == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = getString(Res.string.create_shift_error_save_location_failed),
                    )
                    return@launch
                }
                val current = _state.value
                val warehouseChanged = resolved.id != current.resolvedWarehouseId
                _state.value = current.copy(
                    isLoading = false,
                    step = CreateShiftStep.Packages,
                    resolvedWarehouseId = resolved.id,
                    selectedWarehouseId = resolved.id,
                    addingWarehouse = false,
                    warehouses = if (current.warehouses.any { it.id == resolved.id }) current.warehouses else current.warehouses + resolved,
                    selectedPackageIds = if (warehouseChanged) emptySet() else current.selectedPackageIds,
                )
                loadPackages(resolved.id)
            }
            CreateShiftStep.Packages -> viewModelScope.launch {
                val problem = packagesProblem(s)
                if (problem != null) return@launch setError(problem)
                _state.value = s.copy(step = CreateShiftStep.Review, error = null)
            }
            CreateShiftStep.Review -> submit()
        }
    }

    fun back() {
        val s = _state.value
        val previous = when (s.step) {
            CreateShiftStep.Details -> CreateShiftStep.Details
            CreateShiftStep.Packages -> CreateShiftStep.Details
            CreateShiftStep.Review -> CreateShiftStep.Packages
        }
        _state.value = s.copy(step = previous, error = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun detailsProblem(s: CreateShiftUiState): String? = when {
        s.selectedVehicleId == null -> getString(Res.string.create_shift_error_choose_vehicle)
        s.startDateMillis == null -> getString(Res.string.create_shift_error_choose_date)
        s.addingWarehouse && s.pickedWarehouse == null -> getString(Res.string.create_shift_error_choose_location)
        s.addingWarehouse && s.warehouseName.isBlank() -> getString(Res.string.create_shift_error_name_location)
        !s.addingWarehouse && s.selectedWarehouseId == null ->
            getString(Res.string.create_shift_error_choose_saved_location)
        else -> null
    }

    private suspend fun packagesProblem(s: CreateShiftUiState): String? =
        if (s.selectedPackageIds.isEmpty()) getString(Res.string.create_shift_error_no_packages) else null

    // ----- Submit -----

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        _state.value = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val warehouseId = _state.value.resolvedWarehouseId
            if (warehouseId == null) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = getString(Res.string.create_shift_error_save_location_failed),
                )
                return@launch
            }
            // A vehicle with no type recorded can't resolve a routing profile server-side, so it
            // can't run a shift; reject it client-side before submitting.
            val selectedVehicle = s.vehicles.firstOrNull { it.id == s.selectedVehicleId }
            if (selectedVehicle?.vehicleTypeId == null) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = getString(Res.string.create_shift_error_choose_vehicle),
                )
                return@launch
            }
            // The DatePicker returns midnight UTC of the chosen day; departure adds the picked time.
            // driverId isn't collected here — the repository fills it in from the caller's own
            // session, since a driver can only create a shift for themselves.
            val submission = ShiftSubmission(
                orgId = orgId,
                orgSlug = orgSlug,
                warehouseId = warehouseId,
                vehicleId = selectedVehicle.id,
                startDateTime = combineDateAndTimeToIsoUtc(s.startDateMillis!!, s.startHour, s.startMinute),
                packageIds = s.selectedPackageIds.toList(),
            )
            repository.submitShift(submission)
                .onSuccess {
                    draftStore.clear()
                    _state.value = _state.value.copy(isSubmitting = false, done = true)
                }
                .onFailure {
                    if (it is PackageConflictException) {
                        recoverFromPackageConflict(warehouseId)
                    } else {
                        _state.value = _state.value.copy(
                            isSubmitting = false,
                            error = it.message ?: getString(Res.string.create_shift_error_submit_failed),
                        )
                    }
                }
        }
    }

    /**
     * A selected package turned out to already be claimed by another shift (the wizard's
     * available-packages snapshot went stale since the Packages step). Re-fetches the current
     * available list, drops any selection that's no longer in it, and sends the user back to
     * Packages to review rather than leaving them stuck on Review with a dead submission.
     */
    private suspend fun recoverFromPackageConflict(warehouseId: String) {
        val fresh = repository.fetchAvailablePackages(orgId, warehouseId).getOrDefault(emptyList())
        val freshIds = fresh.map { it.id }.toSet()
        _state.value = _state.value.copy(
            isSubmitting = false,
            step = CreateShiftStep.Packages,
            availablePackages = fresh,
            selectedPackageIds = _state.value.selectedPackageIds.intersect(freshIds),
            error = getString(Res.string.create_shift_error_package_conflict),
        )
    }

    /** Returns the chosen existing warehouse, or creates a new one from the picked address. */
    private suspend fun resolveWarehouse(s: CreateShiftUiState): WarehouseOption? {
        if (!s.addingWarehouse) return s.warehouses.firstOrNull { it.id == s.selectedWarehouseId }
        val picked = s.pickedWarehouse ?: return null
        return repository.createWarehouse(orgId, s.warehouseName.trim(), picked).getOrNull()
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    /**
     * The draft's phone as a canonical E.164 string (matching the `customer_phone` CHECK), or null
     * if the typed national number isn't valid for the selected country.
     */
    private fun customerE164(draft: CustomerDraft): String? =
        if (PhoneNumbers.isValid(draft.phone, draft.countryIso)) {
            PhoneNumbers.toE164(draft.phone, draft.countryIso)
        } else {
            null
        }

    private companion object {
        const val MIN_QUERY = 3
        const val DEBOUNCE_MS = 1500L
        const val DRAFT_DEBOUNCE_MS = 500L
    }
}

private fun CreateShiftUiState.toDraft(orgId: String): CreateShiftDraft = CreateShiftDraft(
    orgId = orgId,
    step = step.name,
    selectedVehicleId = selectedVehicleId,
    selectedWarehouseId = selectedWarehouseId,
    addingWarehouse = addingWarehouse,
    warehouseName = warehouseName,
    warehouseQuery = warehouseQuery,
    pickedWarehouse = pickedWarehouse,
    startDateMillis = startDateMillis,
    startHour = startHour,
    startMinute = startMinute,
    resolvedWarehouseId = resolvedWarehouseId,
    selectedPackageIds = selectedPackageIds,
    showAddPackageForm = showAddPackageForm,
    packageWeight = packageWeight,
    packageLength = packageLength,
    packageWidth = packageWidth,
    packageHeight = packageHeight,
    packageSender = packageSender.toSnapshot(),
    packageReceiver = packageReceiver.toSnapshot(),
    packageDeliveryNotes = packageDeliveryNotes,
    packageArrivalDateMillis = packageArrivalDateMillis,
    packageArrivalHour = packageArrivalHour,
    packageArrivalMinute = packageArrivalMinute,
)

private fun CustomerDraft.toSnapshot(): CustomerDraftSnapshot = CustomerDraftSnapshot(
    localId = localId,
    name = name,
    phone = phone,
    countryIso = countryIso,
    addressQuery = addressQuery,
    picked = picked,
)

private fun CustomerDraftSnapshot.toCustomerDraft(defaultCountryIso: String): CustomerDraft = CustomerDraft(
    localId = localId,
    name = name,
    phone = phone,
    countryIso = countryIso.ifBlank { defaultCountryIso },
    addressQuery = addressQuery,
    picked = picked,
)
