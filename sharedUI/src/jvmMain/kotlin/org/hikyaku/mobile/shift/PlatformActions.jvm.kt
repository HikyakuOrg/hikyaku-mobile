package org.hikyaku.mobile.shift

import androidx.compose.runtime.Composable

/** Desktop has no permission gate; shifts can be started for testing (location simply won't stream). */
@Composable
actual fun rememberShiftPermissions(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

/** Desktop has no app-settings page. */
@Composable
actual fun rememberOpenAppSettings(): () -> Unit = { }

/** Desktop has no phone dialer. */
@Composable
actual fun rememberDialPhone(): (String) -> Unit = { }

/** Desktop has no auto-start watcher. */
@Composable
actual fun rememberHasShiftTrackingPermissions(): Boolean = false

/** Desktop has no camera capture. */
@Composable
actual fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }

/** Desktop has no image picker. */
@Composable
actual fun rememberImagePicker(onResult: (List<ByteArray>) -> Unit): () -> Unit = { onResult(emptyList()) }

/** Desktop has no runtime permission model, and no camera to grant access to. */
@Composable
actual fun rememberCameraPermission(): CameraPermissionState = DesktopCameraPermission

private object DesktopCameraPermission : CameraPermissionState {
    override val granted: Boolean = false
    override val denied: Boolean = false
    override fun request() = Unit
}
