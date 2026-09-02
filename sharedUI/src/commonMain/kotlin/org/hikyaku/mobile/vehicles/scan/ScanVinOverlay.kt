package org.hikyaku.mobile.vehicles.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_grant_permission
import hikyaku.sharedui.generated.resources.action_open_settings
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.cd_vin_scan_close
import hikyaku.sharedui.generated.resources.vin_scan_analyzing
import hikyaku.sharedui.generated.resources.vin_scan_camera_permission
import hikyaku.sharedui.generated.resources.vin_scan_camera_unavailable
import hikyaku.sharedui.generated.resources.vin_scan_choose_image
import hikyaku.sharedui.generated.resources.vin_scan_failed
import hikyaku.sharedui.generated.resources.vin_scan_found
import hikyaku.sharedui.generated.resources.vin_scan_found_ai
import hikyaku.sharedui.generated.resources.vin_scan_instruction
import hikyaku.sharedui.generated.resources.vin_scan_not_found
import hikyaku.sharedui.generated.resources.vin_scan_preparing
import hikyaku.sharedui.generated.resources.vin_scan_title
import hikyaku.sharedui.generated.resources.vin_scan_torch
import kotlinx.coroutines.delay
import org.hikyaku.mobile.shift.rememberCameraPermission
import org.hikyaku.mobile.shift.rememberImagePicker
import org.hikyaku.mobile.shift.rememberOpenAppSettings
import org.jetbrains.compose.resources.stringResource

/** How long the "Found …" banner stays up before the overlay hands the VIN back and closes. */
private const val VIN_HANDOFF_DELAY_MS = 500L

/**
 * The VIN scanner: aim at the plate and the field fills itself, or pick a photo of the plate from
 * the gallery.
 *
 * Rendered as a full-screen [Dialog] — mirroring
 * [org.hikyaku.mobile.shift.scan.ScanPackagesOverlay] — so the add-vehicle form keeps its existing
 * `AddVehicleViewModel` and every field the user already typed, instead of needing a new nav route
 * and `savedStateHandle` result plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanVinOverlay(
    onClose: () -> Unit,
    onVinRecognised: (String) -> Unit,
) {
    val controller = rememberVinScanner()
    val cameraPermission = rememberCameraPermission()
    val pickImage = rememberImagePicker { images ->
        images.firstOrNull()?.let(controller::scanImage)
    }
    var torchEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.prepare()
        controller.prepareFallback()
    }
    LaunchedEffect(Unit) {
        if (vinScanningSupported && !cameraPermission.granted) cameraPermission.request()
    }

    val state = controller.state
    val found = state as? VinScanState.Found
    LaunchedEffect(found) {
        if (found != null) {
            delay(VIN_HANDOFF_DELAY_MS)
            onVinRecognised(found.vin)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.vin_scan_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.cd_vin_scan_close),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                when {
                    !vinScanningSupported || state is VinScanState.Unsupported ->
                        UnavailablePanel(stringResource(Res.string.vin_scan_camera_unavailable))

                    !cameraPermission.granted -> CameraPermissionPanel(
                        denied = cameraPermission.denied,
                        onRequest = cameraPermission::request,
                    )

                    else -> Box(
                        Modifier.fillMaxWidth().aspectRatio(3f / 4f).clipToBounds(),
                    ) {
                        VinCameraPreview(
                            controller = controller,
                            torchEnabled = torchEnabled,
                            modifier = Modifier.fillMaxSize(),
                        )
                        TextButton(
                            onClick = { torchEnabled = !torchEnabled },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ),
                        ) {
                            Text(stringResource(Res.string.vin_scan_torch))
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.vin_scan_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )

                VinScanFeedback(
                    state = state,
                    onRetry = controller::reset,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = pickImage,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(Res.string.vin_scan_choose_image))
                }
            }
        }
    }
}

/** Visual weight of a feedback message; keeps the colour choices in one place. */
private enum class FeedbackTone { Busy, Success, Caution, Error }

@Composable
private fun VinScanFeedback(
    state: VinScanState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to say while the camera is simply looking; the instruction line already covers it.
    if (state is VinScanState.Scanning) return

    val (message, tone) = when (state) {
        VinScanState.PreparingModels ->
            stringResource(Res.string.vin_scan_preparing) to FeedbackTone.Busy

        VinScanState.AnalyzingImage ->
            stringResource(Res.string.vin_scan_analyzing) to FeedbackTone.Busy

        is VinScanState.Found -> if (state.viaFallback) {
            stringResource(Res.string.vin_scan_found_ai, state.vin) to FeedbackTone.Caution
        } else {
            stringResource(Res.string.vin_scan_found, state.vin) to FeedbackTone.Success
        }

        VinScanState.NotFound ->
            stringResource(Res.string.vin_scan_not_found) to FeedbackTone.Error

        VinScanState.Failed ->
            stringResource(Res.string.vin_scan_failed) to FeedbackTone.Error

        // Rendered by the panel above the feedback row, so there is nothing to repeat here.
        VinScanState.Unsupported, VinScanState.Scanning -> return
    }

    val container = when (tone) {
        FeedbackTone.Busy -> MaterialTheme.colorScheme.surfaceVariant
        FeedbackTone.Success -> MaterialTheme.colorScheme.primaryContainer
        FeedbackTone.Caution -> MaterialTheme.colorScheme.secondaryContainer
        FeedbackTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        FeedbackTone.Busy -> MaterialTheme.colorScheme.onSurfaceVariant
        FeedbackTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        FeedbackTone.Caution -> MaterialTheme.colorScheme.onSecondaryContainer
        FeedbackTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                when (tone) {
                    FeedbackTone.Busy -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = onContainer,
                    )

                    FeedbackTone.Success -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = onContainer,
                    )

                    FeedbackTone.Caution, FeedbackTone.Error -> Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = onContainer,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(message, color = onContainer, style = MaterialTheme.typography.bodyMedium)
            }
            if (tone == FeedbackTone.Error) {
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun CameraPermissionPanel(denied: Boolean, onRequest: () -> Unit) {
    val openSettings = rememberOpenAppSettings()
    Box(Modifier.fillMaxWidth().height(240.dp).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.vin_scan_camera_permission),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            // Once the system prompt has been refused it will not show again, so the only way
            // forward is the app's settings page.
            if (denied) {
                TextButton(onClick = openSettings) {
                    Text(stringResource(Res.string.action_open_settings))
                }
            } else {
                TextButton(onClick = onRequest) {
                    Text(stringResource(Res.string.action_grant_permission))
                }
            }
        }
    }
}

@Composable
private fun UnavailablePanel(message: String) {
    Box(Modifier.fillMaxWidth().height(240.dp).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
