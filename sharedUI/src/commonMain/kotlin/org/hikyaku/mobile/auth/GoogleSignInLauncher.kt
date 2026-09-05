package org.hikyaku.mobile.auth

import androidx.compose.runtime.Composable

/** A Google ID token paired with the raw nonce used to request it, ready for [AuthRepository.signInWithGoogle]. */
data class GoogleIdToken(val idToken: String, val rawNonce: String)

/**
 * Returns a suspend function that launches the platform's native Google sign-in UI and
 * resolves with the resulting [GoogleIdToken]. No-op failure off Android.
 */
@Composable
expect fun rememberGoogleIdTokenLauncher(): suspend () -> Result<GoogleIdToken>
