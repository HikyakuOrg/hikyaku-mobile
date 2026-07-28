package org.hikyaku.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import org.hikyaku.mobile.theme.HikyakuTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.coil.coil3
import org.hikyaku.mobile.auth.AuthNavGraph
import org.hikyaku.mobile.auth.AuthViewModel
import org.hikyaku.mobile.auth.NewPasswordScreen
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.environment.EnvironmentScreen
import org.hikyaku.mobile.environment.EnvironmentViewModel
import org.hikyaku.mobile.toast.LocalToastHostState

@Composable
@Preview
fun App() {
    HikyakuTheme {
        // The Surface paints the app background edge-to-edge (including behind the
        // status/navigation bars) so the transparent system bars sit on top of the
        // app colour rather than the white window background. safeContentPadding is
        // applied to the content only, so UI still avoids the system bars.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.safeContentPadding()) {
                val environmentViewModel: EnvironmentViewModel = viewModel { EnvironmentViewModel() }
                val environment by environmentViewModel.state.collectAsState()
                val toastHostState = remember { SnackbarHostState() }

                CompositionLocalProvider(LocalToastHostState provides toastHostState) {
                    when (environment.phase) {
                        EnvironmentViewModel.Phase.Loading -> LoadingScreen()

                        EnvironmentViewModel.Phase.Unconfigured -> EnvironmentScreen(
                            state = environment,
                            onConnectSelfHosted = environmentViewModel::connectSelfHosted,
                            onClearError = environmentViewModel::clearError,
                        )

                        EnvironmentViewModel.Phase.Configured -> AuthenticatedApp(
                            environment = environment,
                            onUseDefault = environmentViewModel::useDefault,
                            onConnectSelfHosted = environmentViewModel::connectSelfHosted,
                            onClearEnvError = environmentViewModel::clearError,
                        )
                    }
                }
                SnackbarHost(toastHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@OptIn(SupabaseExperimental::class)
@Composable
private fun AuthenticatedApp(
    environment: EnvironmentViewModel.UiState,
    onUseDefault: () -> Unit,
    onConnectSelfHosted: (String) -> Unit,
    onClearEnvError: () -> Unit,
) {
    // Only reached once SupabaseClientProvider.initialize() has completed (Phase.Configured),
    // so SupabaseClientProvider.client is safe to access here. Registers the Supabase Coil3
    // fetcher so AsyncImage can load StorageItem models (private-bucket photos) directly,
    // alongside the existing Ktor network fetcher for public URLs/local bytes.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(SupabaseClientProvider.client.coil3)
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }

    // Keying by the active instance rebuilds the auth stack (and its Supabase-bound
    // repositories) when the user switches to a different Hikyaku instance.
    val instanceKey = environment.source?.key ?: "default"
    val viewModel: AuthViewModel = viewModel(key = instanceKey) { AuthViewModel() }
    val authState by viewModel.authState.collectAsState()
    val isRecoveryPending by viewModel.isRecoveryPending.collectAsState()
    val screenState by viewModel.screenState.collectAsState()

    when (val state = authState) {
        is AuthState.Authenticated -> {
            if (isRecoveryPending) {
                // The user only holds a session because they verified a password-reset code;
                // they must set a new password before the main app is usable.
                NewPasswordScreen(state = screenState, onSubmit = viewModel::setNewPassword)
            } else {
                LaunchedEffect(state.userId) { viewModel.loadOrganisations() }
                MainNavGraph(user = state, viewModel = viewModel)
            }
        }
        AuthState.Unauthenticated -> AuthNavGraph(
            viewModel = viewModel,
            environment = environment,
            onConnectSelfHosted = onConnectSelfHosted,
            onClearEnvError = onClearEnvError,
        )
        AuthState.Loading -> LoadingScreen()
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
