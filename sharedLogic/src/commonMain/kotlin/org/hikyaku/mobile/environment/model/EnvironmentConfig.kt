package org.hikyaku.mobile.environment.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Runtime configuration for the app, returned by `GET {baseUrl}/api/environment`.
 * The JSON keys are upper-snake-case to match the endpoint contract.
 */
@Serializable
data class EnvironmentConfig(
    @SerialName("SUPABASE_URL") val supabaseUrl: String,
    @SerialName("SUPABASE_ANON_KEY") val supabaseAnonKey: String,
    @SerialName("HIKYAKU_API_URL") val hikyakuApiUrl: String,
)

/** Where the [EnvironmentConfig] is fetched from. */
sealed class EnvironmentSource(val baseUrl: String) {
    /** A stable identifier, used to key per-instance UI/state. */
    abstract val key: String

    /** The canonical, hosted Hikyaku instance. */
    data object Default : EnvironmentSource(DEFAULT_BASE_URL) {
        override val key: String = "default"
    }

    /** A user-supplied, self-hosted Hikyaku instance. */
    data class SelfHosted(val url: String) : EnvironmentSource(url) {
        override val key: String = "self:$url"
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://hikyaku.org"
    }
}

/** A previously resolved environment, restored from disk. */
data class StoredEnvironment(
    val config: EnvironmentConfig,
    val source: EnvironmentSource,
)
