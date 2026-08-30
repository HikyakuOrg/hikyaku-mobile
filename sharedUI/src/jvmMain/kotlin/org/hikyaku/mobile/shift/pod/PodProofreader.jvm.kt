package org.hikyaku.mobile.shift.pod

import androidx.compose.runtime.Composable

/** Desktop has no on-device GenAI. */
@Composable
actual fun rememberPodProofreader(): PodProofreaderController = object : PodProofreaderController {
    override val state: PodProofreadState = PodProofreadState.Unavailable
    override fun prepare() {}
    override fun proofread(text: String, onCorrected: (String) -> Unit) {}
}
