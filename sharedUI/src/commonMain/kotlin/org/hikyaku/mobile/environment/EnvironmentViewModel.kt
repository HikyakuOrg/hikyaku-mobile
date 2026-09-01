package org.hikyaku.mobile.environment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.environment_error_invalid_url
import hikyaku.sharedui.generated.resources.environment_error_unreachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.environment.model.EnvironmentSource
import org.jetbrains.compose.resources.getString

/**
 * Owns the app's environment bootstrap: restores the persisted Supabase/Hikyaku config,
 * or fetches it from the hosted (or a self-hosted) instance on first launch, and rebuilds
 * the [SupabaseClientProvider] whenever the active instance changes.
 */
class EnvironmentViewModel(
    private val repository: EnvironmentRepository = EnvironmentRepository(),
) : ViewModel() {

    enum class Phase { Loading, Unconfigured, Configured }

    data class UiState(
        val phase: Phase = Phase.Loading,
        val source: EnvironmentSource? = null,
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        bootstrap()
    }

    private fun bootstrap() {
        val stored = repository.loadPersisted()
        if (stored != null) {
            viewModelScope.launch {
                SupabaseClientProvider.initialize(stored.config)
                _state.value = UiState(phase = Phase.Configured, source = stored.source)
            }
        } else {
            // First launch: resolve the default hosted environment automatically.
            connect(EnvironmentSource.Default)
        }
    }

    /** Retries resolving the default hosted (app.hikyaku.org) environment. */
    fun useDefault() = connect(EnvironmentSource.Default)

    /** Connects to a user-supplied self-hosted Hikyaku instance. */
    fun connectSelfHosted(rawUrl: String) {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            viewModelScope.launch {
                _state.value = _state.value.copy(
                    errorMessage = getString(Res.string.environment_error_invalid_url),
                )
            }
            return
        }
        connect(EnvironmentSource.SelfHosted(normalized))
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun connect(source: EnvironmentSource) {
        _state.value = _state.value.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            repository.configure(source)
                .onSuccess { config ->
                    SupabaseClientProvider.initialize(config)
                    _state.value = UiState(phase = Phase.Configured, source = source)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isBusy = false,
                        // If we were never configured, stay on the setup screen.
                        phase = if (_state.value.phase == Phase.Configured) {
                            Phase.Configured
                        } else {
                            Phase.Unconfigured
                        },
                        errorMessage = error.message
                            ?: getString(Res.string.environment_error_unreachable),
                    )
                }
        }
    }
}

/** Normalises user input into an http(s) base URL, or null if it can't be made valid. */
private fun normalizeUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    // Require at least a host after the scheme.
    val host = withScheme.substringAfter("://")
    if (host.isBlank() || host.contains(' ')) return null
    return withScheme
}
