package org.hikyaku.mobile.environment

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.hikyaku.mobile.environment.model.EnvironmentConfig
import org.hikyaku.mobile.environment.model.EnvironmentSource
import org.hikyaku.mobile.environment.model.StoredEnvironment
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.appHttpClient

/**
 * Resolves the app's [EnvironmentConfig], either from disk (subsequent launches) or by
 * querying the `/api/environment` endpoint of a Hikyaku instance (first launch, or when
 * the user switches to a self-hosted instance). Successful results are persisted.
 */
class EnvironmentRepository(
    private val store: EnvironmentStore = EnvironmentStore(),
    private val httpClient: HttpClient = appHttpClient,
) {
    /** The environment persisted on a previous launch, if any. */
    fun loadPersisted(): StoredEnvironment? = store.load()

    /**
     * Fetches the environment from [source]'s `/api/environment` endpoint and persists it.
     */
    suspend fun configure(source: EnvironmentSource): Result<EnvironmentConfig> = runCatching {
        val endpoint = ApiEndpoints.environment(source.baseUrl)
        val config = httpClient.get(endpoint).body<EnvironmentConfig>()
        store.save(config, source)
        config
    }
}
