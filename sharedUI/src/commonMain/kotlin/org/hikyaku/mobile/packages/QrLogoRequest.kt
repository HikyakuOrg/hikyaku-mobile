package org.hikyaku.mobile.packages

import coil3.request.ImageRequest

/**
 * Asks for the image to be decoded into a software bitmap. The QR painter buffers its output into
 * an off-screen software canvas, and drawing a hardware bitmap there throws on Android.
 */
expect fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder
