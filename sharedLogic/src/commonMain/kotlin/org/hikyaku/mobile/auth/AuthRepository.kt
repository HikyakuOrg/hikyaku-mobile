package org.hikyaku.mobile.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.storage.storage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.auth.model.SignUpOutcome
import org.hikyaku.mobile.supabase.SupabaseBuckets

class AuthRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    private val auth get() = client.auth

    /** Current auth state, updating as the session changes. */
    val authState: Flow<AuthState> = auth.sessionStatus.map { it.toAuthState() }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): Result<SignUpOutcome> = runCatching {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject { put("display_name", displayName) }
        }
        if (auth.sessionStatus.value is SessionStatus.Authenticated) {
            SignUpOutcome.SignedIn
        } else {
            SignUpOutcome.NeedsEmailConfirmation
        }
    }

    /** Verifies the 6-digit signup code sent to [email], completing the sign-up. */
    suspend fun verifySignUpOtp(email: String, token: String): Result<Unit> = runCatching {
        auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email = email, token = token)
        Unit
    }

    /** Requests a new signup verification code be sent to [email]. */
    suspend fun resendSignUpOtp(email: String): Result<Unit> = runCatching {
        auth.resendEmail(type = OtpType.Email.SIGNUP, email = email)
    }

    /** Updates the signed-in user's display name. Email is intentionally left unchanged. */
    suspend fun updateDisplayName(displayName: String): Result<Unit> = runCatching {
        auth.updateUser {
            data { put("display_name", displayName) }
        }
    }

    /**
     * Uploads [bytes] as the signed-in user's profile picture to the public `avatar` bucket at
     * `{userId}.jpg` (upserted, so a re-upload replaces the old photo at the same path) and
     * points `avatar_url` at its public URL. A timestamp query param is appended to the stored
     * URL so the new photo isn't served from an image cache still keyed by the unchanged path.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun uploadAvatar(bytes: ByteArray): Result<Unit> = runCatching {
        val userId = auth.currentUserOrNull()?.id ?: error("No authenticated user.")
        val path = "$userId.jpg"
        client.storage.from(SupabaseBuckets.AVATAR).upload(path, bytes) { upsert = true }
        val url = client.storage.from(SupabaseBuckets.AVATAR).publicUrl(path)
        auth.updateUser {
            data { put("avatar_url", "$url?t=${Clock.System.now().toEpochMilliseconds()}") }
        }
        Unit
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.resetPasswordForEmail(email)
    }

    /** Verifies the 6-digit password-reset code sent to [email], starting a session for [updatePassword]. */
    suspend fun verifyPasswordResetOtp(email: String, token: String): Result<Unit> = runCatching {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = token)
        Unit
    }

    /** Sets the signed-in user's password, completing the password-reset flow. */
    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        auth.updateUser { password = newPassword }
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        auth.signOut()
    }
}

private fun SessionStatus.toAuthState(): AuthState = when (this) {
    is SessionStatus.Authenticated -> {
        val user = session.user
        AuthState.Authenticated(
            userId = user?.id ?: "",
            email = user?.email,
            displayName = user?.userMetadata
                ?.get("display_name")?.jsonPrimitive?.contentOrNull,
            avatarUrl = user?.userMetadata
                ?.get("avatar_url")?.jsonPrimitive?.contentOrNull,
        )
    }
    SessionStatus.Initializing -> AuthState.Loading
    is SessionStatus.NotAuthenticated -> AuthState.Unauthenticated
    is SessionStatus.RefreshFailure -> AuthState.Unauthenticated
}
