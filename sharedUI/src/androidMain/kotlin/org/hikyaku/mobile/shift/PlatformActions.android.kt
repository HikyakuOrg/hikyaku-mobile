package org.hikyaku.mobile.shift

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.rememberDefaultLocationProvider

@Composable
actual fun rememberShiftPermissions(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current

    // Background location can't be requested in the same prompt as foreground, and notifications
    // and activity recognition are separate runtime prompts, so the requests are chained: each
    // launcher's callback kicks off the next stage. Declared in dependency order so earlier
    // callbacks can reference later launchers. Activity recognition powers the auto-start watcher;
    // it isn't part of the start gate, so it's requested last and doesn't affect the reported grant.
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> onResult(hasBackgroundLocation(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        if (needsActivityRecognition(context)) {
            activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            onResult(hasBackgroundLocation(context))
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        when {
            needsNotifications(context) -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsActivityRecognition(context) -> activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else -> onResult(hasBackgroundLocation(context))
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            !granted -> onResult(false)
            needsBackgroundLocation(context) ->
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            needsNotifications(context) ->
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsActivityRecognition(context) ->
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else -> onResult(true)
        }
    }

    return {
        when {
            !hasForegroundLocation(context) -> foregroundLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            needsBackgroundLocation(context) ->
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            needsNotifications(context) ->
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsActivityRecognition(context) ->
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else -> onResult(true)
        }
    }
}

@Composable
actual fun rememberHasShiftTrackingPermissions(): Boolean {
    val context = LocalContext.current
    return hasForegroundLocation(context) &&
        hasBackgroundLocation(context) &&
        hasActivityRecognition(context)
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
actual fun rememberDialPhone(): (String) -> Unit {
    val context = LocalContext.current
    return { phoneNumber ->
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun hasForegroundLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocation(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        true // Pre-Android 10, background location is implied by foreground location.
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

private fun hasNotifications(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true // Pre-Android 13, notifications need no runtime grant.
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

private fun hasActivityRecognition(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        true // Pre-Android 10, activity recognition was an install-time permission.
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    }

private fun needsBackgroundLocation(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation(context)

private fun needsNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications(context)

private fun needsActivityRecognition(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityRecognition(context)

@Composable
actual fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    // The temp file the camera writes the full-resolution photo into.
    val pendingFile = remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingFile.value
        onResult(if (success && file != null) file.readBytes() else null)
        file?.delete()
        pendingFile.value = null
    }
    return {
        val file = File.createTempFile("pod_", ".jpg", context.cacheDir)
        pendingFile.value = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        launcher.launch(uri)
    }
}

/** Foreground location is already granted by the time a shift can be started (see [rememberShiftPermissions]). */
@Composable
actual fun rememberShiftLocationProvider(): LocationProvider = rememberDefaultLocationProvider()

@Composable
actual fun rememberImagePicker(onResult: (List<ByteArray>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        val bytes = uris.mapNotNull { uri ->
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        }
        onResult(bytes)
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
