package org.hikyaku.mobile.vehicles.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.vehicle_error_choose_type
import hikyaku.sharedui.generated.resources.vehicle_error_choose_warehouse
import hikyaku.sharedui.generated.resources.vehicle_error_invalid_gross_limits
import hikyaku.sharedui.generated.resources.vehicle_error_invalid_year
import hikyaku.sharedui.generated.resources.vehicle_error_submit_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.vehicles.VehicleRepository
import org.hikyaku.mobile.vehicles.model.VehicleDraft
import org.hikyaku.mobile.vehicles.model.VehicleTypeOption
import org.hikyaku.mobile.vehicles.model.VehicleWarehouseOption
import org.jetbrains.compose.resources.getString

data class AddVehicleUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    // Free-text fields
    val model: String = "",
    val make: String = "",
    val plate: String = "",
    val vin: String = "",
    val year: String = "",
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
    private val repository: VehicleRepository = VehicleRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AddVehicleUiState())
    val state: StateFlow<AddVehicleUiState> = _state.asStateFlow()

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

    fun setVin(value: String) {
        _state.value = _state.value.copy(vin = value)
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
            repository.createVehicle(draft)
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
