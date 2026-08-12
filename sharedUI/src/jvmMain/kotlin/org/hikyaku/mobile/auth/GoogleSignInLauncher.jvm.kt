package org.hikyaku.mobile.auth

import androidx.compose.runtime.Composable

/** Desktop has no native Google sign-in integration wired up yet. */
@Composable
actual fun rememberGoogleIdTokenLauncher(): suspend () -> Result<GoogleIdToken> =
    { Result.failure(UnsupportedOperationException("Google sign-in isn't available on desktop yet.")) }
