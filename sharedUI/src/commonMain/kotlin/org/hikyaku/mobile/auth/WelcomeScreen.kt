package org.hikyaku.mobile.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.app_name
import hikyaku.sharedui.generated.resources.auth_continue_with_google
import hikyaku.sharedui.generated.resources.auth_sign_in
import hikyaku.sharedui.generated.resources.auth_sign_up
import hikyaku.sharedui.generated.resources.google_signin_dark
import hikyaku.sharedui.generated.resources.google_signin_light
import hikyaku.sharedui.generated.resources.welcome_self_hosted
import kotlinx.coroutines.launch
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeScreen(
    state: AuthScreenState,
    onSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onGoogleSignIn: (Result<GoogleIdToken>) -> Unit,
    onSelfHostedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requestGoogleIdToken = rememberGoogleIdTokenLauncher()
    val scope = rememberCoroutineScope()
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
        // The active environment may not have Google sign-in set up (e.g. most self-hosted
        // instances) - GoogleAuthConfig is only populated when the environment endpoint
        // returns a GOOGLE_WEB_CLIENT_ID, so there's nothing to show without it.
        if (GoogleAuthConfig.isConfigured) {
            Spacer(Modifier.height(12.dp))
            // Google's pre-approved button asset (see developers.google.com/identity/branding-guidelines) -
            // used unmodified rather than hand-built, since the guidelines forbid recreating the "G" mark
            // or button chrome. Its baked-in aspect ratio (180x40) is preserved to avoid distorting it.
            Image(
                painter = painterResource(if (isSystemInDarkTheme()) Res.drawable.google_signin_dark else Res.drawable.google_signin_light),
                contentDescription = stringResource(Res.string.auth_continue_with_google),
                modifier = Modifier
                    .width(180.dp)
                    .height(40.dp)
                    .clickable { scope.launch { onGoogleSignIn(requestGoogleIdToken()) } },
            )
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSelfHostedClick) { Text(stringResource(Res.string.welcome_self_hosted)) }
        MessageArea(state)
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    HikyakuTheme {
        WelcomeScreen(
            state = AuthScreenState(),
            onSignInClick = {},
            onSignUpClick = {},
            onGoogleSignIn = {},
            onSelfHostedClick = {},
        )
    }
}
