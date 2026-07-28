package org.hikyaku.mobile.shift.detail.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single route within a shift (`vrp_route`). Shown in the route selector; the steps,
 * packages and map line for a route are loaded on demand when it is selected.
 */
@Serializable
data class VrpRoute(
    val id: String,
    val duration: Int? = null,
    val cost: Int? = null,
)

/** Wrapper used to read `vrp_route` rows nested under their `vrp_solution`. */
@Serializable
data class ShiftSolutionRoutes(
    @SerialName("vrp_route") val routes: List<VrpRoute> = emptyList(),
)
