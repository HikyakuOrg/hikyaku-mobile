package org.hikyaku.mobile.vehicles.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.error_load_maintenance
import hikyaku.sharedui.generated.resources.error_load_vehicle
import io.github.jan.supabase.storage.StorageItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.vehicles.VehicleRepository
import org.hikyaku.mobile.vehicles.model.MaintenanceRecord
import org.hikyaku.mobile.vehicles.model.VehicleDetail
import org.jetbrains.compose.resources.getString

data class VehicleDetailUiState(
    val isLoading: Boolean = false,
    val vehicle: VehicleDetail? = null,
    /** The vehicle's own photos, populated after the vehicle itself loads. */
    val vehicleImages: List<StorageItem> = emptyList(),
    val maintenanceRecords: List<MaintenanceRecord> = emptyList(),
    /** Photo StorageItems per maintenance record id, populated after the records themselves load. */
    val maintenanceImages: Map<String, List<StorageItem>> = emptyMap(),
    val error: String? = null,
)

/**
 * Drives the vehicle detail screen: loads the `vehicles` row for [vehicleId] plus its
 * `vehicle_maintenance` service history, via [VehicleRepository].
 */
class VehicleDetailViewModel(
    private val vehicleId: String,
    private val repository: VehicleRepository = VehicleRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleDetailUiState())
    val state: StateFlow<VehicleDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val vehicleResult = repository.fetchVehicle(vehicleId)
            val vehicle = vehicleResult.getOrNull()
            if (vehicle == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = vehicleResult.exceptionOrNull()?.message ?: getString(Res.string.error_load_vehicle),
                )
                return@launch
            }
            val vehicleImages = repository.fetchVehicleImages(vehicleId).getOrDefault(emptyList())
            repository.fetchMaintenanceRecords(vehicleId)
                .onSuccess { records ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        vehicleImages = vehicleImages,
                        maintenanceRecords = records,
                        error = null,
                    )
                    loadImages(records)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        vehicleImages = vehicleImages,
                        error = it.message ?: getString(Res.string.error_load_maintenance),
                    )
                }
        }
    }

    /** Re-fetches just the maintenance history, e.g. after returning from the add-record screen. */
    fun refreshMaintenance() {
        viewModelScope.launch {
            repository.fetchMaintenanceRecords(vehicleId).onSuccess { records ->
                _state.value = _state.value.copy(maintenanceRecords = records)
                loadImages(records)
            }
        }
    }

    /** Loads each record's photos in parallel, mirroring how proof-of-delivery photos load per package. */
    private fun loadImages(records: List<MaintenanceRecord>) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            val results = coroutineScope {
                records.map { record ->
                    async { record.id to repository.fetchMaintenanceImages(record.id).getOrDefault(emptyList()) }
                }.map { it.await() }
            }
            _state.value = _state.value.copy(maintenanceImages = results.toMap())
        }
    }
}
