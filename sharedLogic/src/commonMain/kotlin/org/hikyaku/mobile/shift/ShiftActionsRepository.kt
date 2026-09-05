package org.hikyaku.mobile.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.shift.pod.PodUploadQueue
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
 * publication, hence the dashboard's live feed); a database trigger on that table mirrors each
 * write into `driver_location_history`.
 */
class ShiftActionsRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val statusCatalog: PackageStatusCatalog = PackageStatusCatalog(client),
    private val uploadQueue: PodUploadQueue = PodUploadQueue(),
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
     * Upserts the driver's live position. PostGIS geometry columns accept EWKT, so the point is
     * sent as `SRID=4326;POINT(lng lat)` text and cast on insert. The row is keyed by
     * `driver_id`, which RLS requires to equal `auth.uid()`.
     *
     * The breadcrumb row in `driver_location_history` is appended by the table's
     * `log_driver_location_history` trigger, not from here: the trigger fires for every writer
     * (this app, the web dashboard, the API), it can't be skipped by a process death between two
     * requests, and it drops an update that doesn't move the point. A second insert from here
     * would simply duplicate every breadcrumb.
     */
    suspend fun updateLocation(lat: Double, lng: Double, speed: Double? = null): Result<Unit> = runCatching {
        val driverId = client.auth.currentUserOrNull()?.id ?: error("No authenticated user.")
        val point = "SRID=4326;POINT($lng $lat)"
        client.postgrest.from(SupabaseTables.DRIVER_CURRENT_LOCATION)
            .upsert(DriverLocationUpsert(driverId, point, speed)) { onConflict = "driver_id" }
        Unit
    }

    /**
     * Uploads a proof-of-delivery photo to `packages/{packageId}/pod.jpg` (the same private
     * bucket the detail screen reads, where RLS lets the assigned driver write) and records a
     * `package_proof_of_delivery` row pointing at it, along with an optional caption (either the
     * on-device AI-drafted description or the courier's own edit of it). Overwrites any existing
     * driver POD photo for the package.
     */
    suspend fun uploadProofPhoto(
        packageId: String,
        bytes: ByteArray,
        description: String? = null,
    ): Result<Unit> = runCatching {
        val path = "$packageId/pod.jpg"
        client.storage.from(SupabaseBuckets.PACKAGES).upload(path, bytes) { upsert = true }
        client.postgrest.from(SupabaseTables.PACKAGE_PROOF_OF_DELIVERY)
            .insert(PodInsert(packageId, POD_TYPE_PHOTO, path, description?.trim()?.takeIf { it.isNotBlank() }))
        Unit
    }

    /**
     * Uploads the POD photo inline, falling back to a durably-queued background retry
     * ([PodUploadQueue]) if that fails — e.g. no signal at the delivery point. Only reports
     * failure if the photo couldn't even be queued (unsupported platform, disk error), in which
     * case the original inline failure is what's surfaced, since the queue failure itself isn't
     * actionable by the caller.
     */
    suspend fun uploadProofPhotoOrQueue(
        packageId: String,
        bytes: ByteArray,
        description: String? = null,
    ): Result<Unit> {
        val result = uploadProofPhoto(packageId, bytes, description)
        if (result.isSuccess) return result
        return uploadQueue.enqueue(packageId, bytes, description).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { result },
        )
    }

    /**
     * Uploads a proof-of-delivery signature to `packages/{packageId}/signature/signature.png` and
     * records a `package_proof_of_delivery` row pointing at it. Unlike [uploadProofPhoto], this
     * never upserts and the signature sub-path carries no storage UPDATE grant, so a captured
     * signature can't be silently replaced; the unique `(package_id, pod_type_id)` index on
     * `package_proof_of_delivery` — which also has no UPDATE/DELETE policy at all — backs the same
     * guarantee at the metadata layer.
     */
    suspend fun uploadProofSignature(
        packageId: String,
        pngBytes: ByteArray,
    ): Result<Unit> = runCatching {
        val path = "$packageId/signature/signature.png"
        client.storage.from(SupabaseBuckets.PACKAGES).upload(path, pngBytes)
        client.postgrest.from(SupabaseTables.PACKAGE_PROOF_OF_DELIVERY)
            .insert(PodInsert(packageId, POD_TYPE_SIGNATURE, path))
        Unit
    }

    private companion object {
        // pod_type lookup ids (see the `pod_type` table).
        const val POD_TYPE_PHOTO = 2
        const val POD_TYPE_SIGNATURE = 3
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
private data class PodInsert(
    @SerialName("package_id") val packageId: String,
    @SerialName("pod_type_id") val podTypeId: Int,
    @SerialName("file_url") val fileUrl: String,
    val description: String? = null,
)
