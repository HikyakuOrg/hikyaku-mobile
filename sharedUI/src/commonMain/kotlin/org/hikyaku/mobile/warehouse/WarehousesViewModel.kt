package org.hikyaku.mobile.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.error_load_warehouses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.warehouse.model.WarehouseOption
import org.jetbrains.compose.resources.getString

data class WarehousesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val warehouses: List<WarehouseOption> = emptyList(),
    val error: String? = null,
    /** False once a personal org has reached [PERSONAL_ORG_WAREHOUSE_LIMIT]. */
    val canAddWarehouse: Boolean = true,
)

/** Drives the standalone warehouse overview list. */
class WarehousesViewModel(
    private val orgId: String,
    private val isPersonalOrg: Boolean,
    private val repository: WarehouseRepository = WarehouseRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(WarehousesUiState())
    val state: StateFlow<WarehousesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch { fetch() }
    }

    /** Re-fetches for pull-to-refresh, keeping the current list visible while it loads. */
    fun refresh() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        repository.fetchWarehouses(orgId)
            .onSuccess { warehouses ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    warehouses = warehouses,
                    canAddWarehouse = canAddWarehouse(isPersonalOrg, warehouses.size),
                    error = null,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = it.message ?: getString(Res.string.error_load_warehouses),
                )
            }
    }
}
