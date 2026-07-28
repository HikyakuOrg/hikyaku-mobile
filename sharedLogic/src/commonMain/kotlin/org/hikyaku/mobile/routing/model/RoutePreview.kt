package org.hikyaku.mobile.routing.model

import kotlinx.serialization.Serializable

/**
 * Road-snapped route geometry returned by `POST /api/v1/routing/route`. [coordinates] is
 * the full path as `[longitude, latitude]` pairs; [wayPoints] indexes into it marking each
 * visited stop.
 */
@Serializable
data class RoutePreview(
    val coordinates: List<List<Double>> = emptyList(),
    val wayPoints: List<Int> = emptyList(),
    val legs: List<RouteLeg> = emptyList(),
    val summary: RouteSummaryStats? = null,
)

@Serializable
data class RouteLeg(
    val duration: Double? = null,
    val distance: Double? = null,
)

@Serializable
data class RouteSummaryStats(
    val duration: Double? = null,
    val distance: Double? = null,
)
