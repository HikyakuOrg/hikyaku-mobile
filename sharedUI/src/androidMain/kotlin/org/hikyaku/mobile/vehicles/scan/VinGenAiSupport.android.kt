package org.hikyaku.mobile.vehicles.scan

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.flow.Flow

/**
 * Sibling to [org.hikyaku.mobile.shift.pod.warmUpGenAiFeature], for the same job on a different
 * client shape.
 *
 * The older genai-image-description and genai-proofreading clients expose
 * `downloadFeature(DownloadCallback)`, which that helper bridges to a suspend function. genai-prompt
 * exposes only `download(): Flow<DownloadStatus>`, so the callback helper cannot be reused as-is.
 * Same contract otherwise: true once the feature is AVAILABLE (downloading first if it has to),
 * false on UNAVAILABLE or a failed download, and never throws.
 */
internal suspend fun warmUpGenAiPrompt(
    checkStatus: suspend () -> Int,
    download: () -> Flow<DownloadStatus>,
): Boolean {
    val status = runCatching { checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)
    return when (status) {
        FeatureStatus.AVAILABLE -> true
        FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> runCatching {
            var completed = false
            download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadCompleted -> completed = true
                    is DownloadStatus.DownloadFailed -> completed = false
                    else -> Unit
                }
            }
            completed
        }.getOrDefault(false)

        else -> false
    }
}
