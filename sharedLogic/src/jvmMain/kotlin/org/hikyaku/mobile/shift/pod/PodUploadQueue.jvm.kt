package org.hikyaku.mobile.shift.pod

/** Desktop has no background work infrastructure and isn't a courier surface; never queues. */
actual class PodUploadQueue actual constructor() {
    actual suspend fun enqueue(packageId: String, bytes: ByteArray, description: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException("Background photo upload retry isn't supported on this platform."))
}
