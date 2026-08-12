package org.hikyaku.mobile.environment

import com.russhwolf.settings.Settings
import org.hikyaku.mobile.environment.model.EnvironmentConfig
import org.hikyaku.mobile.environment.model.EnvironmentSource
import org.hikyaku.mobile.environment.model.StoredEnvironment

/**
 * Persists the resolved [EnvironmentConfig] to disk via multiplatform-settings, so the
 * environment is only fetched from the network on first launch (or when the user
 * switches instances).
 */
class EnvironmentStore(private val settings: Settings = Settings()) {

    /** Returns the persisted environment, or null if the app has never been configured. */
    fun load(): StoredEnvironment? {
        val supabaseUrl = settings.getStringOrNull(KEY_SUPABASE_URL) ?: return null
        val supabaseAnonKey = settings.getStringOrNull(KEY_SUPABASE_ANON_KEY) ?: return null
        val hikyakuApiUrl = settings.getStringOrNull(KEY_HIKYAKU_API_URL) ?: return null
        val googleWebClientId = settings.getStringOrNull(KEY_GOOGLE_WEB_CLIENT_ID)

        val source = if (settings.getBoolean(KEY_SELF_HOSTED, false)) {
            val baseUrl = settings.getStringOrNull(KEY_SOURCE_BASE_URL) ?: return null
            EnvironmentSource.SelfHosted(baseUrl)
        } else {
            EnvironmentSource.Default
        }

        return StoredEnvironment(
            config = EnvironmentConfig(supabaseUrl, supabaseAnonKey, hikyakuApiUrl, googleWebClientId),
            source = source,
        )
    }

    fun save(config: EnvironmentConfig, source: EnvironmentSource) {
        settings.putString(KEY_SUPABASE_URL, config.supabaseUrl)
        settings.putString(KEY_SUPABASE_ANON_KEY, config.supabaseAnonKey)
        settings.putString(KEY_HIKYAKU_API_URL, config.hikyakuApiUrl)
        if (config.googleWebClientId != null) {
            settings.putString(KEY_GOOGLE_WEB_CLIENT_ID, config.googleWebClientId)
        } else {
            settings.remove(KEY_GOOGLE_WEB_CLIENT_ID)
        }
        settings.putBoolean(KEY_SELF_HOSTED, source is EnvironmentSource.SelfHosted)
        settings.putString(KEY_SOURCE_BASE_URL, source.baseUrl)
    }

    fun clear() {
        listOf(
            KEY_SUPABASE_URL,
            KEY_SUPABASE_ANON_KEY,
            KEY_HIKYAKU_API_URL,
            KEY_GOOGLE_WEB_CLIENT_ID,
            KEY_SELF_HOSTED,
            KEY_SOURCE_BASE_URL,
        ).forEach(settings::remove)
    }

    private companion object {
        const val KEY_SUPABASE_URL = "environment.supabaseUrl"
        const val KEY_SUPABASE_ANON_KEY = "environment.supabaseAnonKey"
        const val KEY_HIKYAKU_API_URL = "environment.hikyakuApiUrl"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "environment.googleWebClientId"
        const val KEY_SELF_HOSTED = "environment.selfHosted"
        const val KEY_SOURCE_BASE_URL = "environment.sourceBaseUrl"
    }
}
