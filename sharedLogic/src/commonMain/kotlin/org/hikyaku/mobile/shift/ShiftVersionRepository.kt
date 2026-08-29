package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.hikyaku.mobile.api.generated.models.ShiftVersionDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient

/**
 * Reads `GET /api/v1/shifts/{id}/version` — the shift's revision, stop count and status, and
 * nothing else.
 *
 * This exists because the driver app has no realtime and no push: a package assigned to a planned
 * shift after the driver opened it is otherwise invisible until they pull to refresh. The endpoint
 * is deliberately tiny so [ShiftVersionPoll] can ask for it every 30 seconds while the shift screen
 * is on top, and the app only refetches the (expensive) route when the revision actually moved.
 *
 * Authenticated with the caller's Supabase access token and scoped with `X-Organisation-Slug`,
 * matching [org.hikyaku.mobile.optimisation.OptimisationRepository].
 */
class ShiftVersionRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /** The current version of [shiftId]. */
    suspend fun fetchVersion(orgSlug: String, shiftId: String): Result<ShiftVersionDto> = runCatching {
        val response = httpClient.get(ApiEndpoints.shiftVersion(apiUrl(), shiftId)) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
            header(ApiHeaders.ORGANISATION_SLUG, orgSlug)
        }
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Couldn't check the shift version (${response.status.value}): ${bodyText.take(300)}")
        }
        json.decodeFromString<ShiftVersionDto>(bodyText)
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    private companion object {
        // Mirrors the ContentNegotiation config on appHttpClient (HttpClientFactory.kt) — used here
        // to decode a body already consumed as text for the failure message above.
        val json = Json { ignoreUnknownKeys = true }
    }
}
