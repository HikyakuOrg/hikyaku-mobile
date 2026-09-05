package org.hikyaku.mobile.shift.pod

import androidx.compose.ui.graphics.ImageBitmap

/** Encodes [this] as PNG bytes, suitable for uploading straight to Supabase Storage. */
expect fun ImageBitmap.encodeToPngBytes(): ByteArray
