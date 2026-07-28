package org.hikyaku.mobile.share

import androidx.compose.runtime.Composable

/**
 * Returns an action that opens the system share sheet with [text] (Android's ACTION_SEND, as
 * plain text so it works with SMS/WhatsApp/email/etc). No-op off Android.
 */
@Composable
expect fun rememberShareText(): (text: String) -> Unit
