package org.hikyaku.mobile.shift.pod

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

actual fun ImageBitmap.encodeToPngBytes(): ByteArray =
    ByteArrayOutputStream().use { stream ->
        asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
