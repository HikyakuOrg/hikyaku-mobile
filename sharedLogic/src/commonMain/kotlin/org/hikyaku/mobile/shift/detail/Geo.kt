package org.hikyaku.mobile.shift.detail

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Great-circle distance in metres between two `(lat, lng)` points (haversine). Used to decide
 * whether the driver is "back at the warehouse" — close enough to the depot step to finish a shift.
 */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = (lat2 - lat1).toRadians()
    val dLng = (lng2 - lng1).toRadians()
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2) * sin(dLng / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0

/**
 * Samples points along a `[lng, lat]` polyline, walking it by arc length: it emits the first
 * vertex, a point roughly every [spacingMeters] thereafter, and always the last vertex. On long
 * lines the spacing is widened so no more than [maxSamples] points are produced (bounding how many
 * point-radius lookups a sweep of the whole route fans out to). Returns the line as-is when it has
 * fewer than two vertices.
 */
fun sampleAlong(
    line: List<List<Double>>,
    spacingMeters: Double,
    maxSamples: Int,
): List<List<Double>> {
    if (line.size < 2) return line
    var total = 0.0
    for (i in 1 until line.size) {
        total += haversineMeters(line[i - 1][1], line[i - 1][0], line[i][1], line[i][0])
    }
    if (total == 0.0) return listOf(line.first())

    val spacing = maxOf(spacingMeters, total / maxSamples)
    val result = mutableListOf(line.first())
    var nextAt = spacing
    var travelled = 0.0
    for (i in 1 until line.size) {
        val a = line[i - 1]
        val b = line[i]
        val seg = haversineMeters(a[1], a[0], b[1], b[0])
        if (seg == 0.0) continue
        while (travelled + seg >= nextAt) {
            val t = (nextAt - travelled) / seg
            result.add(listOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            nextAt += spacing
        }
        travelled += seg
    }
    if (result.last() != line.last()) result.add(line.last())
    return result
}
