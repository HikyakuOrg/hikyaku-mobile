package org.hikyaku.mobile.routing

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.hikyaku.mobile.api.generated.models.RoutePreviewDto
import org.hikyaku.mobile.api.generated.models.RouteRequestDto
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import org.hikyaku.mobile.routing.model.RouteLeg
import org.hikyaku.mobile.routing.model.RoutePreview
import org.hikyaku.mobile.routing.model.RouteSummaryStats

/**
 * Fetches road-snapped route geometry from the whendan-api routing endpoint. The backend
 * owns the routing engine and returns a normalised route ([RoutePreviewDto], generated from the
 * API's OpenAPI spec — see `openapi/`), mapped here to the domain [RoutePreview]. The client only
 * sends the ordered stop coordinates. The organisation is identified by the `x-org-slug` header;
 * the endpoint is public (no JWT).
 */
class RoutingRepository(
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /**
     * Routes through [coordinates] (`[longitude, latitude]` pairs, in visit order) for the
     * vehicle [profile] (e.g. `driving-car`), scoped to [orgSlug].
     */
    suspend fun fetchRoutePreview(
        profile: String,
        orgSlug: String,
        coordinates: List<List<Double>>,
    ): Result<RoutePreview> = runCatching {
        val endpoint = ApiEndpoints.routingRoute(apiUrl())
        val dto: RoutePreviewDto = httpClient.post(endpoint) {
            header(ApiHeaders.ORG_SLUG, orgSlug)
            contentType(ContentType.Application.Json)
            setBody(RouteRequestDto(profile = profile, coordinates = coordinates))
        }.body()
        dto.toRoutePreview()
    }

    private fun RoutePreviewDto.toRoutePreview(): RoutePreview = RoutePreview(
        coordinates = coordinates,
        wayPoints = wayPoints.map { it.toInt() },
        legs = legs.map { RouteLeg(duration = it.duration, distance = it.distance) },
        summary = RouteSummaryStats(duration = summary.duration, distance = summary.distance),
    )
}
