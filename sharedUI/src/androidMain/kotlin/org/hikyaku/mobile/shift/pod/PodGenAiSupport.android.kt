package org.hikyaku.mobile.shift.pod

import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Shared lifecycle plumbing for on-device GenAI features: the [PodDescriberController] and
 * [PodProofreaderController] Android actuals both drive their readiness through the identical
 * checkFeatureStatus/downloadFeature shape (from `genai-common`), so both bridge it through this
 * one helper instead of duplicating the DownloadCallback-to-suspend glue.
 *
 * Returns true once the feature is AVAILABLE (downloading first if needed), false on
 * UNAVAILABLE or a failed download. Never throws.
 */
internal suspend fun warmUpGenAiFeature(
    checkStatus: suspend () -> Int,
    download: (DownloadCallback) -> Unit,
): Boolean {
    val status = runCatching { checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)
    return when (status) {
        FeatureStatus.AVAILABLE -> true
        FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> suspendCancellableCoroutine { cont ->
            download(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {}
                override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                override fun onDownloadCompleted() {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onDownloadFailed(e: GenAiException) {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
        else -> false
    }
}
