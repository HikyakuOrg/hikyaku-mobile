package org.hikyaku.mobile.packages

import coil3.request.ImageRequest
import coil3.request.allowHardware

actual fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder = allowHardware(false)
