package org.hikyaku.mobile.shift.pod

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@Composable
actual fun rememberPodDescriber(): PodDescriberController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AndroidPodDescriberController(context, scope) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

private class AndroidPodDescriberController(
    context: Context,
    private val scope: CoroutineScope,
) : PodDescriberController {
    private val describer: ImageDescriber =
        ImageDescription.getClient(ImageDescriberOptions.builder(context).build())
    private var ready = false

    override var state: PodDraftState by mutableStateOf(PodDraftState.Idle)
        private set

    override fun prepare() {
        if (ready || state == PodDraftState.Preparing) return
        state = PodDraftState.Preparing
        scope.launch {
            ready = warmUpGenAiFeature(
                checkStatus = { describer.checkFeatureStatus().await() },
                download = { callback -> describer.downloadFeature(callback) },
            )
            state = if (ready) PodDraftState.Idle else PodDraftState.Unavailable
        }
    }

    override fun describe(photoBytes: ByteArray) {
        if (!ready) return // Still preparing/unavailable — leave the field for manual entry.
        state = PodDraftState.Analyzing
        scope.launch {
            val bitmap = runCatching {
                BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            }.getOrNull()
            if (bitmap == null) {
                state = PodDraftState.Failed("Couldn't read the captured photo.")
                return@launch
            }
            runCatching {
                describer.runInference(ImageDescriptionRequest.builder(bitmap).build()).await()
            }.onSuccess {
                state = PodDraftState.Ready(it.description)
            }.onFailure {
                state = PodDraftState.Failed(it.message ?: "Couldn't generate a description.")
            }
        }
    }

    override fun resetDraft() {
        state = if (ready) PodDraftState.Idle else state
    }

    fun close() {
        runCatching { describer.close() }
    }
}
