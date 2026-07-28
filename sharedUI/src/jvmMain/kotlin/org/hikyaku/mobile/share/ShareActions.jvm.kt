package org.hikyaku.mobile.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.tracking_url_copied
import kotlinx.coroutines.launch
import org.hikyaku.mobile.toast.LocalToastHostState
import org.jetbrains.compose.resources.getString

/** Desktop has no system share sheet; copies [text] to the clipboard and toasts confirmation instead. */
@Composable
actual fun rememberShareText(): (String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    val hostState = LocalToastHostState.current
    val scope = rememberCoroutineScope()
    return { text ->
        clipboardManager.setText(AnnotatedString(text))
        scope.launch { hostState.showSnackbar(getString(Res.string.tracking_url_copied)) }
    }
}
