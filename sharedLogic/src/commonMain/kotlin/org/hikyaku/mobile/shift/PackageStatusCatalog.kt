package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.shift.session.ShiftStatus
import org.hikyaku.mobile.supabase.SupabaseTables
import kotlin.concurrent.Volatile

/**
 * Resolves a `package_status` row from its machine `enums` string, so callers never hardcode a
 * status id or duplicate its human-readable `status` label. The table is small and effectively
 * static, so it's read once in full and cached for the process lifetime; a [Mutex] serialises
 * concurrent first-callers instead of firing one query per caller.
 *
 * The five statuses that existed before scanning shipped ([FALLBACK_IDS]) fall back to their known
 * ids if the lookup can't be completed, so a transient read failure can never break the existing
 * delivery flow. `ONBOARD_FOR_DELIVERY` has no such fallback: guessing its id risks silently
 * writing the wrong status, and scanning already requires a live connection, so a failed lookup
 * should surface as a retryable error instead.
 */
class PackageStatusCatalog(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    private val mutex = Mutex()

    /** Resolves the `package_status.id` for [statusEnum] (e.g. "ONBOARD_FOR_DELIVERY"). */
    suspend fun idFor(statusEnum: String): Int =
        rowFor(statusEnum)?.id
            ?: FALLBACK_IDS[statusEnum]
            ?: error("Unknown package_status.enums $statusEnum and no cached/fallback id.")

    /**
     * Resolves the `package_status.status` human-readable label for [statusEnum] (e.g. "Onboard
     * For Delivery"), or null if the lookup fails and there's no cached row yet.
     */
    suspend fun labelFor(statusEnum: String): String? = rowFor(statusEnum)?.label

    private suspend fun rowFor(statusEnum: String): PackageStatusLookupRow? {
        cache[statusEnum]?.let { return it }
        mutex.withLock {
            cache[statusEnum]?.let { return it }
            val fetched = runCatching {
                client.postgrest.from(SupabaseTables.PACKAGE_STATUS)
                    .select()
                    .decodeList<PackageStatusLookupRow>()
                    .associateBy { it.enums }
            }.getOrNull()
            if (fetched != null) cache = fetched
        }
        return cache[statusEnum]
    }

    private companion object {
        /** Process-wide cache: `enums` -> row. Shared across instances since the table is static. */
        @Volatile
        var cache: Map<String, PackageStatusLookupRow> = emptyMap()

        /** Known ids for the statuses that existed before scanning shipped; last-resort fallback only. */
        val FALLBACK_IDS = mapOf(
            ShiftStatus.DELIVERED to 1,
            ShiftStatus.FAILED to 2,
            ShiftStatus.PENDING to 3,
            ShiftStatus.ASSIGNED to 4,
            ShiftStatus.IN_TRANSIT to 5,
        )
    }
}

@Serializable
private data class PackageStatusLookupRow(
    val id: Int,
    val enums: String,
    @SerialName("status") val label: String? = null,
)
