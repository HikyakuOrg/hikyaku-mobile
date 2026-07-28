package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.shift.session.ShiftStatus
import org.hikyaku.mobile.supabase.SupabaseBuckets
import org.hikyaku.mobile.supabase.SupabaseTables

/**
 * The write side of running a shift: advancing a package's status, streaming the driver's
 * location, and attaching a proof-of-delivery photo. Complements the read-only
 * [ShiftDetailRepository].
 *
 * Status changes are recorded by inserting into `package_timeline`; the
 * `packages_with_latest_status` view the UI already reads derives `current_status` from the
 * latest timeline row, so an insert flips the displayed status. Row Level Security permits the
 * insert only for the package's assigned driver (`package_assignment.driver_id = auth.uid()`),
 * so these calls fail for a route that isn't the signed-in driver's.
 *
 * Location is upserted into `driver_current_location` (the only table in the `supabase_realtime`
 * publication, hence the dashboard's live feed) and appended to `driver_location_history`.
 */
class ShiftActionsRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val statusCatalog: PackageStatusCatalog = PackageStatusCatalog(client),
) {
    /** Marks [packageId] as in transit (the stop currently being driven to). */
    suspend fun markInTransit(packageId: String): Result<Unit> = setStatus(packageId, ShiftStatus.IN_TRANSIT)

    /** Marks [packageId] as delivered. */
    suspend fun markDelivered(packageId: String): Result<Unit> = setStatus(packageId, ShiftStatus.DELIVERED)

    /** Marks [packageId] as loaded onto the van, ahead of the shift starting. */
    suspend fun markOnboardForDelivery(packageId: String): Result<Unit> =
        setStatus(packageId, ShiftStatus.ONBOARD_FOR_DELIVERY)

    /**
     * Whether [packageId]'s authoritative status is already `IN_TRANSIT`, read from the
     * `packages_with_latest_status` view. Used by the auto-start safety net to avoid re-marking a
     * package the driver already started manually. Defaults to false on any read error.
     */
    suspend fun isInTransit(packageId: String): Boolean = runCatching {
        client.postgrest.from(SupabaseTables.PACKAGES_WITH_LATEST_STATUS)
            .select(Columns.raw("current_status")) { filter { eq("id", packageId) } }
            .decodeSingleOrNull<LatestStatusRow>()
            ?.currentStatus
            ?.equals(ShiftStatus.IN_TRANSIT, ignoreCase = true) == true
    }.getOrDefault(false)

    private suspend fun setStatus(packageId: String, statusEnum: String): Result<Unit> = runCatching {
        val statusId = statusCatalog.idFor(statusEnum)
        client.postgrest.from(SupabaseTables.PACKAGE_TIMELINE).insert(TimelineInsert(packageId, statusId))
        Unit
    }

    /**
     * Upserts the driver's live position and appends it to the breadcrumb history. PostGIS
     * geometry columns accept EWKT, so the point is sent as `SRID=4326;POINT(lng lat)` text and
     * cast on insert. The row is keyed by `driver_id`, which RLS requires to equal `auth.uid()`.
     */
    suspend fun updateLocation(lat: Double, lng: Double, speed: Double? = null): Result<Unit> = runCatching {
        val driverId = client.auth.currentUserOrNull()?.id ?: error("No authenticated user.")
        val point = "SRID=4326;POINT($lng $lat)"
        client.postgrest.from(SupabaseTables.DRIVER_CURRENT_LOCATION)
            .upsert(DriverLocationUpsert(driverId, point, speed)) { onConflict = "driver_id" }
        client.postgrest.from(SupabaseTables.DRIVER_LOCATION_HISTORY)
            .insert(DriverLocationHistoryInsert(driverId, point))
        Unit
    }

    /**
     * Uploads a proof-of-delivery photo to `packages/{packageId}/pod.jpg` (the same private
     * bucket the detail screen reads, where RLS lets the assigned driver write) and records a
     * `package_proof_of_delivery` row pointing at it. Overwrites any existing driver POD photo
     * for the package.
     */
    suspend fun uploadProofPhoto(packageId: String, bytes: ByteArray): Result<Unit> = runCatching {
        val path = "$packageId/pod.jpg"
        client.storage.from(SupabaseBuckets.PACKAGES).upload(path, bytes) { upsert = true }
        client.postgrest.from(SupabaseTables.PACKAGE_PROOF_OF_DELIVERY)
            .insert(PodInsert(packageId, POD_TYPE_PHOTO, path))
        Unit
    }

    private companion object {
        // pod_type lookup id for "Photo".
        const val POD_TYPE_PHOTO = 2
    }
}

@Serializable
private data class LatestStatusRow(
    @SerialName("current_status") val currentStatus: String? = null,
)

@Serializable
private data class TimelineInsert(
    @SerialName("package_id") val packageId: String,
    @SerialName("package_status") val status: Int,
)

@Serializable
private data class DriverLocationUpsert(
    @SerialName("driver_id") val driverId: String,
    val location: String,
    val speed: Double? = null,
)

@Serializable
private data class DriverLocationHistoryInsert(
    @SerialName("driver_id") val driverId: String,
    val location: String,
)

@Serializable
private data class PodInsert(
    @SerialName("package_id") val packageId: String,
    @SerialName("pod_type_id") val podTypeId: Int,
    @SerialName("file_url") val fileUrl: String,
)
