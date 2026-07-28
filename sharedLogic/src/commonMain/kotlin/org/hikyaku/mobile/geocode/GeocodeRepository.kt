package org.hikyaku.mobile.geocode

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import org.hikyaku.mobile.api.generated.models.GeoJsonFeatureCollectionDto
import org.hikyaku.mobile.api.generated.models.GeoJsonFeatureDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import kotlin.coroutines.cancellation.CancellationException

/**
 * Address autocomplete via the Hikyaku geocode endpoint (`GET /geocode/autocomplete?text=`),
 * which returns a GeoJSON `FeatureCollection` (typed as [GeoJsonFeatureCollectionDto], generated
 * from the API's OpenAPI spec — see `openapi/`). The backend owns the geocoder and the request is
 * authenticated with the caller's Supabase access token (`Authorization: Bearer <jwt>`).
 * Coordinates come back as `[lon, lat]`.
 */
class GeocodeRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    suspend fun autocomplete(query: String): Result<List<AddressSuggestion>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val endpoint = ApiEndpoints.geocodeAutocomplete(apiUrl())
        val token = accessToken()
        val collection: GeoJsonFeatureCollectionDto = httpClient.get(endpoint) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(token))
            header("Accept", "application/json")
            parameter("text", query.trimEnd())
        }.body()
        collection.features.mapNotNull { it.toSuggestion() }
    }.onFailure {
        // A superseded request (the user typed another character) cancels this coroutine; let that
        // cancellation propagate instead of reporting it as a real failure, otherwise runCatching
        // turns it into a Result.failure and breaks structured concurrency.
        if (it is CancellationException) throw it
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    private fun GeoJsonFeatureDto.toSuggestion(): AddressSuggestion? {
        val coordinates = geometry.coordinates
        if (coordinates.size < 2) return null
        val lon = coordinates[0]
        val lat = coordinates[1]
        val p = properties

        // Photon has no pre-formatted label, so build one from the address parts.
        val addressLine = listOfNotNull(p.housenumber, p.street).joinToString(" ").ifBlank { null }
        val streetLine = p.name ?: addressLine
        val suburb = p.district ?: p.city
        val regionLine = listOfNotNull(p.state, p.postcode).joinToString(" ").ifBlank { null }
        val label = listOfNotNull(streetLine, suburb, regionLine, p.country)
            .joinToString(", ")
            .ifBlank { return null }

        return AddressSuggestion(
            label = label,
            street = streetLine,
            suburb = suburb,
            state = p.state,
            country = p.country,
            postcode = p.postcode,
            lon = lon,
            lat = lat,
            // Photon has no gid; osm_type + osm_id is the stable per-result identifier.
            gid = p.osmId?.let { "${p.osmType.orEmpty()}${it.toLong()}" },
            confidence = null,
        )
    }
}
