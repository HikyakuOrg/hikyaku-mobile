package org.hikyaku.mobile.net

import io.github.jan.supabase.auth.auth
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hikyaku.mobile.auth.SupabaseClientProvider

/**
 * supabase-kt resolves an authenticated request's access token, notices it's expired and kicks
 * off a refresh as a side effect — but the token it already read (the stale one) is still what
 * goes out on *that* request (see `Auth.resolveAccessToken`/`checkAccessToken` upstream). The
 * *next* request is the first to see the refreshed token. Under normal foreground use the
 * background auto-refresh (fired at 80% of session lifetime) wins that race, but right after cold
 * start — session loaded from storage already past due — it doesn't, and the caller gets a raw
 * "JWT expired" from PostgREST/whendan-api.
 *
 * Installed on every authenticated [io.ktor.client.HttpClient] (the Supabase-managed one via
 * [SupabaseClientProvider.initialize], and [appHttpClient] for the geocode/optimisation
 * endpoints), this retries a 401 exactly once: refresh the session, resend with the new token —
 * so the race never surfaces to the user. Concurrent 401s (e.g.
 * [org.hikyaku.mobile.geocode.RoutePoiRepository]'s parallel POI lookups) share a single refresh
 * via [JwtRefreshCoordinator] instead of each triggering their own.
 */
val JwtRefresh: ClientPlugin<Unit> = createClientPlugin("JwtRefresh") {
    on(Send) { request ->
        val call = proceed(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@on call

        val staleToken = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        val newToken = JwtRefreshCoordinator.refresh(staleToken) ?: return@on call

        val retryRequest = HttpRequestBuilder().takeFrom(request)
        retryRequest.headers.remove(HttpHeaders.Authorization)
        retryRequest.header(HttpHeaders.Authorization, "Bearer $newToken")
        proceed(retryRequest)
    }
}

/** Serializes session refreshes so N concurrent 401s trigger one refresh, not N. */
private object JwtRefreshCoordinator {
    private val mutex = Mutex()

    suspend fun refresh(staleToken: String?): String? = mutex.withLock {
        // Unauthenticated calls (e.g. EnvironmentRepository's pre-login /api/environment fetch)
        // can 401 before the Supabase client exists at all — nothing to refresh, so bail out
        // rather than let SupabaseClientProvider.client's error() propagate as a plugin failure.
        if (!SupabaseClientProvider.isInitialized) return@withLock null
        val auth = SupabaseClientProvider.client.auth
        val current = auth.currentAccessTokenOrNull()
        // Another caller already refreshed while we were waiting for the lock.
        if (current != null && current != staleToken) return@withLock current
        runCatching {
            auth.refreshCurrentSession()
            auth.currentAccessTokenOrNull()
        }.getOrNull()
    }
}
