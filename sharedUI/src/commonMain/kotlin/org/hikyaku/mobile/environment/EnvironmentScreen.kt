package org.hikyaku.mobile.environment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.environment_choose_instance
import hikyaku.sharedui.generated.resources.environment_connect
import hikyaku.sharedui.generated.resources.environment_connected_to
import hikyaku.sharedui.generated.resources.environment_label_instance_url
import hikyaku.sharedui.generated.resources.environment_placeholder_url
import org.hikyaku.mobile.environment.model.EnvironmentSource
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.LocalToastHostState
import org.hikyaku.mobile.toast.ToastEffect
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the user choose between the hosted Hikyaku instance and a self-hosted one.
 * Shown full-screen on first launch when no environment is configured, and reachable
 * from the welcome screen to switch instances ([onBack] non-null).
 */
@Composable
fun EnvironmentScreen(
    state: EnvironmentViewModel.UiState,
    onConnectSelfHosted: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val selfHosted = state.source as? EnvironmentSource.SelfHosted
    var instanceUrl by remember { mutableStateOf(selfHosted?.url ?: "") }
    ToastEffect(state.errorMessage)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(Res.string.environment_choose_instance),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (onBack != null && state.source != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.environment_connected_to, state.source.baseUrl),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = instanceUrl,
            onValueChange = { instanceUrl = it; if (state.errorMessage != null) onClearError() },
            label = { Text(stringResource(Res.string.environment_label_instance_url)) },
            placeholder = { Text(stringResource(Res.string.environment_placeholder_url)) },
            singleLine = true,
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onConnectSelfHosted(instanceUrl) },
            enabled = !state.isBusy && instanceUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.environment_connect)) }
        Spacer(Modifier.height(16.dp))

        if (state.isBusy) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }

        if (onBack != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack, enabled = !state.isBusy) { Text(stringResource(Res.string.action_back)) }
        }
    }
}

/**
 * [ToastEffect] reads [org.hikyaku.mobile.toast.LocalToastHostState], which is normally provided
 * by [org.hikyaku.mobile.App]. Previews need to supply their own so the composition local lookup
 * doesn't crash at design time.
 */
@Composable
private fun EnvironmentScreenPreviewHost(content: @Composable () -> Unit) {
    val toastHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalToastHostState provides toastHostState) {
        content()
    }
}

@Preview
@Composable
private fun EnvironmentScreenConnectedPreview() {
    HikyakuTheme {
        EnvironmentScreenPreviewHost {
            EnvironmentScreen(
                state = EnvironmentViewModel.UiState(
                    phase = EnvironmentViewModel.Phase.Configured,
                    source = EnvironmentSource.SelfHosted("https://acme.hikyaku.org"),
                    isBusy = false,
                    errorMessage = null,
                ),
                onConnectSelfHosted = {},
                onClearError = {},
                onBack = {},
            )
        }
    }
}

@Preview
@Composable
private fun EnvironmentScreenErrorPreview() {
    HikyakuTheme {
        EnvironmentScreenPreviewHost {
            EnvironmentScreen(
                state = EnvironmentViewModel.UiState(
                    phase = EnvironmentViewModel.Phase.Unconfigured,
                    source = null,
                    isBusy = false,
                    errorMessage = "Couldn't reach that instance. Check the URL and try again.",
                ),
                onConnectSelfHosted = {},
                onClearError = {},
                onBack = null,
            )
        }
    }
}
