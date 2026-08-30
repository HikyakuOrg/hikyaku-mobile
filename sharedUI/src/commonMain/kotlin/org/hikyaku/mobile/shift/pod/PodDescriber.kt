package org.hikyaku.mobile.shift.pod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * State of the on-device AI-drafted caption for the proof-of-delivery photo captured at the
 * current stop. Backed by ML Kit GenAI's Image Description API (Gemini Nano via AICore) —
 * input, inference, and output all stay on-device.
 */
sealed interface PodDraftState {
    /** No draft: nothing captured yet, or warm-up hasn't resolved. */
    data object Idle : PodDraftState

    /** Warming the model up (checkFeatureStatus/downloadFeature), ahead of any capture. */
    data object Preparing : PodDraftState

    /** Device/build doesn't support the feature — terminal for this session. */
    data object Unavailable : PodDraftState

    /** Inference running for the just-captured photo. */
    data object Analyzing : PodDraftState

    /** Draft caption ready; the screen pre-fills the editable field with it exactly once. */
    data class Ready(val text: String) : PodDraftState

    /** Inference failed; the field stays empty for manual entry. */
    data class Failed(val message: String) : PodDraftState
}

/** Drives [PodDraftState] for the photo captured at the currently in-transit stop. */
@Stable
interface PodDescriberController {
    val state: PodDraftState

    /** Idempotent. Call once, when the shift starts, to warm the model up ahead of capture. */
    fun prepare()

    /** Fire-and-forget inference on a freshly captured photo. No-ops if not yet available. */
    fun describe(photoBytes: ByteArray)

    /** Clears drafted/analyzing state (moving to the next stop, or discarding the photo). */
    fun resetDraft()
}

/**
 * Android-only in practice: on JVM/desktop this is a permanent no-op ([PodDraftState.Unavailable]).
 */
@Composable
expect fun rememberPodDescriber(): PodDescriberController
