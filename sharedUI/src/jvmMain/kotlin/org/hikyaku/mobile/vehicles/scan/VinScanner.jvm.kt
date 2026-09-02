package org.hikyaku.mobile.vehicles.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop has no camera pipeline and no on-device ML Kit, so every call is inert. */
@Composable
actual fun rememberVinScanner(): VinScannerController = DesktopVinScannerController

@Composable
actual fun VinCameraPreview(
    controller: VinScannerController,
    torchEnabled: Boolean,
    modifier: Modifier,
) {
    // Nothing to preview.
}

private object DesktopVinScannerController : VinScannerController {
    override val state: VinScanState = VinScanState.Unsupported
    override fun prepare() = Unit
    override fun prepareFallback() = Unit
    override fun scanImage(imageBytes: ByteArray) = Unit
    override fun reset() = Unit
}
