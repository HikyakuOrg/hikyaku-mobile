package org.hikyaku.mobile.shift.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.util.formatHourMinute
import org.hikyaku.mobile.util.formatIsoAsDisplayDate
import org.hikyaku.mobile.util.isoDateTimeToHourMinute
import org.maplibre.spatialk.geojson.Point

/**
 * A row from the `vrp_optimization` table, which represents a single shift
 * (one route-optimisation run) owned by an organisation. The embedded
 * [solutions] carry the route/package summary for that run.
 */
@Serializable
data class Shift(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val provider: String,
    /** Dispatcher-set scheduled start time (ISO-8601), or null if unscheduled. */
    @SerialName("scheduled_start") val scheduledStart: String? = null,
    @SerialName("vrp_solution") val solutions: List<ShiftSolution> = emptyList(),
) {
    /** Number of routes in the (usually single) solution for this shift. */
    val routesCount: Int get() = solutions.firstOrNull()?.routesCount ?: 0

    /** Jobs that the optimiser could not assign to a route. */
    val unassignedCount: Int get() = solutions.firstOrNull()?.unassignedCount ?: 0

    /** Manually created shifts come from the dispatcher rather than the optimiser. */
    val isManual: Boolean get() = provider.equals("manual", ignoreCase = true)

    /** The date this shift falls on for calendar grouping: [scheduledStart] if set, else [createdAt]. */
    val calendarDate: LocalDate get() = LocalDate.parse((scheduledStart ?: createdAt).take(10))

    /** Wall-clock `HH:MM` this shift starts at: [scheduledStart] if set, else [createdAt]. */
    val displayTime: String
        get() = isoDateTimeToHourMinute(scheduledStart ?: createdAt)
            ?.let { (hour, minute) -> formatHourMinute(hour, minute) }
            ?: formatIsoAsDisplayDate(scheduledStart ?: createdAt)

    /** Number of delivery stops (`job` steps) across every route in the shift's solution. */
    val stopCount: Int
        get() = solutions.firstOrNull()?.routes.orEmpty().sumOf { route ->
            route.steps.count { it.type.equals("job", ignoreCase = true) }
        }

    /**
     * Each route's id and outbound stops (start → jobs, in travel order) as coordinates, used to
     * fetch and draw the home-screen route preview map. The return-to-depot step is dropped since
     * the preview only shows the outbound leg. A route with fewer than two located stops is
     * dropped since it can't be drawn as a line.
     */
    val routePreviewInputs: List<ShiftRoutePreviewInput>
        get() = solutions.firstOrNull()?.routes.orEmpty().mapNotNull { route ->
            val stops = route.steps
                .filterNot { it.type.equals("end", ignoreCase = true) }
                .sortedBy { it.stepIndex }
                .mapNotNull { it.location }
            if (stops.size >= 2) ShiftRoutePreviewInput(route.id, stops) else null
        }
}

/** A route's id paired with its outbound stop coordinates, in travel order. */
data class ShiftRoutePreviewInput(val routeId: String, val stops: List<Point>)

/** The `vrp_solution` summary embedded alongside a [Shift]. */
@Serializable
data class ShiftSolution(
    @SerialName("routes_count") val routesCount: Int? = null,
    @SerialName("unassigned_count") val unassignedCount: Int? = null,
    val duration: Int? = null,
    @SerialName("vrp_route") val routes: List<ShiftRoute> = emptyList(),
)

/** A `vrp_route` embedded alongside a [ShiftSolution], carrying just enough to draw its shape. */
@Serializable
data class ShiftRoute(
    val id: String = "",
    @SerialName("vrp_route_step") val steps: List<ShiftRouteStep> = emptyList(),
)

/** A `vrp_route_step` embedded alongside a [ShiftRoute]. */
@Serializable
data class ShiftRouteStep(
    @SerialName("step_index") val stepIndex: Int,
    val type: String,
    val location: Point? = null,
)
