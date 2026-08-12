package org.hikyaku.mobile.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.coil.Coil3Integration
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import org.hikyaku.mobile.environment.model.EnvironmentConfig
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.AppVersionProvider
import org.hikyaku.mobile.net.JwtRefresh
import kotlin.concurrent.Volatile

/**
 * App-wide [SupabaseClient]. Unlike a compile-time configuration, the client is built at
 * runtime from an [EnvironmentConfig] resolved from the Hikyaku environment endpoint (or
 * a self-hosted instance), so it must be [initialize]d before [client] is accessed.
 *
 * Auth is installed with its defaults, which persist the session via multiplatform-settings
 * and auto-refresh the token. Postgrest is installed so repositories can query tables.
 */
object SupabaseClientProvider {
    @Volatile
    private var current: SupabaseClient? = null

    val isInitialized: Boolean get() = current != null

    val client: SupabaseClient
        get() = current ?: error(
            "Supabase client not initialized. The environment must be configured " +
                "(SupabaseClientProvider.initialize) before the client is used.",
        )

    /**
     * (Re)builds the client from [config], closing any previously created client. Calling
     * this with a new config switches the app to a different Supabase project (e.g. when
     * the user selects a self-hosted instance). Suspends because closing the previous
     * client suspends.
     */
    @OptIn(SupabaseInternal::class)
    suspend fun initialize(config: EnvironmentConfig) {
        val previous = current
        current = createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.supabaseAnonKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Coil3Integration)
            // Installs JwtRefresh on the shared HttpClient so a 401 caused by the client's
            // known stale-token-on-refresh race (see JwtRefreshPlugin.kt) is retried silently
            // instead of surfacing "JWT expired" to the user.
            httpConfig {
                install(JwtRefresh)
                defaultRequest {
                    header(ApiHeaders.APP_VERSION, AppVersionProvider.version)
                }
            }
        }
        ApiConfigProvider.set(config.hikyakuApiUrl)
        GoogleAuthConfig.set(config.googleWebClientId)
        previous?.close()
    }
}
