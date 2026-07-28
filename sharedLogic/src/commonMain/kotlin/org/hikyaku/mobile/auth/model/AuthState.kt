package org.hikyaku.mobile.auth.model

/** App-facing auth state, decoupled from the Supabase SDK types. */
sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val avatarUrl: String?,
    ) : AuthState
}

enum class SignUpOutcome { SignedIn, NeedsEmailConfirmation }
