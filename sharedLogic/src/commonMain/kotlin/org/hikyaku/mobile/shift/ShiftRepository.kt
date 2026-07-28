package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.shift.model.Shift
import org.hikyaku.mobile.shift.model.ShiftProgress
import org.hikyaku.mobile.supabase.SupabaseTables

class ShiftRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /**
     * Returns the shifts (route-optimisation runs) belonging to [orgId], newest
     * first. The `organisation_id` filter scopes the result to the selected
     * organisation; Row Level Security additionally restricts it to users with
     * the `shifts.view` permission in that org.
     */
    suspend fun fetchShifts(orgId: String): Result<List<Shift>> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .select(
                Columns.raw("id, created_at, provider, scheduled_start, vrp_solution(routes_count, unassigned_count, duration)"),
            ) {
                filter { eq("organisation_id", orgId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Shift>()
    }

    /**
     * Delivery progress per shift for [orgId], keyed by `vrp_optimization` id. A package belongs
     * to a shift via `packages.optimisation_id`; its latest status comes from the
     * `packages_with_latest_status` view. Used by the home screen to separate active shifts from
     * completed ones (every package delivered).
     */
    suspend fun fetchShiftProgress(orgId: String): Result<Map<String, ShiftProgress>> = runCatching {
        val packages = client.postgrest.from(SupabaseTables.PACKAGES)
            .select(Columns.raw("id, optimisation_id")) {
                filter { eq("organisation_id", orgId) }
            }
            .decodeList<PackageShiftRow>()
            .filter { it.optimisationId != null }
        if (packages.isEmpty()) return@runCatching emptyMap()

        val deliveredIds = client.postgrest.from(SupabaseTables.PACKAGES_WITH_LATEST_STATUS)
            .select(Columns.raw("id, current_status")) {
                filter { isIn("id", packages.map { it.id }) }
            }
            .decodeList<PackageStatusRow>()
            .filter { it.currentStatus == STATUS_DELIVERED }
            .map { it.id }
            .toSet()

        packages.groupBy { it.optimisationId!! }
            .mapValues { (_, pkgs) ->
                ShiftProgress(delivered = pkgs.count { it.id in deliveredIds }, total = pkgs.size)
            }
    }

    /**
     * Deletes shift [shiftId], scoped to [orgId] so RLS and this filter agree on which row can be
     * removed. Callers are responsible for only exposing this to personal organisations.
     */
    suspend fun deleteShift(orgId: String, shiftId: String): Result<Unit> = runCatching {
        client.postgrest.from(SupabaseTables.VRP_OPTIMIZATION)
            .delete {
                filter {
                    eq("id", shiftId)
                    eq("organisation_id", orgId)
                }
            }
    }

    private companion object {
        const val STATUS_DELIVERED = "DELIVERED"
    }
}

@Serializable
private data class PackageShiftRow(
    val id: String,
    @SerialName("optimisation_id") val optimisationId: String? = null,
)

@Serializable
private data class PackageStatusRow(
    val id: String,
    @SerialName("current_status") val currentStatus: String? = null,
)
