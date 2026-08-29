package org.hikyaku.mobile.packages

import kotlin.time.Instant
import org.hikyaku.mobile.api.generated.models.CreatePackageDto
import org.hikyaku.mobile.api.generated.models.PackageDimensionsDto
import org.hikyaku.mobile.packages.model.PackageDraft

/**
 * Maps a composed [PackageDraft] onto the `POST /api/v1/packages` body. Kept a pure function,
 * separate from [PackageRepository], so the wire shape can be tested without a client: the
 * repository's only remaining job on the request side is resolving the two customer ids.
 *
 * [id] is minted client-side and sent deliberately — `CreatePackageDto.id` is optional precisely so
 * both clients can name the Supabase Storage path for the package's photos before the row exists,
 * and it makes a retried create idempotent rather than duplicating the package.
 *
 * [PackageDraft.scheduledArrival] is the customer deadline, so it maps to `deadlineAt`. It must be
 * a parseable ISO-8601 instant (every caller builds it with
 * [org.hikyaku.mobile.util.combineDateAndTimeToIsoUtc]); a malformed value throws rather than
 * quietly sending a deadline-less package, which the backend would treat as evictable.
 */
fun PackageDraft.toCreatePackageDto(
    id: String,
    fromCustomerId: String,
    toCustomerId: String,
): CreatePackageDto = CreatePackageDto(
    warehouseId = warehouseId,
    fromCustomerId = fromCustomerId,
    toCustomerId = toCustomerId,
    dimensions = PackageDimensionsDto(
        weightKg = weightKg,
        lengthCm = lengthCm,
        widthCm = widthCm,
        heightCm = heightCm,
    ),
    id = id,
    deliveryNotes = deliveryNotes?.takeIf { it.isNotBlank() },
    deadlineAt = Instant.parse(scheduledArrival),
    autoAssign = autoAssign,
)
