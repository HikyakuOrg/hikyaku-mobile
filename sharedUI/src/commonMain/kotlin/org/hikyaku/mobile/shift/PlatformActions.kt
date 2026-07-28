package org.hikyaku.mobile.shift

import androidx.compose.runtime.Composable
import org.maplibre.compose.location.LocationProvider

/**
 * Returns a launcher that requests every permission a backgrounded shift needs, then reports
 * whether tracking is fully permitted to [onResult]. On Android this stages the requests
 * (foreground location → background "Allow all the time" → notifications) and reports `true`
 * only when background location is granted; other platforms report `true` (no permission gate).
 */
@Composable
expect fun rememberShiftPermissions(onResult: (granted: Boolean) -> Unit): () -> Unit

/** Returns an action that opens this app's system settings page (to grant a denied permission). */
@Composable
expect fun rememberOpenAppSettings(): () -> Unit

/**
 * Returns an action that opens the phone dialer pre-filled with the given number (Android's
 * ACTION_DIAL, which never places the call itself, so it needs no CALL_PHONE permission). No-op
 * off Android.
 */
@Composable
expect fun rememberDialPhone(): (phoneNumber: String) -> Unit

/**
 * Whether every permission the background auto-start watcher needs (foreground + background
 * location and activity recognition) is already granted, so it can be armed silently without
 * prompting. Always false off Android, where there is no auto-start watcher.
 */
@Composable
expect fun rememberHasShiftTrackingPermissions(): Boolean

/**
 * Returns a launcher that opens the camera to capture a proof-of-delivery photo, delivering the
 * JPEG bytes to [onResult] (or `null` if cancelled/failed). Stubbed off Android.
 */
@Composable
expect fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit

/**
 * Returns a launcher that opens the system photo/file picker for one or more images, delivering
 * their bytes to [onResult] (empty if cancelled). Stubbed off Android.
 */
@Composable
expect fun rememberImagePicker(onResult: (List<ByteArray>) -> Unit): () -> Unit

/**
 * A [LocationProvider] for showing the driver's own position on the route map while a shift is
 * running. Backed by the platform's location APIs on Android (foreground location is already
 * granted by the time a shift starts); desktop has no such API, so it never emits.
 */
@Composable
expect fun rememberShiftLocationProvider(): LocationProvider
