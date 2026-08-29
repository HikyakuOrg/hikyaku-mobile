package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.supabase.SupabaseFunctions
import org.maplibre.spatialk.geojson.Point

/**
 * Reads the signed-in driver's own breadcrumb trail from `driver_location_history` (written by
 * [ShiftActionsRepository.updateLocation] while a shift runs), via the same
 * `get_driver_location_history` RPC the web dashboard's driver-tracking view uses. Lets a driver
 * see the path they actually drove once a shift completes.
 *
 * The history table carries no shift/route id, so the only way to isolate one shift's breadcrumbs
 * from every other shift the driver has ever run is a time window — callers pass the shift's own
 * start/end wall-clock times. RLS (`driver_id = auth.uid()`) independently restricts results to
 * the signed-in driver's own rows regardless of [driverId].
 */
class ShiftRouteHistoryRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /** Points in travel order (oldest first), or failure if not signed in or the request fails. */
    suspend fun fetchTravelledRoute(fromIso: String, toIso: String): Result<List<Point>> = runCatching {
        val driverId = client.auth.currentUserOrNull()?.id ?: error("No authenticated user.")
        client.postgrest.rpc(
            SupabaseFunctions.GET_DRIVER_LOCATION_HISTORY,
            LocationHistoryParams(driverId, fromIso, toIso),
        )
            .decodeList<LocationHistoryRow>()
            .sortedBy { it.createdAt } // the RPC orders newest-first; a travelled path wants oldest-first
            .map { Point(longitude = it.lng, latitude = it.lat) }
    }
}

@Serializable
private data class LocationHistoryParams(
    @SerialName("p_driver_id") val driverId: String,
    @SerialName("from_ts") val fromTs: String,
    @SerialName("to_ts") val toTs: String,
)

@Serializable
private data class LocationHistoryRow(
    @SerialName("created_at") val createdAt: String,
    val lat: Double,
    val lng: Double,
)
