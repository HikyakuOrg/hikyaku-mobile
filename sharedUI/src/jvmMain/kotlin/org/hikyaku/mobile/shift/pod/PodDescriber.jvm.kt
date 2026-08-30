package org.hikyaku.mobile.shift.pod

import androidx.compose.runtime.Composable

/** Desktop has no on-device GenAI. */
@Composable
actual fun rememberPodDescriber(): PodDescriberController = object : PodDescriberController {
    override val state: PodDraftState = PodDraftState.Unavailable
    override fun prepare() {}
    override fun describe(photoBytes: ByteArray) {}
    override fun resetDraft() {}
}
