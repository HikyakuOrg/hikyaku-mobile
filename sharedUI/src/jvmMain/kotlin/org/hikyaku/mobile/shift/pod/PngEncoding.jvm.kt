package org.hikyaku.mobile.shift.pod

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

// Unlike the rest of this feature, org.jetbrains.skia's API wasn't verifiable via kmp-lsp here
// (its sources jar isn't in the local Gradle cache to extract) — this is written from Skiko's
// long-stable, well-documented surface rather than confirmed against this project's exact version.
actual fun ImageBitmap.encodeToPngBytes(): ByteArray {
    val data = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
        ?: error("Failed to encode signature to PNG.")
    return data.bytes
}
