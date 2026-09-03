package org.hikyaku.mobile.vehicles.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.vehicle_error_choose_type
import hikyaku.sharedui.generated.resources.vehicle_error_choose_warehouse
import hikyaku.sharedui.generated.resources.vehicle_error_invalid_gross_limits
import hikyaku.sharedui.generated.resources.vehicle_error_invalid_year
import hikyaku.sharedui.generated.resources.vehicle_error_submit_failed
import hikyaku.sharedui.generated.resources.vehicle_vin_decode_failed
import hikyaku.sharedui.generated.resources.vehicle_vin_decode_incomplete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.vehicles.VehicleRepository
import org.hikyaku.mobile.vehicles.model.VehicleDraft
import org.hikyaku.mobile.vehicles.model.VehicleTypeOption
import org.hikyaku.mobile.vehicles.model.VehicleWarehouseOption
import org.hikyaku.mobile.vehicles.vin.VinDecodeRepository
import org.hikyaku.mobile.vehicles.vin.isVinShaped
import org.jetbrains.compose.resources.getString

data class AddVehicleUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    // Free-text fields
    val model: String = "",
    val isModelReadOnly: Boolean = false,
    val make: String = "",
    val isMakeReadOnly: Boolean = false,
    val plate: String = "",
    val vin: String = "",
    val isDecodingVin: Boolean = false,
    val year: String = "",
    val isYearReadOnly: Boolean = false,
    val grossLimits: String = "",
    // Vehicle type (required)
    val vehicleTypes: List<VehicleTypeOption> = emptyList(),
    val selectedVehicleTypeId: String? = null,
    // Warehouse (required)
    val warehouses: List<VehicleWarehouseOption> = emptyList(),
    val selectedWarehouseId: String? = null,
    // Photos (optional)
    val images: List<ByteArray> = emptyList(),
)

/**
 * Drives the add-vehicle form: loads the org's vehicle type and warehouse options, validates the
 * required fields, and on submit persists the new `vehicles` row through [VehicleRepository].
 */
class AddVehicleViewModel(
    private val orgId: String,
    private val isPersonalOrg: Boolean,
    private val repository: VehicleRepository = VehicleRepository(),
    private val vinDecodeRepository: VinDecodeRepository = VinDecodeRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AddVehicleUiState())
    val state: StateFlow<AddVehicleUiState> = _state.asStateFlow()

    /** The last VIN a decode was attempted for, so retyping the same 17 characters doesn't refire it. */
    private var lastAttemptedVinDecode: String? = null

    /**
     * The VIN whose decode currently backs the read-only make/model/year fields, or null if none
     * are locked. Compared against on every [setVin] edit — the moment the field no longer matches
     * this, the fields it locked are freed for manual entry again, whether or not a new decode ends
     * up succeeding.
     */
    private var lockedForVin: String? = null

    init {
        loadOptions()
    }

    private fun loadOptions() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val vehicleTypes = repository.fetchVehicleTypes().getOrDefault(emptyList())
            val warehouses = repository.fetchWarehouses(orgId).getOrDefault(emptyList())
            _state.value = _state.value.copy(
                isLoading = false,
                vehicleTypes = vehicleTypes,
                selectedVehicleTypeId = vehicleTypes.singleOrNull()?.id,
                warehouses = warehouses,
                selectedWarehouseId = warehouses.singleOrNull()?.id,
            )
        }
    }

    fun setModel(value: String) {
        _state.value = _state.value.copy(model = value)
    }

    fun setMake(value: String) {
        _state.value = _state.value.copy(make = value)
    }

    fun setPlate(value: String) {
        _state.value = _state.value.copy(plate = value)
    }

    /**
     * Sets the VIN field and, once it reaches a full 17 characters — however it got there, scanned
     * or typed — fires a debounced-by-content decode against the backend to prefill make/model/year.
     * Re-editing back to a VIN already attempted does not refire it.
     *
     * Whatever make/model/year a decode locked (see [decodeVin]) stays locked only while the VIN
     * field still reads exactly the VIN that produced it — any other edit frees them again, even
     * before a replacement decode (if any) comes back.
     */
    fun setVin(value: String) {
        val candidate = value.trim().uppercase()
        val stillLocked = candidate == lockedForVin
        val s = _state.value
        _state.value = s.copy(
            vin = value,
            isMakeReadOnly = s.isMakeReadOnly && stillLocked,
            isModelReadOnly = s.isModelReadOnly && stillLocked,
            isYearReadOnly = s.isYearReadOnly && stillLocked,
        )
        if (!isVinShaped(candidate) || candidate == lastAttemptedVinDecode) return
        lastAttemptedVinDecode = candidate
        decodeVin(candidate)
    }

    /**
     * Decodes [vin] and, for each of make/model/year the response actually resolved, both fills it
     * in and locks it read-only — a returned value is authoritative, so it shouldn't be hand-edited
     * out from under the VIN that produced it. A field the decode left empty is untouched and stays
     * editable, ready for manual entry.
     */
    private fun decodeVin(vin: String) {
        _state.value = _state.value.copy(isDecodingVin = true, error = null)
        viewModelScope.launch {
            vinDecodeRepository.decode(vin)
                .onSuccess { result ->
                    val s = _state.value
                    lockedForVin = vin
                    _state.value = s.copy(
                        isDecodingVin = false,
                        make = result.make ?: s.make,
                        isMakeReadOnly = result.make != null,
                        model = result.model ?: s.model,
                        isModelReadOnly = result.model != null,
                        year = result.year?.toString() ?: s.year,
                        isYearReadOnly = result.year != null,
                    )
                    if (!result.isComplete) setError(getString(Res.string.vehicle_vin_decode_incomplete))
                }
                .onFailure {
                    _state.value = _state.value.copy(isDecodingVin = false)
                    setError(getString(Res.string.vehicle_vin_decode_failed))
                }
        }
    }

    fun setYear(value: String) {
        _state.value = _state.value.copy(year = value)
    }

    fun setGrossLimits(value: String) {
        _state.value = _state.value.copy(grossLimits = value)
    }

    fun selectVehicleType(id: String) {
        _state.value = _state.value.copy(selectedVehicleTypeId = id)
    }

    fun selectWarehouse(id: String) {
        _state.value = _state.value.copy(selectedWarehouseId = id)
    }

    /**
     * Re-fetches warehouses, e.g. after the user returns from the "add warehouse" screen reached
     * via [AddVehicleUiState.warehouses] being empty. Keeps the current selection if it's still
     * present, otherwise falls back to the only warehouse (if there's exactly one).
     */
    fun refreshWarehouses() {
        viewModelScope.launch {
            val warehouses = repository.fetchWarehouses(orgId).getOrDefault(emptyList())
            val s = _state.value
            _state.value = s.copy(
                warehouses = warehouses,
                selectedWarehouseId = warehouses.firstOrNull { it.id == s.selectedWarehouseId }?.id
                    ?: warehouses.singleOrNull()?.id,
            )
        }
    }

    fun addImages(images: List<ByteArray>) {
        if (images.isEmpty()) return
        _state.value = _state.value.copy(images = _state.value.images + images)
    }

    fun removeImage(index: Int) {
        _state.value = _state.value.copy(images = _state.value.images.filterIndexed { i, _ -> i != index })
    }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        viewModelScope.launch {
            val problem = validationProblem(s)
            if (problem != null) return@launch setError(problem)

            _state.value = _state.value.copy(isSubmitting = true, error = null)
            val draft = VehicleDraft(
                organisationId = orgId,
                vehiclePlate = s.plate.trim().takeIf { it.isNotBlank() },
                vehicleIdentificationNumber = s.vin.trim().takeIf { it.isNotBlank() },
                vehicleMake = s.make.trim().takeIf { it.isNotBlank() },
                vehicleModel = s.model.trim().takeIf { it.isNotBlank() },
                vehicleYear = s.year.trim().toInt(),
                vehicleTypeId = s.selectedVehicleTypeId!!,
                vehicleGrossLimits = s.grossLimits.trim().toDouble(),
                warehouseId = s.selectedWarehouseId!!,
                images = s.images,
            )
            repository.createVehicle(draft, assignToSelf = isPersonalOrg)
                .onSuccess { _state.value = _state.value.copy(isSubmitting = false, done = true) }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = it.message ?: getString(Res.string.vehicle_error_submit_failed),
                    )
                }
        }
    }

    private suspend fun validationProblem(s: AddVehicleUiState): String? {
        val year = s.year.trim().toIntOrNull()
        val grossLimits = s.grossLimits.trim().toDoubleOrNull()
        return when {
            year == null || year <= 0 -> getString(Res.string.vehicle_error_invalid_year)
            grossLimits == null || grossLimits <= 0 -> getString(Res.string.vehicle_error_invalid_gross_limits)
            s.selectedVehicleTypeId == null -> getString(Res.string.vehicle_error_choose_type)
            s.selectedWarehouseId == null -> getString(Res.string.vehicle_error_choose_warehouse)
            else -> null
        }
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }
}
