package org.hikyaku.mobile.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Platform HTTP engine (OkHttp on Android, CIO on JVM, Darwin on iOS). */
internal expect fun httpClientEngine(): HttpClientEngine

/**
 * App-wide Ktor client used for plain HTTP calls (e.g. fetching the environment
 * config). Supabase manages its own client, so this is intentionally minimal.
 */
internal val appHttpClient: HttpClient by lazy {
    HttpClient(httpClientEngine()) {
        install(JwtRefresh)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            header(ApiHeaders.APP_VERSION, AppVersionProvider.version)
        }
        install(HttpTimeout) {
            // A cold connection (DNS + TLS handshake) on mobile data, or the geocoder's own
            // tail latency, routinely exceeds 5s. With the previous 5s socket/connect timeouts the
            // first autocomplete request after the app had been idle would silently time out — the
            // repository swallows the failure and the suggestion dropdown just stays empty, which is
            // the "sometimes nothing shows up" flakiness. Give the socket/connect enough headroom to
            // ride out a slow first request; requestTimeoutMillis still bounds the whole call.
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 20000
            requestTimeoutMillis = 55000
        }

    }
}
