package org.hikyaku.mobile.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

@Composable
actual fun rememberGoogleIdTokenLauncher(): suspend () -> Result<GoogleIdToken> {
    val context = LocalContext.current
    return {
        runCatching {
            val rawNonce = UUID.randomUUID().toString()
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(GoogleAuthConfig.requireWebClientId())
                .setNonce(sha256Hex(rawNonce))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = CredentialManager.create(context).getCredential(request = request, context = context)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            GoogleIdToken(idToken = credential.idToken, rawNonce = rawNonce)
        }
    }
}

/** Google's `GetGoogleIdOption` nonce must be the SHA-256 hash of the raw nonce sent to Supabase. */
private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
