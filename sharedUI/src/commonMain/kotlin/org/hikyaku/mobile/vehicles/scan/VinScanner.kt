package org.hikyaku.mobile.vehicles.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * Where a VIN scan currently stands. Mirrors the shape of
 * [org.hikyaku.mobile.shift.pod.PodDraftState]: a sealed state the controller owns and the overlay
 * renders, with no exceptions crossing the boundary.
 */
sealed interface VinScanState {
    /**
     * Google Play Services is still fetching the OCR and barcode modules.
     *
     * This has to be its own state rather than folding into [NotFound]: while a module is
     * downloading ML Kit returns *empty results* instead of failing, so an un-downloaded model is
     * otherwise indistinguishable from a label with no VIN on it.
     */
    data object PreparingModels : VinScanState

    /** Camera bound and analysing frames; nothing recognised yet. */
    data object Scanning : VinScanState

    /** A still image is being run through OCR, barcode decoding and, if needed, the fallback. */
    data object AnalyzingImage : VinScanState

    /**
     * [viaFallback] is true when the on-device generative model produced this rather than OCR or a
     * barcode. Worth telling the user, because it is materially less trustworthy.
     */
    data class Found(val vin: String, val viaFallback: Boolean) : VinScanState

    /** A still-image pass completed without finding a VIN. Terminal for that image only. */
    data object NotFound : VinScanState

    /** The image could not be decoded at all. Carries no message: the copy belongs to the UI. */
    data object Failed : VinScanState

    /** No camera, or no Google Play Services on this device. Terminal for the session. */
    data object Unsupported : VinScanState
}

/**
 * Drives VIN recognition for one scanner session. The Android actual owns the ML Kit clients and
 * the CameraX analysis loop; a platform without them is permanently [VinScanState.Unsupported].
 */
@Stable
interface VinScannerController {
    val state: VinScanState

    /**
     * Ensures the ML Kit modules are installed, holding [VinScanState.PreparingModels] until they
     * are. Idempotent — safe to call on every recomposition.
     */
    fun prepare()

    /**
     * Warms the on-device generative fallback if this build has AICore. Idempotent, never blocks
     * the UI, and silently does nothing on the overwhelming majority of devices.
     */
    fun prepareFallback()

    /**
     * Still-image path: gallery bytes, or a captured frame. Runs OCR and barcode decoding at
     * several rotations, then the generative fallback only if both come up empty.
     */
    fun scanImage(imageBytes: ByteArray)

    /** Back to [VinScanState.Scanning], clearing the live-frame accumulator. */
    fun reset()
}

/** Android binds CameraX and ML Kit; a platform without them returns an unsupported stub. */
@Composable
expect fun rememberVinScanner(): VinScannerController

/**
 * The live preview and frame-analysis surface. Renders nothing where [vinScanningSupported] is
 * false, so callers can place it unconditionally.
 */
@Composable
expect fun VinCameraPreview(
    controller: VinScannerController,
    torchEnabled: Boolean,
    modifier: Modifier = Modifier,
)
