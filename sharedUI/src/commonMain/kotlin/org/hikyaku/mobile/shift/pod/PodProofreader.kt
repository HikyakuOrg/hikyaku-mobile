package org.hikyaku.mobile.shift.pod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * State of the on-device grammar/typo check applied to the POD caption field when it loses
 * focus. Backed by ML Kit GenAI's Proofreading API (Gemini Nano via AICore) — on-device only.
 */
sealed interface PodProofreadState {
    data object Idle : PodProofreadState

    data object Preparing : PodProofreadState

    data object Unavailable : PodProofreadState

    /** Correcting the text the courier just tabbed away from. */
    data object Checking : PodProofreadState
}

@Stable
interface PodProofreaderController {
    val state: PodProofreadState

    /** Idempotent. Call once, when the shift starts, to warm the model up. */
    fun prepare()

    /**
     * Best-effort: proofreads [text] (call on field blur) and invokes [onCorrected] with the top
     * suggestion if one comes back. Silently no-ops on blank input, an unavailable/still-
     * downloading feature, or inference failure — the courier's as-typed text is always valid.
     */
    fun proofread(text: String, onCorrected: (String) -> Unit)
}

@Composable
expect fun rememberPodProofreader(): PodProofreaderController
