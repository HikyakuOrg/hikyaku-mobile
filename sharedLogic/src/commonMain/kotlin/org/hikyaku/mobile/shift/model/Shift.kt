package org.hikyaku.mobile.shift.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.util.formatHourMinute
import org.hikyaku.mobile.util.formatIsoAsDisplayDate
import org.hikyaku.mobile.util.isoDateTimeToHourMinute

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
}

/** The `vrp_solution` summary embedded alongside a [Shift]. */
@Serializable
data class ShiftSolution(
    @SerialName("routes_count") val routesCount: Int? = null,
    @SerialName("unassigned_count") val unassignedCount: Int? = null,
    val duration: Int? = null,
)
