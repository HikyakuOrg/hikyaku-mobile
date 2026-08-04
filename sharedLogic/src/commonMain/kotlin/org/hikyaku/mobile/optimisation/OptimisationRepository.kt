package org.hikyaku.mobile.optimisation

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.hikyaku.mobile.api.generated.models.LatestOptimisationRunDto
import org.hikyaku.mobile.api.generated.models.RunOptimisationDto
import org.hikyaku.mobile.api.generated.models.RunOptimisationResultDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient

/**
 * Backs the one-click "optimise" action: queues a warehouse-wide run via
 * `POST /api/v1/optimisation/run` (which assigns every unassigned package at that warehouse to a
 * route), then [fetchLatestRun] polls `GET /api/v1/optimisation/run/latest` for its outcome. Both
 * endpoints are authenticated with the caller's Supabase access token and scoped with the
 * `X-Organisation-Slug` header, matching [org.hikyaku.mobile.shift.create.CreateShiftRepository].
 */
class OptimisationRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /** Queues a run for [warehouseId], returning the new run's id. */
    suspend fun runOptimisation(orgSlug: String, warehouseId: String): Result<String> = runCatching {
        val response = httpClient.post(ApiEndpoints.optimisationRun(apiUrl())) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
            header(ApiHeaders.ORGANISATION_SLUG, orgSlug)
            contentType(ContentType.Application.Json)
            setBody(RunOptimisationDto(warehouseId = warehouseId))
        }
        // Read as text once (the response body can only be consumed once) so both the failure
        // message and the typed decode below work off the same captured string.
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val body = bodyText.take(300)
            if (response.status.value == 429) throw OptimisationRateLimitedException(body)
            error("Optimisation failed (${response.status.value}): $body")
        }
        json.decodeFromString<RunOptimisationResultDto>(bodyText).runId
    }

    /** The organisation's most recent run, or null if it has never run one. */
    suspend fun fetchLatestRun(orgSlug: String): Result<LatestOptimisationRunDto?> = runCatching {
        val response = httpClient.get(ApiEndpoints.optimisationRunLatest(apiUrl())) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
            header(ApiHeaders.ORGANISATION_SLUG, orgSlug)
        }
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Couldn't check optimisation status (${response.status.value}): ${bodyText.take(300)}")
        }
        if (bodyText.isBlank() || bodyText == "null") null else json.decodeFromString<LatestOptimisationRunDto>(bodyText)
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    private companion object {
        // Mirrors the ContentNegotiation config on appHttpClient (HttpClientFactory.kt) — used here
        // to decode a response body already consumed as text for the failure messages above.
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Thrown when `/api/v1/optimisation/run` rejects a request because a prior run is still on cooldown. */
class OptimisationRateLimitedException(message: String) : Exception(message)
