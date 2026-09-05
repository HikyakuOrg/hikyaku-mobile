package org.hikyaku.mobile.shift.pod

/**
 * Platform seam for durably queuing a proof-of-delivery photo that failed to upload inline (e.g.
 * no signal at the delivery point), so it can be retried silently in the background instead of
 * being lost.
 *
 * On Android [enqueue] persists the photo to disk and schedules a WorkManager job that retries
 * the upload once connectivity returns. On iOS, where there is no background work
 * infrastructure yet, it's a no-op that always fails, so the caller falls back to surfacing the
 * original inline failure exactly as before this queue existed.
 */
expect class PodUploadQueue() {
    /**
     * Queues [bytes] (the POD photo for [packageId], with optional [description] caption) for a
     * background retry. Returns failure only when queuing itself isn't possible.
     */
    suspend fun enqueue(packageId: String, bytes: ByteArray, description: String?): Result<Unit>
}
