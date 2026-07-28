package org.hikyaku.mobile.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.error_load_vehicles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.vehicles.model.VehicleSummary
import org.jetbrains.compose.resources.getString

data class VehiclesUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val vehicles: List<VehicleSummary> = emptyList(),
    val error: String? = null,
    /** Whether another page might exist; false once a fetch returns fewer than [PAGE_SIZE] rows. */
    val hasMore: Boolean = true,
)

/**
 * Drives the vehicle overview list for [orgId]: loads pages of [PAGE_SIZE] vehicles via
 * [VehicleRepository.fetchVehicles], appending on [loadNextPage].
 */
class VehiclesViewModel(
    private val orgId: String,
    private val repository: VehicleRepository = VehicleRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(VehiclesUiState())
    val state: StateFlow<VehiclesUiState> = _state.asStateFlow()

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        _state.value = VehiclesUiState(isLoading = true)
        viewModelScope.launch { loadPage(offset = 0, replace = true) }
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.value = s.copy(isLoadingMore = true)
        viewModelScope.launch { loadPage(offset = s.vehicles.size, replace = false) }
    }

    /** Re-fetches the first page for pull-to-refresh, keeping the current list visible while it loads. */
    fun refresh() {
        val s = _state.value
        if (s.isLoading || s.isRefreshing) return
        _state.value = s.copy(isRefreshing = true)
        viewModelScope.launch { loadPage(offset = 0, replace = true) }
    }

    private suspend fun loadPage(offset: Int, replace: Boolean) {
        // Fetch one extra row so a next page can be detected without a separate count query.
        val from = offset.toLong()
        val to = from + PAGE_SIZE
        repository.fetchVehicles(orgId, from, to)
            .onSuccess { rows ->
                val hasMore = rows.size > PAGE_SIZE
                val pageItems = rows.take(PAGE_SIZE)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    vehicles = if (replace) pageItems else _state.value.vehicles + pageItems,
                    hasMore = hasMore,
                    error = null,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = it.message ?: getString(Res.string.error_load_vehicles),
                )
            }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
