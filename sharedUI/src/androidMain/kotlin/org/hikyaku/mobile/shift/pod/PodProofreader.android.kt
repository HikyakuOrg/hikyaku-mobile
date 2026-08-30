package org.hikyaku.mobile.shift.pod

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@Composable
actual fun rememberPodProofreader(): PodProofreaderController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AndroidPodProofreaderController(context, scope) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

private class AndroidPodProofreaderController(
    context: Context,
    private val scope: CoroutineScope,
) : PodProofreaderController {
    private val proofreader: Proofreader = Proofreading.getClient(
        ProofreaderOptions.builder(context)
            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build(),
    )
    private var ready = false

    override var state: PodProofreadState by mutableStateOf(PodProofreadState.Idle)
        private set

    override fun prepare() {
        if (ready || state == PodProofreadState.Preparing) return
        state = PodProofreadState.Preparing
        scope.launch {
            ready = warmUpGenAiFeature(
                checkStatus = { proofreader.checkFeatureStatus().await() },
                download = { callback -> proofreader.downloadFeature(callback) },
            )
            state = if (ready) PodProofreadState.Idle else PodProofreadState.Unavailable
        }
    }

    override fun proofread(text: String, onCorrected: (String) -> Unit) {
        if (!ready || text.isBlank()) return
        state = PodProofreadState.Checking
        scope.launch {
            runCatching {
                proofreader.runInference(ProofreadingRequest.builder(text).build()).await()
            }.onSuccess { result ->
                result.results.firstOrNull()?.text?.let(onCorrected)
            }
            state = PodProofreadState.Idle
        }
    }

    fun close() {
        runCatching { proofreader.close() }
    }
}
