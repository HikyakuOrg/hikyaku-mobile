package org.hikyaku.mobile.shift.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.action_open_settings
import hikyaku.sharedui.generated.resources.cd_shift_scan_close
import hikyaku.sharedui.generated.resources.shift_scan_accepted
import hikyaku.sharedui.generated.resources.shift_scan_already
import hikyaku.sharedui.generated.resources.shift_scan_camera_permission
import hikyaku.sharedui.generated.resources.shift_scan_camera_unavailable
import hikyaku.sharedui.generated.resources.shift_scan_failed
import hikyaku.sharedui.generated.resources.shift_scan_flashlight
import hikyaku.sharedui.generated.resources.shift_scan_instruction
import hikyaku.sharedui.generated.resources.shift_scan_manual_label
import hikyaku.sharedui.generated.resources.shift_scan_manual_submit
import hikyaku.sharedui.generated.resources.shift_scan_manual_title
import hikyaku.sharedui.generated.resources.shift_scan_not_on_shift
import hikyaku.sharedui.generated.resources.shift_scan_not_ready
import hikyaku.sharedui.generated.resources.shift_scan_progress
import hikyaku.sharedui.generated.resources.shift_scan_refresh_failed
import hikyaku.sharedui.generated.resources.shift_scan_remaining_count
import hikyaku.sharedui.generated.resources.shift_scan_remaining_title
import hikyaku.sharedui.generated.resources.shift_scan_title
import hikyaku.sharedui.generated.resources.shift_scan_unrecognised
import org.hikyaku.mobile.shift.ShiftDetailViewModel
import org.hikyaku.mobile.shift.detail.model.RouteStep
import org.hikyaku.mobile.shift.rememberOpenAppSettings
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import qrscanner.CameraLens
import qrscanner.OverlayShape
import qrscanner.QrScanner

/**
 * The load-scanning overlay: scan every package's QR code (or enter its tracking number manually)
 * before the shift may start. Rendered as a full-screen [Dialog] — mirroring `FullScreenRouteMap`
 * in `ShiftDetailScreen.kt` — so it keeps the existing [ShiftDetailViewModel] instance rather than
 * needing a new nav route and `savedStateHandle` result plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPackagesOverlay(
    state: ShiftDetailViewModel.UiState,
    onClose: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onQrScanned: (String) -> Unit,
    onUpdateManualEntry: (String) -> Unit,
    onToggleManualEntry: () -> Unit,
    onSubmitManualEntry: () -> Unit,
    onRetryFailedScan: () -> Unit,
    onDismissFeedback: () -> Unit,
    onRefresh: () -> Unit,
) {
    val draft = state.scan ?: return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.shift_scan_title)) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_shift_scan_close))
                            }
                        },
                    )
                },
            ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (qrScanningSupported) {
                    // Bounded to a square (matching the scanner's own square viewfinder) and clipped
                    // so the camera preview can never crowd out the top bar or the controls below it.
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()) {
                        QrScanner(
                            modifier = Modifier.fillMaxSize(),
                            flashlightOn = draft.flashlightOn,
                            cameraLens = CameraLens.Back,
                            openImagePicker = false,
                            // The VM debounces repeated frames from the same held-up label.
                            onCompletion = onQrScanned,
                            imagePickerHandler = { },
                            onFailure = { },
                            overlayShape = OverlayShape.Square,
                            permissionDeniedView = { CameraPermissionDenied() },
                        )
                        TextButton(
                            onClick = onToggleFlashlight,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ),
                        ) {
                            Text(stringResource(Res.string.shift_scan_flashlight))
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(240.dp).padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.shift_scan_camera_unavailable),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.shift_scan_progress, state.scannedCount, state.scanTotal),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (draft.refreshing) CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    val progress = if (state.scanTotal == 0) 1f else state.scannedCount / state.scanTotal.toFloat()
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.shift_scan_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                draft.feedback?.let { feedback ->
                    ScanFeedbackRow(
                        feedback = feedback,
                        onRetry = onRetryFailedScan,
                        onDismiss = onDismissFeedback,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (draft.refreshError != null) {
                    RetryRow(
                        message = stringResource(Res.string.shift_scan_refresh_failed),
                        onRetry = onRefresh,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                ManualEntrySection(
                    manualEntry = draft.manualEntry,
                    expanded = draft.manualExpanded || !qrScanningSupported,
                    forceExpanded = !qrScanningSupported,
                    onToggle = onToggleManualEntry,
                    onChange = onUpdateManualEntry,
                    onSubmit = onSubmitManualEntry,
                )

                Text(
                    text = pluralStringResource(
                        Res.plurals.shift_scan_remaining_count,
                        state.unscannedStops.size,
                        state.unscannedStops.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(state.unscannedStops, key = { it.id }) { step ->
                        RemainingPackageRow(state = state, step = step)
                    }
                }
            }
            }

            if (draft.submitting != null) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionDenied() {
    val openSettings = rememberOpenAppSettings()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.shift_scan_camera_permission),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = openSettings) {
                Text(stringResource(Res.string.action_open_settings))
            }
        }
    }
}

@Composable
private fun ScanFeedbackRow(
    feedback: ScanFeedback,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (message, isError) = when (feedback) {
        is ScanFeedback.Accepted -> stringResource(Res.string.shift_scan_accepted, feedback.trackingNumber) to false
        is ScanFeedback.AlreadyScanned -> stringResource(Res.string.shift_scan_already, feedback.trackingNumber) to false
        is ScanFeedback.NotOnThisShift -> stringResource(Res.string.shift_scan_not_on_shift, feedback.trackingNumber) to true
        is ScanFeedback.Unrecognised -> stringResource(Res.string.shift_scan_unrecognised) to true
        is ScanFeedback.NotReady -> stringResource(Res.string.shift_scan_not_ready) to true
        is ScanFeedback.Failed -> stringResource(Res.string.shift_scan_failed, feedback.trackingNumber) to true
    }
    val container = when {
        feedback is ScanFeedback.Accepted -> MaterialTheme.colorScheme.primaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = when {
        feedback is ScanFeedback.Accepted -> MaterialTheme.colorScheme.onPrimaryContainer
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (feedback is ScanFeedback.Accepted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = onContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = message, color = onContainer, style = MaterialTheme.typography.bodyMedium)
            }
            if (feedback is ScanFeedback.Failed) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.action_retry), tint = onContainer)
                }
            }
        }
    }
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
        }
    }
}

@Composable
private fun ManualEntrySection(
    manualEntry: String,
    expanded: Boolean,
    forceExpanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (!forceExpanded) {
            TextButton(onClick = onToggle) {
                Text(stringResource(Res.string.shift_scan_manual_title))
            }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualEntry,
                    onValueChange = onChange,
                    label = { Text(stringResource(Res.string.shift_scan_manual_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSubmit) { Text(stringResource(Res.string.shift_scan_manual_submit)) }
            }
        }
    }
}

@Composable
private fun RemainingPackageRow(state: ShiftDetailViewModel.UiState, step: RouteStep) {
    val recipient = state.recipientFor(step)?.name
    val tracking = state.trackingNumberFor(step)
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = recipient ?: stringResource(Res.string.shift_scan_remaining_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tracking != null) {
                Text(text = tracking, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
