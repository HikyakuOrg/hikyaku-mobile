package org.hikyaku.mobile.shift.pod

/**
 * iOS stub. A real implementation should persist the photo and schedule a `BGTaskScheduler` job;
 * until then a failed inline upload's photo simply isn't recoverable, same as before this queue
 * existed.
 */
actual class PodUploadQueue actual constructor() {
    actual suspend fun enqueue(packageId: String, bytes: ByteArray, description: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException("Background photo upload retry isn't supported on this platform."))
}
