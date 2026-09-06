package org.hikyaku.mobile.geocode.model

import kotlinx.serialization.Serializable

/** A geocoded address suggestion from the Hikyaku (Pelias) autocomplete service. */
@Serializable
data class AddressSuggestion(
    val label: String,
    val street: String?,
    val suburb: String?,
    val state: String?,
    val country: String?,
    val postcode: String?,
    val lat: Double,
    val lon: Double,
    val gid: String?,
    val confidence: Double?,
    /** Photon's OSM key for the matched feature, e.g. `building`, `place`. Null for the synthetic dropped-pin result. */
    val osmKey: String? = null,
    /** Photon's OSM value for the matched feature, e.g. `apartments`, `yes`. */
    val osmValue: String? = null,
    /** Bounding box `[minLon, minLat, maxLon, maxLat]` when Photon's match is a footprint rather than a point. */
    val extent: List<Double>? = null,
)

/**
 * Whether [suggestion] most likely points at a building (an apartment block, office tower, etc.)
 * rather than a standalone house, so the caller should prompt for a unit/suite/business name.
 * Mirrors the dashboard's `isLikelyBuilding` (HIK-44) so both clients agree on the same Photon
 * result. Two independent signals, either is enough: Photon tagged the feature `building=*`
 * directly, or the match came back with an [AddressSuggestion.extent] bounding box alongside a
 * resolved street line — a plain point-level house-number match never carries an extent, so its
 * presence on a real address means the match is a footprint, not a point.
 */
fun isLikelyBuilding(suggestion: AddressSuggestion): Boolean {
    if (suggestion.osmKey == "building") return true
    return suggestion.extent != null && suggestion.street != null
}
