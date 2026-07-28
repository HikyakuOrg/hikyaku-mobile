package org.hikyaku.mobile.vehicles.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.maintenance_error_choose_date
import hikyaku.sharedui.generated.resources.maintenance_error_invalid_odometer
import hikyaku.sharedui.generated.resources.maintenance_error_missing_description
import hikyaku.sharedui.generated.resources.maintenance_error_submit_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.util.epochMillisToIsoDate
import org.hikyaku.mobile.vehicles.VehicleRepository
import org.hikyaku.mobile.vehicles.model.MaintenanceDraft
import org.jetbrains.compose.resources.getString

data class AddMaintenanceUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val odometer: String = "",
    val description: String = "",
    val dateServicedMillis: Long? = null,
    val images: List<ByteArray> = emptyList(),
)

/**
 * Drives the add-maintenance-record form: validates odometer/description/date, then persists a new
 * `vehicle_maintenance` row for [vehicleId] through [VehicleRepository].
 */
class AddMaintenanceViewModel(
    private val orgId: String,
    private val vehicleId: String,
    private val repository: VehicleRepository = VehicleRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AddMaintenanceUiState())
    val state: StateFlow<AddMaintenanceUiState> = _state.asStateFlow()

    fun setOdometer(value: String) {
        _state.value = _state.value.copy(odometer = value)
    }

    fun setDescription(value: String) {
        _state.value = _state.value.copy(description = value)
    }

    fun setDateServiced(millis: Long?) {
        _state.value = _state.value.copy(dateServicedMillis = millis)
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
            val draft = MaintenanceDraft(
                organisationId = orgId,
                vehicleId = vehicleId,
                odometer = s.odometer.trim().toDouble(),
                description = s.description.trim(),
                dateServiced = epochMillisToIsoDate(s.dateServicedMillis!!),
                images = s.images,
            )
            repository.addMaintenanceRecord(draft)
                .onSuccess { _state.value = _state.value.copy(isSubmitting = false, done = true) }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = it.message ?: getString(Res.string.maintenance_error_submit_failed),
                    )
                }
        }
    }

    private suspend fun validationProblem(s: AddMaintenanceUiState): String? {
        val odometer = s.odometer.trim().toDoubleOrNull()
        return when {
            odometer == null || odometer < 0 -> getString(Res.string.maintenance_error_invalid_odometer)
            s.description.trim().isEmpty() -> getString(Res.string.maintenance_error_missing_description)
            s.dateServicedMillis == null -> getString(Res.string.maintenance_error_choose_date)
            else -> null
        }
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }
}
