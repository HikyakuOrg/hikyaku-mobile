package org.hikyaku.mobile.auth

import kotlin.concurrent.Volatile

/**
 * Holds the Google OAuth Web Client ID used to request a Google ID token via Credential
 * Manager (see `rememberGoogleIdTokenLauncher` in sharedUI). Set from
 * [org.hikyaku.mobile.environment.model.EnvironmentConfig.googleWebClientId] whenever
 * [SupabaseClientProvider.initialize] runs, same as the rest of the app's runtime config.
 * Null on instances that haven't configured Google sign-in (e.g. most self-hosted ones) -
 * callers should check [isConfigured] before offering Google sign-in at all.
 */
object GoogleAuthConfig {
    @Volatile
    private var webClientId: String? = null

    fun set(webClientId: String?) {
        this.webClientId = webClientId
    }

    val isConfigured: Boolean get() = !webClientId.isNullOrBlank()

    /** The configured Web Client ID, or throws if it isn't set for the active environment. */
    fun requireWebClientId(): String = webClientId.takeIf { !it.isNullOrBlank() }
        ?: error("Google sign-in isn't configured for this environment.")
}
