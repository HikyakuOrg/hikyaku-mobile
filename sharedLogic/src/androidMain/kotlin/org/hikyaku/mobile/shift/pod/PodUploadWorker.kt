package org.hikyaku.mobile.shift.pod

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.environment.EnvironmentRepository
import org.hikyaku.mobile.shift.ShiftActionsRepository
import java.io.File
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * Retries a proof-of-delivery photo upload that failed inline, using the photo [PodUploadQueue]
 * wrote to disk. Allowed up to [MAX_ATTEMPTS] total executions; once exhausted, the photo is
 * discarded and the courier is notified, since there is no in-app retry surface once a stop is no
 * longer the in-transit one.
 *
 * May run in a process the OS woke up solely for this job, so [ensureBackendReady] replicates
 * [org.hikyaku.mobile.shift.departure.AutoStartCoordinator.ensureBackendReady] rather than
 * assuming [SupabaseClientProvider] was already initialised by the UI bootstrap.
 */
class PodUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): WorkResult {
        val packageId = inputData.getString(KEY_PACKAGE_ID) ?: return WorkResult.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return WorkResult.failure()
        val description = inputData.getString(KEY_DESCRIPTION)
        val file = File(filePath)
        if (!file.exists()) return WorkResult.failure()

        if (!ensureBackendReady()) return WorkResult.retry()

        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        val uploaded = ShiftActionsRepository().uploadProofPhoto(packageId, bytes, description)
        if (uploaded.isSuccess) {
            file.delete()
            return WorkResult.success()
        }

        if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            file.delete()
            notifyFailed(packageId)
            return WorkResult.failure()
        }
        return WorkResult.retry()
    }

    private suspend fun ensureBackendReady(): Boolean {
        if (!SupabaseClientProvider.isInitialized) {
            val stored = EnvironmentRepository().loadPersisted() ?: return false
            SupabaseClientProvider.initialize(stored.config)
        }
        val status = SupabaseClientProvider.client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        return status is SessionStatus.Authenticated
    }

    private fun notifyFailed(packageId: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Delivery photo didn't upload")
            .setContentText("The delivery was still recorded, but the proof photo couldn't be uploaded.")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(packageId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Delivery photo upload failed",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }
    }

    companion object {
        const val KEY_PACKAGE_ID = "packageId"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_DESCRIPTION = "description"
        private const val CHANNEL_ID = "pod_upload_failed"
        private const val MAX_ATTEMPTS = 3
    }
}
