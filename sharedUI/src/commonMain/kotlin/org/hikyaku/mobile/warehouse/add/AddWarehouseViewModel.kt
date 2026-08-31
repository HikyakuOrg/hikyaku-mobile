package org.hikyaku.mobile.warehouse.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.warehouse_error_choose_location
import hikyaku.sharedui.generated.resources.warehouse_error_name
import hikyaku.sharedui.generated.resources.warehouse_error_submit_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.geocode.GeocodeRepository
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.warehouse.WarehouseRepository
import org.jetbrains.compose.resources.getString

data class AddWarehouseUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val name: String = "",
    val query: String = "",
    val suggestions: List<AddressSuggestion> = emptyList(),
    val searching: Boolean = false,
    val picked: AddressSuggestion? = null,
)

/**
 * Drives the add-warehouse form: geocodes the typed address via [GeocodeRepository] (debounced),
 * validates the name and picked address, and on submit persists the warehouse through
 * [WarehouseRepository].
 */
class AddWarehouseViewModel(
    private val orgId: String,
    private val repository: WarehouseRepository = WarehouseRepository(),
    private val geocodeRepository: GeocodeRepository = GeocodeRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AddWarehouseUiState())
    val state: StateFlow<AddWarehouseUiState> = _state.asStateFlow()

    private var geocodeJob: Job? = null

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value)
    }

    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text, picked = null)
        geocodeJob?.cancel()
        if (text.length < MIN_QUERY) {
            _state.value = _state.value.copy(suggestions = emptyList(), searching = false)
            return
        }
        _state.value = _state.value.copy(searching = true)
        geocodeJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val result = geocodeRepository.autocomplete(text)
            if (_state.value.query == text) {
                // Keep existing suggestions on a transient failure; don't blank the dropdown.
                result
                    .onSuccess { _state.value = _state.value.copy(suggestions = it, searching = false) }
                    .onFailure { _state.value = _state.value.copy(searching = false) }
            }
        }
    }

    fun pickAddress(suggestion: AddressSuggestion) {
        geocodeJob?.cancel()
        _state.value = _state.value.copy(
            picked = suggestion,
            query = suggestion.label,
            suggestions = emptyList(),
            searching = false,
            name = _state.value.name.ifBlank { suggestion.suburb ?: suggestion.label },
        )
    }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        viewModelScope.launch {
            val problem = validationProblem(s)
            if (problem != null) return@launch setError(problem)
            // validationProblem already refused a submit with no picked address.
            val picked = checkNotNull(s.picked) { "validationProblem should have rejected a warehouse with no picked address." }

            _state.value = _state.value.copy(isSubmitting = true, error = null)
            repository.createWarehouse(orgId, s.name.trim(), picked)
                .onSuccess { _state.value = _state.value.copy(isSubmitting = false, done = true) }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = it.message ?: getString(Res.string.warehouse_error_submit_failed),
                    )
                }
        }
    }

    private suspend fun validationProblem(s: AddWarehouseUiState): String? = when {
        s.picked == null -> getString(Res.string.warehouse_error_choose_location)
        s.name.isBlank() -> getString(Res.string.warehouse_error_name)
        else -> null
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    private companion object {
        const val MIN_QUERY = 3
        const val DEBOUNCE_MS = 300L
    }
}
