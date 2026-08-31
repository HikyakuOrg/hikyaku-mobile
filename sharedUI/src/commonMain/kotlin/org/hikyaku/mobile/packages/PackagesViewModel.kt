package org.hikyaku.mobile.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.error_load_packages
import hikyaku.sharedui.generated.resources.package_optimise_error_failed
import hikyaku.sharedui.generated.resources.package_optimise_error_none
import hikyaku.sharedui.generated.resources.package_optimise_error_no_warehouse
import hikyaku.sharedui.generated.resources.package_optimise_error_rate_limited
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.optimisation.OptimisationRateLimitedException
import org.hikyaku.mobile.optimisation.OptimisationRepository
import org.hikyaku.mobile.packages.model.PackageSummary
import org.hikyaku.mobile.packages.optimisation.OptimisationProgress
import org.hikyaku.mobile.warehouse.WarehouseRepository
import org.jetbrains.compose.resources.getString

data class PackagesUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val packages: List<PackageSummary> = emptyList(),
    val error: String? = null,
    /** Whether another page might exist; false once a fetch returns fewer than [PAGE_SIZE] rows. */
    val hasMore: Boolean = true,
    /** Non-null while the one-click optimise dialog is open (queuing, polling, or showing its outcome). */
    val optimisation: OptimisationProgress? = null,
    /** A one-off reason [startOptimisation] didn't open the dialog (no warehouse, nothing to optimise). */
    val optimisationToast: String? = null,
)

/**
 * Drives the package overview list for [orgId]: loads pages of [PAGE_SIZE] packages (newest
 * first) via [PackageRepository.fetchPackages], appending on [loadNextPage]. Also drives the
 * one-click [startOptimisation] action, which queues a warehouse-wide optimisation run via
 * [OptimisationRepository] and polls it to completion.
 */
class PackagesViewModel(
    private val orgId: String,
    private val orgSlug: String,
    private val repository: PackageRepository = PackageRepository(),
    private val warehouseRepository: WarehouseRepository = WarehouseRepository(),
    private val optimisationRepository: OptimisationRepository = OptimisationRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(PackagesUiState())
    val state: StateFlow<PackagesUiState> = _state.asStateFlow()

    private var optimisationJob: Job? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        _state.value = PackagesUiState(isLoading = true)
        viewModelScope.launch { loadPage(offset = 0, replace = true) }
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.value = s.copy(isLoadingMore = true)
        viewModelScope.launch { loadPage(offset = s.packages.size, replace = false) }
    }

    /** Re-fetches the first page for pull-to-refresh, keeping the current list visible while it loads. */
    fun refresh() {
        val s = _state.value
        if (s.isLoading || s.isRefreshing) return
        _state.value = s.copy(isRefreshing = true)
        viewModelScope.launch { loadPage(offset = 0, replace = true) }
    }

    /**
     * Resolves the org's warehouse and how many of its packages are unassigned, then queues a
     * warehouse-wide optimisation run and polls it to completion. A no-op while a run is already
     * showing; guard-clause failures (no warehouse, nothing unassigned) are surfaced as a toast
     * instead of opening the dialog.
     */
    fun startOptimisation() {
        if (_state.value.optimisation != null) return
        optimisationJob = viewModelScope.launch {
            val warehouseId = warehouseRepository.fetchWarehouses(orgId).getOrNull()?.firstOrNull()?.id
            if (warehouseId == null) {
                _state.value = _state.value.copy(optimisationToast = getString(Res.string.package_optimise_error_no_warehouse))
                return@launch
            }
            val packageCount = repository.countUnassignedPackages(orgId, warehouseId).getOrDefault(0)
            if (packageCount == 0) {
                _state.value = _state.value.copy(optimisationToast = getString(Res.string.package_optimise_error_none))
                return@launch
            }

            _state.value = _state.value.copy(optimisation = OptimisationProgress(packageCount = packageCount))

            val runId = optimisationRepository.runOptimisation(orgSlug, warehouseId).getOrElse {
                failOptimisation(
                    if (it is OptimisationRateLimitedException) {
                        getString(Res.string.package_optimise_error_rate_limited)
                    } else {
                        it.message ?: getString(Res.string.package_optimise_error_failed)
                    },
                )
                return@launch
            }
            pollUntilDone(runId)
        }
    }

    private suspend fun pollUntilDone(runId: String) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            val latest = optimisationRepository.fetchLatestRun(orgSlug).getOrNull() ?: continue
            if (latest.id != runId) continue
            when {
                latest.optimisationId != null -> {
                    _state.value = _state.value.copy(
                        optimisation = _state.value.optimisation?.copy(phase = OptimisationProgress.Phase.SUCCEEDED),
                    )
                    return
                }

                latest.status == "failed" || latest.status == "skipped" -> {
                    failOptimisation(latest.error ?: getString(Res.string.package_optimise_error_failed))
                    return
                }
            }
        }
    }

    private fun failOptimisation(message: String) {
        _state.value = _state.value.copy(
            optimisation = _state.value.optimisation?.copy(phase = OptimisationProgress.Phase.FAILED, message = message),
        )
    }

    /** Closes the optimise dialog, cancelling its poll loop if the run hadn't finished yet. */
    fun dismissOptimisation() {
        val succeeded = _state.value.optimisation?.phase == OptimisationProgress.Phase.SUCCEEDED
        optimisationJob?.cancel()
        optimisationJob = null
        _state.value = _state.value.copy(optimisation = null)
        if (succeeded) refresh()
    }

    private suspend fun loadPage(offset: Int, replace: Boolean) {
        // Fetch one extra row so a next page can be detected without a separate count query.
        val from = offset.toLong()
        val to = from + PAGE_SIZE
        repository.fetchPackages(orgId, from, to)
            .onSuccess { rows ->
                val hasMore = rows.size > PAGE_SIZE
                val pageItems = rows.take(PAGE_SIZE)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    packages = if (replace) pageItems else _state.value.packages + pageItems,
                    hasMore = hasMore,
                    error = null,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = it.message ?: getString(Res.string.error_load_packages),
                )
            }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val POLL_INTERVAL_MS = 2000L
    }
}
