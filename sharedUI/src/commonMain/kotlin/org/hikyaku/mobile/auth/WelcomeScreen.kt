package org.hikyaku.mobile.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.app_name
import hikyaku.sharedui.generated.resources.auth_sign_in
import hikyaku.sharedui.generated.resources.auth_sign_up
import hikyaku.sharedui.generated.resources.welcome_self_hosted
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeScreen(
    onSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onSelfHostedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onSignInClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.auth_sign_in)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSignUpClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.auth_sign_up)) }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSelfHostedClick) { Text(stringResource(Res.string.welcome_self_hosted)) }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    HikyakuTheme {
        WelcomeScreen(
            onSignInClick = {},
            onSignUpClick = {},
            onSelfHostedClick = {},
        )
    }
}
