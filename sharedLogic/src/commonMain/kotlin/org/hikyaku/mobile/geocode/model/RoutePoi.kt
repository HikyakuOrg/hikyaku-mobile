package org.hikyaku.mobile.geocode.model

/**
 * A point of interest found near a route via the reverse-geocode POI lookup. [id] is a stable
 * per-POI key (OSM type + id) used to de-duplicate POIs that fall inside more than one of the
 * overlapping samples swept along the route. [name] is the brand/name when tagged. [lon]/[lat]
 * are its coordinates (`[lng, lat]`, matching the route line).
 */
data class RoutePoi(
    val id: String,
    val name: String?,
    /** One-line address built from the OSM address tags, when present. */
    val address: String?,
    val lon: Double,
    val lat: Double,
)

/**
 * The category of route POI to look up, and its `include` filter value for the reverse endpoint
 * (`osm.<key>.<value>`). Motorised vehicles surface fuel stations; bicycles surface bicycle
 * parking ([amenity=bicycle_parking](https://wiki.openstreetmap.org/wiki/Tag:amenity%3Dbicycle_parking)).
 */
enum class RoutePoiKind(val include: String) {
    Fuel("osm.amenity.fuel"),
    BicycleParking("osm.amenity.bicycle_parking"),
}
