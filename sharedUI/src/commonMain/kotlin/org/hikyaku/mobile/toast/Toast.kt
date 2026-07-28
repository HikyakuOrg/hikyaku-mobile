package org.hikyaku.mobile.toast

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf

/** Provided by [org.hikyaku.mobile.App] so any screen can surface a toast without its own host. */
val LocalToastHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — LocalToastHostState must be set in App()")
}

/**
 * Shows [message] as a toast whenever it changes to a non-null value. For transient,
 * action-result errors (a failed submit, a failed sign-in); content-load failures with a
 * Retry action should stay as inline cards instead, since a toast would disappear along
 * with the only way to retry.
 */
@Composable
fun ToastEffect(message: String?) {
    val hostState = LocalToastHostState.current
    LaunchedEffect(message) {
        if (message != null) {
            hostState.showSnackbar(message)
        }
    }
}
