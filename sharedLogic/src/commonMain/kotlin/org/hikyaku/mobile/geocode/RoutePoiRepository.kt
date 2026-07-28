package org.hikyaku.mobile.geocode

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.hikyaku.mobile.api.generated.models.GeoJsonFeatureCollectionDto
import org.hikyaku.mobile.api.generated.models.GeoJsonFeatureDto
import org.hikyaku.mobile.api.generated.models.GeoJsonFeaturePropertiesDto
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.geocode.model.RoutePoi
import org.hikyaku.mobile.geocode.model.RoutePoiKind
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import org.hikyaku.mobile.shift.detail.sampleAlong

/**
 * Finds points of interest along a route via the Hikyaku reverse-geocode POI lookup
 * (`GET /geocode/reverse?lat=&lon=&radius=&include=osm.<key>.<value>`), which proxies Photon and
 * returns a GeoJSON `FeatureCollection` (typed as [GeoJsonFeatureCollectionDto], generated from the
 * API's OpenAPI spec — see `openapi/`). Mirrors [GeocodeRepository]: the backend owns the geocoder
 * and the request is authenticated with the caller's Supabase access token
 * (`Authorization: Bearer <jwt>`).
 *
 * The endpoint is point-and-radius, so to cover a whole route the line is sampled along its length
 * ([sampleAlong]) and each sample is queried concurrently, then the POIs are de-duplicated
 * (overlapping samples surface the same POI more than once).
 */
class RoutePoiRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /**
     * All POIs of [kind] within [radiusKm] of [line] (`[lng, lat]` pairs). Samples the line every
     * [radiusKm] so the query disks overlap, sweeping the full route.
     */
    suspend fun fetchPoisAlong(
        line: List<List<Double>>,
        kind: RoutePoiKind,
        radiusKm: Double = DEFAULT_RADIUS_KM,
    ): Result<List<RoutePoi>> = runCatching {
        if (line.size < 2) return@runCatching emptyList()
        val samples = sampleAlong(line, spacingMeters = radiusKm * 1000, maxSamples = MAX_SAMPLES)
        val token = accessToken()
        val endpoint = ApiEndpoints.geocodeReverse(apiUrl())
        val pois = coroutineScope {
            samples.map { sample ->
                async { fetchAt(endpoint, token, lat = sample[1], lon = sample[0], radiusKm = radiusKm, kind = kind) }
            }.flatMap { it.await() }
        }
        pois.distinctBy { it.id }
    }

    private suspend fun fetchAt(
        endpoint: String,
        token: String,
        lat: Double,
        lon: Double,
        radiusKm: Double,
        kind: RoutePoiKind,
    ): List<RoutePoi> {
        val collection: GeoJsonFeatureCollectionDto = httpClient.get(endpoint) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(token))
            header("Accept", "application/json")
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("radius", radiusKm)
            parameter("include", kind.include)
        }.body()
        return collection.features.mapNotNull { it.toRoutePoi() }
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    private fun GeoJsonFeatureDto.toRoutePoi(): RoutePoi? {
        val coordinates = geometry.coordinates
        if (coordinates.size < 2) return null
        val lon = coordinates[0]
        val lat = coordinates[1]
        // osm_type + osm_id is the stable per-POI identifier; fall back to the coordinate.
        val id = properties.osmId?.let { "${properties.osmType.orEmpty()}${it.toLong()}" }
            ?: "$lon,$lat"
        return RoutePoi(
            id = id,
            name = properties.name,
            address = properties.toAddress(),
            lon = lon,
            lat = lat,
        )
    }

    /** A one-line address from the Photon address tags (`123 Main St, Suburb, State 3000`). */
    private fun GeoJsonFeaturePropertiesDto.toAddress(): String? {
        val streetLine = listOfNotNull(housenumber, street).joinToString(" ").ifBlank { null }
        val locality = district ?: city
        val region = listOfNotNull(state, postcode).joinToString(" ").ifBlank { null }
        return listOfNotNull(streetLine, locality, region).joinToString(", ").ifBlank { null }
    }

    private companion object {
        const val DEFAULT_RADIUS_KM = 2.0
        const val MAX_SAMPLES = 24
    }
}
