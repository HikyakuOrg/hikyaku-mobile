package org.hikyaku.mobile.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.error_load_package
import hikyaku.sharedui.generated.resources.package_detail_delete_error
import io.github.jan.supabase.storage.StorageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.environment.EnvironmentStore
import org.hikyaku.mobile.environment.model.EnvironmentSource
import org.hikyaku.mobile.packages.model.PackageDetail
import org.hikyaku.mobile.tracking.buildTrackingUrl
import org.jetbrains.compose.resources.getString

data class PackageDetailUiState(
    val isLoading: Boolean = false,
    val detail: PackageDetail? = null,
    /** Proof-of-delivery photos, populated after the package itself loads. */
    val images: List<StorageItem> = emptyList(),
    val error: String? = null,
    val orgName: String = "",
    /** Public tracking-page URL for this package, null if the org slug wasn't available. */
    val trackingUrl: String? = null,
    /** Whether this package can be deleted: a personal-org courier viewing their own PENDING/ASSIGNED package. */
    val canDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    /** True once the delete has actually succeeded; the caller navigates away on this. */
    val isDeleted: Boolean = false,
)

/**
 * Drives the package detail screen: loads the aggregated [PackageDetail] for [trackingNumber] via
 * [PackageRepository.fetchPackageDetail], then its proof-of-delivery photos.
 */
class PackageDetailViewModel(
    private val trackingNumber: String,
    orgSlug: String = "",
    orgName: String = "",
    private val isPersonalOrg: Boolean = false,
    private val repository: PackageRepository = PackageRepository(),
    environmentStore: EnvironmentStore = EnvironmentStore(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        PackageDetailUiState(
            orgName = orgName,
            trackingUrl = orgSlug.takeIf { it.isNotBlank() }?.let {
                buildTrackingUrl(environmentStore.load()?.source ?: EnvironmentSource.Default, it, trackingNumber)
            },
        ),
    )
    val state: StateFlow<PackageDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.fetchPackageDetail(trackingNumber)
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        detail = detail,
                        error = null,
                        canDelete = isPersonalOrg && detail.currentStatusEnum in DELETABLE_STATUSES,
                    )
                    loadImages(detail.id)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: getString(Res.string.error_load_package),
                    )
                }
        }
    }

    /** Loads proof-of-delivery photos once the package id is known; failures leave the list empty. */
    private fun loadImages(packageId: String) {
        viewModelScope.launch {
            val images = repository.fetchPackageImages(packageId).getOrDefault(emptyList())
            _state.value = _state.value.copy(images = images)
        }
    }

    /**
     * Deletes the package currently loaded. Gated by [PackageDetailUiState.canDelete] on the caller
     * side, but re-checked here too since the button reflects the status at load time and a package
     * can move past PENDING/ASSIGNED while the screen is open — RLS then refuses the delete and
     * [PackageRepository.deletePackage] reports that as a failure rather than a silent no-op.
     */
    fun deletePackage() {
        val s = _state.value
        val detail = s.detail ?: return
        if (!s.canDelete || s.isDeleting) return
        _state.value = s.copy(isDeleting = true, deleteError = null)
        viewModelScope.launch {
            repository.deletePackage(detail.id)
                .onSuccess { _state.value = _state.value.copy(isDeleting = false, isDeleted = true) }
                .onFailure {
                    _state.value = _state.value.copy(
                        isDeleting = false,
                        deleteError = it.message ?: getString(Res.string.package_detail_delete_error),
                    )
                }
        }
    }

    fun dismissDeleteError() {
        _state.value = _state.value.copy(deleteError = null)
    }

    private companion object {
        val DELETABLE_STATUSES = setOf("PENDING", "ASSIGNED")
    }
}
