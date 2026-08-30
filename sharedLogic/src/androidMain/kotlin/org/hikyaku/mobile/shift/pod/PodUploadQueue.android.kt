package org.hikyaku.mobile.shift.pod

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hikyaku.mobile.AppContextHolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Writes the photo to internal storage (not cache — cache can be cleared under storage pressure,
 * and these bytes must survive until [PodUploadWorker] consumes or exhausts them) and enqueues a
 * WorkManager job keyed by [packageId], so a repeat failure for the same package replaces rather
 * than stacks a duplicate job.
 */
actual class PodUploadQueue actual constructor() {
    actual suspend fun enqueue(packageId: String, bytes: ByteArray, description: String?): Result<Unit> =
        runCatching {
            val context = AppContextHolder.context
            val file = withContext(Dispatchers.IO) {
                File(context.filesDir, QUEUE_DIR).apply { mkdirs() }
                    .resolve("$packageId.jpg")
                    .apply { writeBytes(bytes) }
            }

            val data = Data.Builder()
                .putString(PodUploadWorker.KEY_PACKAGE_ID, packageId)
                .putString(PodUploadWorker.KEY_FILE_PATH, file.absolutePath)
                .putString(PodUploadWorker.KEY_DESCRIPTION, description)
                .build()
            val request = OneTimeWorkRequestBuilder<PodUploadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(packageId, ExistingWorkPolicy.REPLACE, request)
            Unit
        }

    private companion object {
        const val QUEUE_DIR = "pod-upload-queue"
    }
}
