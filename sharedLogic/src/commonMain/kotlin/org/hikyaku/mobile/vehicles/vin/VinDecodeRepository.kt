package org.hikyaku.mobile.vehicles.vin

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import org.hikyaku.mobile.api.generated.models.VinDecodeResultDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import org.hikyaku.mobile.vehicles.vin.model.VinDecodeResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decodes a VIN via the Hikyaku VIN endpoint (`GET /api/v1/vin/{vin}`), which runs fully offline
 * server-side against a bundled NHTSA snapshot — no rate limit or per-call cost. The request is
 * authenticated with the caller's Supabase access token; unlike the geocode/routing endpoints this
 * one isn't organisation data, so no `X-Organisation-Slug` is sent.
 *
 * The endpoint always answers 200, even for a garbage VIN — a bad or unrecognised one just comes
 * back with less (or none) of [VinDecodeResult] filled in, which the add-vehicle form treats as
 * "fall back to manual entry" rather than an error.
 */
class VinDecodeRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    suspend fun decode(vin: String): Result<VinDecodeResult> = runCatching {
        val endpoint = ApiEndpoints.vinDecode(apiUrl(), vin)
        val token = accessToken()
        val dto: VinDecodeResultDto = httpClient.get(endpoint) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(token))
            header("Accept", "application/json")
        }.body()
        dto.toDecodeResult()
    }.onFailure {
        if (it is CancellationException) throw it
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    /**
     * Prefers the vehicle-level match ([VinDecodeResultDto.components]`.vehicle`); when the WMI
     * (manufacturer) resolved but no model pattern did, at least the make comes through.
     */
    private fun VinDecodeResultDto.toDecodeResult(): VinDecodeResult {
        val vehicle = components.vehicle
        return VinDecodeResult(
            make = vehicle?.make ?: components.wmi?.make,
            model = vehicle?.model,
            year = vehicle?.year?.toInt(),
        )
    }
}
