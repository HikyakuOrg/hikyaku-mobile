package org.hikyaku.mobile.shift

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.jan.supabase.storage.StorageItem
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.hikyaku.mobile.geocode.model.RoutePoi
import org.hikyaku.mobile.geocode.model.RoutePoiKind
import org.hikyaku.mobile.map.mapLayersSupported
import org.hikyaku.mobile.share.rememberShareText
import org.hikyaku.mobile.shift.scan.ScanPackagesOverlay
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.hikyaku.mobile.util.combineDateAndTimeToIsoUtc
import org.hikyaku.mobile.util.epochMillisToDisplayDate
import org.hikyaku.mobile.util.formatHourMinute
import org.hikyaku.mobile.util.formatIsoAsDateTime
import org.hikyaku.mobile.util.isoDateTimeToHourMinute
import org.hikyaku.mobile.util.isoDateToEpochMillisUtc
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.action_cancel
import hikyaku.sharedui.generated.resources.action_continue
import hikyaku.sharedui.generated.resources.action_dismiss
import hikyaku.sharedui.generated.resources.action_not_now
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.action_open_settings
import hikyaku.sharedui.generated.resources.action_remove
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.action_share
import hikyaku.sharedui.generated.resources.tracking_share_text
import hikyaku.sharedui.generated.resources.create_shift_pick_date
import hikyaku.sharedui.generated.resources.cd_package_photo
import hikyaku.sharedui.generated.resources.shift_add_photo
import hikyaku.sharedui.generated.resources.shift_call_recipient
import hikyaku.sharedui.generated.resources.shift_add_stop
import hikyaku.sharedui.generated.resources.shift_add_stop_no_packages
import hikyaku.sharedui.generated.resources.shift_edit
import hikyaku.sharedui.generated.resources.shift_edit_done
import hikyaku.sharedui.generated.resources.shift_map_expand
import hikyaku.sharedui.generated.resources.shift_remove_stop
import hikyaku.sharedui.generated.resources.shift_remove_stop_confirm_message
import hikyaku.sharedui.generated.resources.shift_remove_stop_confirm_title
import hikyaku.sharedui.generated.resources.shift_reschedule_apply
import hikyaku.sharedui.generated.resources.shift_reschedule_title
import hikyaku.sharedui.generated.resources.shift_stop_locked
import hikyaku.sharedui.generated.resources.shift_all_delivered_banner
import hikyaku.sharedui.generated.resources.shift_complete_banner
import hikyaku.sharedui.generated.resources.shift_detail_title
import hikyaku.sharedui.generated.resources.shift_mark_delivered
import hikyaku.sharedui.generated.resources.shift_navigate
import hikyaku.sharedui.generated.resources.poi_fuel_station
import hikyaku.sharedui.generated.resources.poi_bicycle_parking
import hikyaku.sharedui.generated.resources.shift_no_packages
import hikyaku.sharedui.generated.resources.shift_no_routes
import hikyaku.sharedui.generated.resources.shift_packages_count
import hikyaku.sharedui.generated.resources.shift_permission_required_message
import hikyaku.sharedui.generated.resources.shift_photo_added
import hikyaku.sharedui.generated.resources.shift_route_label
import hikyaku.sharedui.generated.resources.shift_scan_gate_banner
import hikyaku.sharedui.generated.resources.shift_scan_packages_button
import hikyaku.sharedui.generated.resources.shift_start_button
import hikyaku.sharedui.generated.resources.shift_start_permission_footnote
import hikyaku.sharedui.generated.resources.shift_start_permission_intro
import hikyaku.sharedui.generated.resources.shift_start_permission_location_reason
import hikyaku.sharedui.generated.resources.shift_start_permission_notifications_reason
import hikyaku.sharedui.generated.resources.shift_start_permission_title
import hikyaku.sharedui.generated.resources.shift_trip_details_title
import hikyaku.sharedui.generated.resources.shift_trip_distance
import hikyaku.sharedui.generated.resources.shift_trip_duration
import hikyaku.sharedui.generated.resources.shift_trip_start
import hikyaku.sharedui.generated.resources.shift_trip_vehicle
import hikyaku.sharedui.generated.resources.shift_unknown_recipient
import org.hikyaku.mobile.shift.detail.model.Customer
import org.hikyaku.mobile.shift.detail.model.PackageAssignment
import org.hikyaku.mobile.shift.detail.model.PackageInfo
import org.hikyaku.mobile.shift.detail.model.RouteStep
import org.hikyaku.mobile.shift.detail.model.VrpRoute
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.FeaturesClickHandler
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.sin

private const val MAP_STYLE_URL = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

/** Outbound leg + stop markers (deep blue), return leg (amber), and depot marker (green). */
private val ROUTE_OUTBOUND_COLOR = Color(0xFF19398D)
private val ROUTE_RETURN_COLOR = Color(0xFFE8833A)
private val DEPOT_COLOR = Color(0xFF2E7D32)

/** Route-POI markers, kept distinct from the route/stop, return, and depot colours: fuel stations
 *  (purple) for motor vehicles, bicycle parking (teal) for bicycles. */
private val FUEL_COLOR = Color(0xFF6A1B9A)
private val BICYCLE_PARKING_COLOR = Color(0xFF00796B)

/** Number of full brightness cycles along a route leg's length, for [flowGradient]. */
private const val FLOW_WAVE_CYCLES = 3f
private const val FLOW_STOP_COUNT = 48

/**
 * A "flow" gradient along a line's length that reads like current moving through a wire: the line
 * stays fully, continuously coloured (no dashes or gaps) while a brighter pulse travels from the
 * line's start toward its end as [phase] advances from 0 to 1. [phase] is expected to loop
 * 0f -> 1f -> 0f (restart, not reverse) — because [FLOW_WAVE_CYCLES] is a whole number, the
 * pattern at phase 1 exactly matches phase 0, so the restart is seamless.
 */
private fun flowGradient(baseColor: Color, phase: Float): Expression<ColorValue> {
    val highlightColor = lerp(baseColor, Color.White, 0.55f)
    val stops = (0..FLOW_STOP_COUNT).map { i ->
        val x = i / FLOW_STOP_COUNT.toFloat()
        val wave = (sin(2f * PI.toFloat() * (FLOW_WAVE_CYCLES * x - phase)) + 1f) / 2f
        val brightness = wave * wave * wave * wave
        x to const(lerp(baseColor, highlightColor, brightness))
    }
    return interpolate(linear(), feature.lineProgress(), *stops.toTypedArray())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDetailScreen(
    viewModel: ShiftDetailViewModel,
    onBack: () -> Unit,
    onPackageClick: (String) -> Unit,
    onVehicleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShiftDetailScreenContent(
        state = state,
        orgName = viewModel.orgName,
        onBack = onBack,
        onPackageClick = onPackageClick,
        onVehicleClick = onVehicleClick,
        trackingUrlFor = viewModel::trackingUrlFor,
        onToggleEditMode = viewModel::toggleEditMode,
        onLoadRoutes = viewModel::loadRoutes,
        onSelectRoute = viewModel::selectRoute,
        onReschedule = viewModel::reschedule,
        onClearEditError = viewModel::clearEditError,
        onClearActionError = viewModel::clearActionError,
        onRemoveStop = viewModel::removeStop,
        onMarkDelivered = viewModel::markDelivered,
        onOpenAddStop = viewModel::openAddStop,
        onCloseAddStop = viewModel::closeAddStop,
        onSelectAddStopPackage = viewModel::selectAddStopPackage,
        onConfirmAddStop = viewModel::confirmAddStop,
        onStartShift = viewModel::startShift,
        onArmAutoStart = viewModel::armAutoStart,
        onOpenScanner = viewModel::openScanner,
        onCloseScanner = viewModel::closeScanner,
        onToggleFlashlight = viewModel::toggleFlashlight,
        onQrScanned = viewModel::onQrScanned,
        onUpdateManualEntry = viewModel::updateManualEntry,
        onToggleManualEntry = viewModel::toggleManualEntry,
        onSubmitManualEntry = viewModel::submitManualEntry,
        onRetryFailedScan = viewModel::retryFailedScan,
        onDismissScanFeedback = viewModel::dismissScanFeedback,
        onRefreshScanStatuses = { viewModel.refreshScanStatuses() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftDetailScreenContent(
    state: ShiftDetailViewModel.UiState,
    orgName: String,
    onBack: () -> Unit,
    onPackageClick: (String) -> Unit,
    onVehicleClick: (String) -> Unit,
    trackingUrlFor: (RouteStep) -> String?,
    onToggleEditMode: () -> Unit,
    onLoadRoutes: () -> Unit,
    onSelectRoute: (String) -> Unit,
    onReschedule: (String) -> Unit,
    onClearEditError: () -> Unit,
    onClearActionError: () -> Unit,
    onRemoveStop: (RouteStep) -> Unit,
    onMarkDelivered: (packageId: String, photoBytes: ByteArray?) -> Unit,
    onOpenAddStop: () -> Unit,
    onCloseAddStop: () -> Unit,
    onSelectAddStopPackage: (String) -> Unit,
    onConfirmAddStop: () -> Unit,
    onStartShift: () -> Unit,
    onArmAutoStart: () -> Unit,
    onOpenScanner: () -> Unit,
    onCloseScanner: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onQrScanned: (String) -> Unit,
    onUpdateManualEntry: (String) -> Unit,
    onToggleManualEntry: () -> Unit,
    onSubmitManualEntry: () -> Unit,
    onRetryFailedScan: () -> Unit,
    onDismissScanFeedback: () -> Unit,
    onRefreshScanStatuses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the full-screen (interactive) map is shown.
    var showFullMap by remember { mutableStateOf(false) }

    // Photo captured for the stop currently in transit; cleared when the stop changes.
    var capturedPhoto by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(state.inTransitPackageId) { capturedPhoto = null }

    val capturePhoto = rememberPhotoCapture { bytes -> if (bytes != null) capturedPhoto = bytes }
    // Background tracking is required, so a shift can only start once "Allow all the time" location
    // (and notifications) are granted; otherwise we surface a banner pointing the user to settings.
    var permissionDenied by remember { mutableStateOf(false) }
    val openSettings = rememberOpenAppSettings()
    val requestShiftPermissions = rememberShiftPermissions { granted ->
        permissionDenied = !granted
        if (granted) onStartShift()
    }

    // Arm the auto-start safety net silently whenever the shift is eligible (scheduled & within
    // window) and the watcher's permissions are already granted. armAutoStart() is idempotent, so
    // re-running this effect on state changes won't reset accumulated detection flags.
    val hasTrackingPermissions = rememberHasShiftTrackingPermissions()

    // Requesting background location cold (no explanation) reads as suspicious and invites a denial,
    // so when permissions aren't already granted, "Start shift" first shows why they're needed; only
    // then does it trigger the real OS prompts via requestShiftPermissions.
    var showPermissionRationale by remember { mutableStateOf(false) }
    val startShift: () -> Unit = {
        if (hasTrackingPermissions) requestShiftPermissions() else showPermissionRationale = true
    }
    LaunchedEffect(state.autoStartEligible, state.shiftStarted, state.selectedRouteId, hasTrackingPermissions) {
        if (state.autoStartEligible && !state.shiftStarted && hasTrackingPermissions) {
            onArmAutoStart()
        }
    }

    // The primary action lives in a pinned bottom bar; it only applies once a route with stops is
    // loaded and the shift hasn't started yet.
    val canStartShift = !state.isLoadingRoutes && state.routesError == null &&
        state.routes.isNotEmpty() && !state.shiftStarted && !state.isLoadingRoute &&
        state.jobStops.isNotEmpty() && !state.allPackagesDelivered

    // Editing (reschedule / add / remove stops) is only offered before the shift starts.
    val canEdit = !state.isLoadingRoutes && state.routesError == null &&
        state.routes.isNotEmpty() && !state.shiftStarted && !state.isLoadingRoute &&
        !state.allPackagesDelivered

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.shift_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (canEdit) {
                        TextButton(onClick = onToggleEditMode, enabled = !state.isEditing) {
                            Text(
                                stringResource(
                                    if (state.editMode) Res.string.shift_edit_done else Res.string.shift_edit,
                                ),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (canStartShift && !state.editMode) {
                Surface(shadowElevation = 8.dp) {
                    if (state.allPackagesScanned) {
                        Button(
                            onClick = startShift,
                            enabled = !state.isActionInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(16.dp),
                        ) { Text(stringResource(Res.string.shift_start_button)) }
                    } else {
                        Button(
                            onClick = onOpenScanner,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(16.dp),
                        ) {
                            Text(
                                stringResource(
                                    Res.string.shift_scan_packages_button,
                                    state.scannedCount,
                                    state.scanTotal,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoadingRoutes -> CenteredSpinner(Modifier.fillMaxSize().padding(padding))

            state.routesError != null -> RetryCard(
                message = state.routesError!!,
                onRetry = onLoadRoutes,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            )

            state.routes.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(Res.string.shift_no_routes)) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.routes.size > 1) {
                    item { RouteSelector(state.routes, state.selectedRouteId, onSelectRoute) }
                }

                item { RouteMap(state, onExpand = { showFullMap = true }) }

                item { TripDetailsCard(state, onVehicleClick) }

                if (canStartShift && !state.allPackagesScanned) {
                    item { InfoBanner(stringResource(Res.string.shift_scan_gate_banner)) }
                }

                if (state.editMode) {
                    item { RescheduleCard(state, onReschedule) }
                }

                state.editError?.let { error ->
                    item {
                        RetryCard(
                            message = error,
                            onRetry = onClearEditError,
                            retryLabel = stringResource(Res.string.action_dismiss),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }

                if (state.shiftComplete) {
                    item { InfoBanner(stringResource(Res.string.shift_complete_banner), success = true) }
                } else if (state.deliveriesComplete) {
                    item { InfoBanner(stringResource(Res.string.shift_all_delivered_banner)) }
                }

                state.actionError?.let { error ->
                    item {
                        RetryCard(
                            message = error,
                            onRetry = onClearActionError,
                            retryLabel = stringResource(Res.string.action_dismiss),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }

                if (permissionDenied && !state.shiftStarted) {
                    item {
                        RetryCard(
                            message = stringResource(Res.string.shift_permission_required_message),
                            onRetry = openSettings,
                            retryLabel = stringResource(Res.string.action_open_settings),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }

                if (state.isLoadingRoute) {
                    item { CenteredSpinner(Modifier.fillMaxWidth().padding(24.dp)) }
                } else if (state.routeError != null) {
                    item {
                        RetryCard(
                            message = state.routeError!!,
                            onRetry = { state.selectedRouteId?.let(onSelectRoute) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    val stops = state.jobStops
                    if (stops.isEmpty() && !state.editMode) {
                        item {
                            Text(
                                stringResource(Res.string.shift_no_packages),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        if (stops.isNotEmpty()) {
                            item {
                                Text(
                                    text = pluralStringResource(Res.plurals.shift_packages_count, stops.size, stops.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        items(stops, key = { it.id }) { step ->
                            val packageId = step.assignment?.packageId
                            val trackingUrl = trackingUrlFor(step)
                            val shareMessage = trackingUrl?.let {
                                stringResource(Res.string.tracking_share_text, orgName, it)
                            }
                            PackageCard(
                                step = step,
                                recipient = state.recipientFor(step),
                                status = state.displayStatusFor(step),
                                images = packageId?.let { state.images[it] } ?: emptyList(),
                                isInTransit = packageId != null && packageId == state.inTransitPackageId,
                                isCurrent = step.id == state.currentJobStepId,
                                showNavigate = state.shiftStarted || state.allPackagesScanned,
                                actionEnabled = !state.isActionInProgress,
                                hasPhoto = capturedPhoto != null,
                                editMode = state.editMode,
                                locked = state.isStopLocked(step),
                                removeEnabled = !state.isEditing,
                                onRemove = { onRemoveStop(step) },
                                onAddPhoto = capturePhoto,
                                shareMessage = shareMessage,
                                onClick = state.trackingNumberFor(step)?.let { tracking ->
                                    { onPackageClick(tracking) }
                                },
                                onMarkDelivered = {
                                    if (packageId != null) {
                                        onMarkDelivered(packageId, capturedPhoto)
                                        capturedPhoto = null
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (state.editMode) {
                            item {
                                OutlinedButton(
                                    onClick = onOpenAddStop,
                                    enabled = !state.isEditing,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(Res.string.shift_add_stop))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullMap) {
        FullScreenRouteMap(state = state, onDismiss = { showFullMap = false })
    }

    if (showPermissionRationale) {
        StartShiftPermissionRationaleDialog(
            onContinue = {
                showPermissionRationale = false
                requestShiftPermissions()
            },
            onDismiss = { showPermissionRationale = false },
        )
    }

    state.addStop?.let { draft ->
        AddStopSheet(
            draft = draft,
            onDismiss = onCloseAddStop,
            onSelectPackage = onSelectAddStopPackage,
            onConfirm = onConfirmAddStop,
        )
    }

    if (state.scan != null) {
        ScanPackagesOverlay(
            state = state,
            onClose = onCloseScanner,
            onToggleFlashlight = onToggleFlashlight,
            onQrScanned = onQrScanned,
            onUpdateManualEntry = onUpdateManualEntry,
            onToggleManualEntry = onToggleManualEntry,
            onSubmitManualEntry = onSubmitManualEntry,
            onRetryFailedScan = onRetryFailedScan,
            onDismissFeedback = onDismissScanFeedback,
            onRefresh = onRefreshScanStatuses,
        )
    }
}

@Preview
@Composable
private fun ShiftDetailScreenPreview() {
    HikyakuTheme {
        ShiftDetailScreenContent(
            state = ShiftDetailViewModel.UiState(
                isLoadingRoutes = false,
                routes = listOf(VrpRoute(id = "route-1", duration = 5400, cost = 1200)),
                selectedRouteId = "route-1",
                steps = listOf(
                    RouteStep(
                        id = 1,
                        stepIndex = 1,
                        type = "job",
                        location = Point(longitude = 103.8318, latitude = 1.3048),
                        assignment = PackageAssignment(
                            packageId = "p1",
                            packageInfo = PackageInfo(
                                currentStatus = "PENDING",
                                toCustomer = Customer(
                                    name = "Jane Tan",
                                    phone = "+6591234567",
                                    address = "45 River Valley Road, Singapore",
                                    suburb = "River Valley",
                                ),
                            ),
                        ),
                    ),
                    RouteStep(
                        id = 2,
                        stepIndex = 2,
                        type = "job",
                        location = Point(longitude = 103.8450, latitude = 1.3100),
                        assignment = PackageAssignment(
                            packageId = "p2",
                            packageInfo = PackageInfo(
                                currentStatus = "IN_TRANSIT",
                                toCustomer = Customer(
                                    name = "Wei Ming Lee",
                                    phone = "+6598765432",
                                    address = "88 Tampines Ave 4, Singapore",
                                    suburb = "Tampines",
                                ),
                            ),
                        ),
                    ),
                ),
                trackingNumbers = mapOf("p1" to "HKY-00123", "p2" to "HKY-00124"),
                inTransitPackageId = "p2",
                distanceMeters = 12500.0,
                durationSeconds = 5400.0,
                vehicleLabel = "Van",
            ),
            orgName = "Acme Logistics",
            onBack = {},
            onPackageClick = {},
            onVehicleClick = {},
            trackingUrlFor = { null },
            onToggleEditMode = {},
            onLoadRoutes = {},
            onSelectRoute = {},
            onReschedule = {},
            onClearEditError = {},
            onClearActionError = {},
            onRemoveStop = {},
            onMarkDelivered = { _, _ -> },
            onOpenAddStop = {},
            onCloseAddStop = {},
            onSelectAddStopPackage = {},
            onConfirmAddStop = {},
            onStartShift = {},
            onArmAutoStart = {},
            onOpenScanner = {},
            onCloseScanner = {},
            onToggleFlashlight = {},
            onQrScanned = {},
            onUpdateManualEntry = {},
            onToggleManualEntry = {},
            onSubmitManualEntry = {},
            onRetryFailedScan = {},
            onDismissScanFeedback = {},
            onRefreshScanStatuses = {},
        )
    }
}

@Composable
private fun RouteSelector(
    routes: List<VrpRoute>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(routes, key = { it.id }) { route ->
            val index = routes.indexOf(route) + 1
            FilterChip(
                selected = route.id == selectedId,
                onClick = { onSelect(route.id) },
                label = { Text(stringResource(Res.string.shift_route_label, index)) },
            )
        }
    }
}

/**
 * The embedded route map: static (gestures disabled) with a "tap to expand" hint. Tapping it opens
 * the full-screen, interactive map via [onExpand].
 */
@Composable
private fun RouteMap(state: ShiftDetailViewModel.UiState, onExpand: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(280.dp)) {
        RouteMapView(state = state, gesturesEnabled = false, modifier = Modifier.fillMaxSize())
        // A transparent overlay captures the tap (the underlying map ignores it while gestures are
        // disabled) and shows an affordance that the map can be expanded.
        Box(
            Modifier.matchParentSize().clickable(onClick = onExpand),
            contentAlignment = Alignment.TopEnd,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(12.dp),
            ) {
                Icon(
                    imageVector = FullscreenIcon,
                    contentDescription = stringResource(Res.string.shift_map_expand),
                    modifier = Modifier.padding(6.dp).size(20.dp),
                )
            }
        }
    }
}

private const val FULLSCREEN_ICON_PATH =
    "M7,14H5v5h5v-2H7v-3z" +
        "M5,10h2V7h3V5H5v5z" +
        "M17,17h-3v2h5v-5h-2v3z" +
        "M14,5v2h3v3h2V5h-5z"

private var fullscreenIconCache: ImageVector? = null

/** The Material "Fullscreen" glyph, defined inline since Compose Multiplatform doesn't ship material-icons-extended. */
private val FullscreenIcon: ImageVector
    get() = fullscreenIconCache ?: ImageVector.Builder(
        name = "Fullscreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(FULLSCREEN_ICON_PATH),
        fill = SolidColor(Color.Black),
    ).build().also { fullscreenIconCache = it }

private const val FUEL_ICON_PATH =
    "m19.77 7.23.01-.01-3.72-3.72L15 4.56l2.11 2.11c-.94.36-1.61 1.26-1.61 2.33 0 1.38 " +
        "1.12 2.5 2.5 2.5.36 0 .69-.08 1-.21v7.21c0 .55-.45 1-1 1s-1-.45-1-1V14c0-1.1-.9-2-2-2h-1V5c0-1.1-.9-2-2-2H6c-1.1 " +
        "0-2 .9-2 2v16h10v-7.5h1.5v5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V9c0-.69-.28-1.32-.73-1.77zM12 " +
        "10H6V5h6v5zm6 0c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z"

private var fuelIconCache: ImageVector? = null

/** The Material "local_gas_station" glyph, defined inline since Compose Multiplatform doesn't ship material-icons-extended. */
private val FuelIcon: ImageVector
    get() = fuelIconCache ?: ImageVector.Builder(
        name = "LocalGasStation",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(FUEL_ICON_PATH),
        fill = SolidColor(Color.Black),
    ).build().also { fuelIconCache = it }

private const val BICYCLE_ICON_PATH =
    "M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 " +
        "5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm5.8-10l2.4-2.4.8.8c1.3 " +
        "1.3 3 2.1 5.1 2.1V9c-1.5 0-2.7-.6-3.6-1.5l-1.9-1.9c-.5-.4-1-.6-1.6-.6s-1.1.2-1.4.6L7.8 8.4c-.4.4-.6.9-.6 1.4 0 .6.2 " +
        "1.1.6 1.4L11 14v5h2v-6.2l-2.2-2.3zM19 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 " +
        "3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5z"

private var bicycleIconCache: ImageVector? = null

/** The Material "directions_bike" glyph, used to mark bicycle parking (Compose Multiplatform doesn't ship material-icons-extended). */
private val BicycleIcon: ImageVector
    get() = bicycleIconCache ?: ImageVector.Builder(
        name = "DirectionsBike",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(BICYCLE_ICON_PATH),
        fill = SolidColor(Color.Black),
    ).build().also { bicycleIconCache = it }

private var navigationIconCache: ImageVector? = null

/** The Material Symbols "assistant_navigation" (outlined) glyph, defined inline since Compose Multiplatform doesn't ship material-icons-extended. */
private val NavigationIcon: ImageVector
    get() = navigationIconCache ?: ImageVector.Builder(
        name = "AssistantNavigation",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(7.98f, 17f)
        lineTo(12f, 15.18f)
        lineTo(16.03f, 17f)
        lineTo(16.4f, 16.63f)
        lineTo(12f, 6f)
        lineTo(7.6f, 16.63f)
        lineTo(7.98f, 17f)
        close()
        moveTo(12f, 22f)
        quadTo(9.93f, 22f, 8.1f, 21.21f)
        quadTo(6.28f, 20.43f, 4.93f, 19.08f)
        quadTo(3.58f, 17.73f, 2.79f, 15.9f)
        reflectiveQuadTo(2f, 12f)
        quadTo(2f, 9.92f, 2.79f, 8.1f)
        quadTo(3.58f, 6.27f, 4.93f, 4.93f)
        quadTo(6.28f, 3.57f, 8.1f, 2.79f)
        quadTo(9.93f, 2f, 12f, 2f)
        reflectiveQuadToRelative(3.9f, 0.79f)
        reflectiveQuadToRelative(3.17f, 2.14f)
        quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
        quadTo(22f, 9.92f, 22f, 12f)
        reflectiveQuadToRelative(-0.79f, 3.9f)
        reflectiveQuadToRelative(-2.14f, 3.17f)
        quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
        reflectiveQuadTo(12f, 22f)
        close()
        moveToRelative(0f, -2f)
        quadToRelative(3.35f, 0f, 5.68f, -2.32f)
        reflectiveQuadTo(20f, 12f)
        reflectiveQuadTo(17.68f, 6.32f)
        reflectiveQuadTo(12f, 4f)
        reflectiveQuadTo(6.33f, 6.32f)
        reflectiveQuadTo(4f, 12f)
        reflectiveQuadToRelative(2.33f, 5.68f)
        reflectiveQuadTo(12f, 20f)
        close()
        moveToRelative(0f, -8f)
        close()
    }.build().also { navigationIconCache = it }

/**
 * Builds a universal Google Maps directions URL for [location] (or, failing that, [address]),
 * which the OS resolves to the user's preferred navigation app. Returns null if neither is usable.
 */
private fun navigationUrl(location: Point?, address: String): String? {
    val lat = location?.latitude
    val lng = location?.longitude
    return when {
        lat != null && lng != null -> "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"
        address.isNotBlank() -> "https://www.google.com/maps/dir/?api=1&destination=${encodeUriComponent(address)}"
        else -> null
    }
}

/** Splits a comma-separated address string (street, suburb, postcode, ...) onto separate lines. */
private fun formatAddressMultiline(address: String): String =
    address.split(',').map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

/** Minimal percent-encoding for a URL query component (RFC 3986 unreserved characters pass through unescaped). */
private fun encodeUriComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        ) {
            append(c.toChar())
        } else {
            append('%')
            append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/**
 * Explains, before the OS permission prompts fire, why starting a shift needs background location
 * and notifications. Shown once per "Start shift" tap while permissions aren't yet granted, so the
 * driver isn't hit with an unexplained "Allow all the time" dialog. [onContinue] proceeds into the
 * real (chained) OS permission requests; [onDismiss] backs out without requesting anything.
 */
@Composable
private fun StartShiftPermissionRationaleDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Place, contentDescription = null) },
        title = { Text(stringResource(Res.string.shift_start_permission_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.shift_start_permission_intro))
                PermissionReasonRow(Icons.Filled.Place, stringResource(Res.string.shift_start_permission_location_reason))
                PermissionReasonRow(Icons.Filled.Notifications, stringResource(Res.string.shift_start_permission_notifications_reason))
                Text(
                    text = stringResource(Res.string.shift_start_permission_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(Res.string.action_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_not_now)) } },
    )
}

/** A single icon + explanation line inside [StartShiftPermissionRationaleDialog]. */
@Composable
private fun PermissionReasonRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The full-screen, interactive (pan/zoom) route map, shown over the detail screen. */
@Composable
private fun FullScreenRouteMap(state: ShiftDetailViewModel.UiState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            RouteMapView(state = state, gesturesEnabled = true, modifier = Modifier.fillMaxSize())
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = CircleShape,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_dismiss))
                }
            }
        }
    }
}

/**
 * Renders the route (two-coloured outbound/return line, numbered stop markers, depot marker) on a
 * MapLibre map. [gesturesEnabled] toggles pan/zoom so the same rendering serves both the static
 * embedded preview and the interactive full-screen view.
 */
@Composable
private fun RouteMapView(
    state: ShiftDetailViewModel.UiState,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Centre on the first stop we have coordinates for.
    val firstStop = state.steps.firstNotNullOfOrNull { step ->
        val lng = step.location?.longitude
        val lat = step.location?.latitude
        if (lng != null && lat != null) Position(longitude = lng, latitude = lat) else null
    }
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = firstStop ?: Position(longitude = 0.0, latitude = 0.0),
            zoom = if (firstStop != null) 11.0 else 1.0,
        ),
    )

    // Once the route line is known, zoom/pan the camera so the whole line fits on screen.
    val routeLine = state.routeLine
    LaunchedEffect(routeLine) {
        if (routeLine.size >= 2) {
            val longitudes = routeLine.map { it[0] }
            val latitudes = routeLine.map { it[1] }
            val bounds = BoundingBox(
                southwest = Position(longitude = longitudes.min(), latitude = latitudes.min()),
                northeast = Position(longitude = longitudes.max(), latitude = latitudes.max()),
            )
            cameraState.animateTo(bounds, padding = PaddingValues(32.dp))
        }
    }

    // Split the polyline into the outbound leg (depot → last stop) and the return leg (last stop →
    // depot) so they can be drawn in distinct colours. Waypoint indices (when the road-snapped
    // preview provides them) mark each stop within the line; otherwise the straight-line fallback
    // ends with the return segment, so split at the second-to-last point.
    val line = state.routeLine
    val splitIndex = when {
        state.routeWayPoints.size >= 2 -> state.routeWayPoints[state.routeWayPoints.size - 2]
        line.size >= 2 -> line.size - 2
        else -> -1
    }
    val outboundLine = if (splitIndex >= 1) line.subList(0, splitIndex + 1) else line
    val returnLine = if (splitIndex in 0 until line.size - 1) line.subList(splitIndex, line.size) else emptyList()

    val depotPosition = (state.steps.firstOrNull { it.type.equals("start", ignoreCase = true) }
        ?: state.steps.firstOrNull { it.type.equals("end", ignoreCase = true) })
        ?.location
        ?.let { Position(longitude = it.longitude, latitude = it.latitude) }

    // Marker for the route's POIs, chosen by category: a fuel pump for motor vehicles, a bicycle
    // for bicycle parking. Rasterised once and recoloured white via an SDF icon.
    val poiColor = when (state.poiKind) {
        RoutePoiKind.Fuel -> FUEL_COLOR
        RoutePoiKind.BicycleParking -> BICYCLE_PARKING_COLOR
    }
    val poiIconPainter = rememberVectorPainter(
        when (state.poiKind) {
            RoutePoiKind.Fuel -> FuelIcon
            RoutePoiKind.BicycleParking -> BicycleIcon
        },
    )
    val poiFallbackName = stringResource(
        when (state.poiKind) {
            RoutePoiKind.Fuel -> Res.string.poi_fuel_station
            RoutePoiKind.BicycleParking -> Res.string.poi_bicycle_parking
        },
    )

    // The POI whose popup (name / address / navigate) is open; cleared when the route changes.
    var selectedPoi by remember { mutableStateOf<RoutePoi?>(null) }
    LaunchedEffect(state.selectedRouteId) { selectedPoi = null }

    // Drives the travelling highlight on the route line (see [flowGradient]): loops 0f -> 1f,
    // restarting (not reversing) each cycle so the seamless wave pattern doesn't visibly jump.
    val flowPhase by rememberInfiniteTransition(label = "route-flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "route-flow-phase",
    )

    Box(modifier) {
        MaplibreMap(
            modifier = Modifier.matchParentSize(),
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = cameraState,
            options = if (gesturesEnabled) {
                MapOptions(ornamentOptions = routeMapOrnamentOptions())
            } else {
                MapOptions(
                    gestureOptions = GestureOptions.AllDisabled,
                    ornamentOptions = routeMapOrnamentOptions(),
                )
            },
        ) {
            // Desktop MapLibre Compose can't render sources/layers yet; show the base map only there.
            if (mapLayersSupported) {
            if (returnLine.size >= 2) {
                val returnFeature = Feature(
                    LineString(returnLine.map { Position(longitude = it[0], latitude = it[1]) }),
                    properties = null,
                )
                val returnSource = rememberGeoJsonSource(
                    GeoJsonData.Features(returnFeature),
                    options = GeoJsonOptions(lineMetrics = true),
                )
                LineLayer(
                    id = "route-line-return",
                    source = returnSource,
                    gradient = flowGradient(ROUTE_RETURN_COLOR, flowPhase),
                    width = const(4.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
            if (outboundLine.size >= 2) {
                val outboundFeature = Feature(
                    LineString(outboundLine.map { Position(longitude = it[0], latitude = it[1]) }),
                    properties = null,
                )
                val outboundSource = rememberGeoJsonSource(
                    GeoJsonData.Features(outboundFeature),
                    options = GeoJsonOptions(lineMetrics = true),
                )
                LineLayer(
                    id = "route-line-outbound",
                    source = outboundSource,
                    gradient = flowGradient(ROUTE_OUTBOUND_COLOR, flowPhase),
                    width = const(4.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }

            // Route POIs (fuel stations, or bicycle parking for bikes). Drawn under the delivery-stop
            // markers so the numbered stops stay on top where they overlap.
            val poiFeatures = state.routePois.map { poi ->
                Feature(
                    Point(Position(longitude = poi.lon, latitude = poi.lat)),
                    properties = buildJsonObject { put("id", poi.id) },
                )
            }
            if (poiFeatures.isNotEmpty()) {
                val poiSource = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(poiFeatures)))
                // Tapping a marker opens its popup. The tap can land on the icon (top) or the circle
                // (below), so both carry the handler that resolves the feature's id back to a POI.
                val onPoiClick: FeaturesClickHandler = { features ->
                    val id = features.firstNotNullOfOrNull { it.properties?.get("id")?.jsonPrimitive?.contentOrNull }
                    val poi = id?.let { pid -> state.routePois.firstOrNull { it.id == pid } }
                    if (poi != null) {
                        selectedPoi = poi
                        ClickResult.Consume
                    } else {
                        ClickResult.Pass
                    }
                }
                CircleLayer(
                    id = "route-poi",
                    source = poiSource,
                    color = const(poiColor),
                    radius = const(11.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                    onClick = onPoiClick,
                )
                SymbolLayer(
                    id = "route-poi-icons",
                    source = poiSource,
                    iconImage = image(poiIconPainter, size = DpSize(16.dp, 16.dp), drawAsSdf = true),
                    iconColor = const(Color.White),
                    iconAllowOverlap = const(true),
                    onClick = onPoiClick,
                )
            }

            // Numbered markers for each delivery stop.
            val stopFeatures = state.jobStops.mapNotNull { step ->
                val lng = step.location?.longitude ?: return@mapNotNull null
                val lat = step.location?.latitude ?: return@mapNotNull null
                Feature(
                    Point(Position(longitude = lng, latitude = lat)),
                    properties = buildJsonObject { put("label", step.stepIndex.toString()) },
                )
            }
            if (stopFeatures.isNotEmpty()) {
                val stopSource = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(stopFeatures)))
                CircleLayer(
                    id = "route-stops",
                    source = stopSource,
                    color = const(ROUTE_OUTBOUND_COLOR),
                    radius = const(11.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
                SymbolLayer(
                    id = "route-stops-labels",
                    source = stopSource,
                    textField = format(span(feature["label"].asString())),
                    textColor = const(Color.White),
                    textSize = const(12.sp),
                )
            }

            // Distinct marker for the start/return depot.
            if (depotPosition != null) {
                val depotSource = rememberGeoJsonSource(
                    GeoJsonData.Features(Feature(Point(depotPosition), properties = null)),
                )
                CircleLayer(
                    id = "route-depot",
                    source = depotSource,
                    color = const(DEPOT_COLOR),
                    radius = const(9.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(3.dp),
                )
            }

            // The driver's own position, once the shift is running.
            if (state.shiftStarted) {
                val locationProvider = rememberShiftLocationProvider()
                val userLocationState = rememberUserLocationState(locationProvider)
                LocationPuck(
                    idPrefix = "current-location",
                    location = userLocationState.location,
                    cameraState = cameraState,
                )
            }
            }
        }

        selectedPoi?.let { poi ->
            PoiPopup(
                poi = poi,
                fallbackName = poiFallbackName,
                onDismiss = { selectedPoi = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

/** A tap popup for a route POI: its name, address, and a Navigate button (as in the stop cards). */
@Composable
private fun PoiPopup(
    poi: RoutePoi,
    fallbackName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val point = Point(Position(longitude = poi.lon, latitude = poi.lat))
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = poi.name ?: fallbackName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_dismiss))
                }
            }
            poi.address?.takeIf { it.isNotBlank() }?.let { address ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatAddressMultiline(address),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            navigationUrl(point, poi.address.orEmpty())?.let { url ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                    Icon(NavigationIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.shift_navigate))
                }
            }
        }
    }
}

@Composable
private fun TripDetailsCard(state: ShiftDetailViewModel.UiState, onVehicleClick: (String) -> Unit) {
    val rows = buildList {
        state.startDateTime?.let { add(Res.string.shift_trip_start to formatIsoAsDateTime(it)) }
        state.durationSeconds?.takeIf { it > 0 }?.let { add(Res.string.shift_trip_duration to formatDuration(it)) }
        state.distanceMeters?.takeIf { it > 0 }?.let { add(Res.string.shift_trip_distance to formatDistance(it)) }
    }
    val vehicleId = state.vehicleId
    // "Type • Plate", omitting whichever part is missing (e.g. an ad-hoc shift has a type but no
    // plate; a package-backed shift usually has both).
    val vehicleText = listOfNotNull(
        state.vehicleLabel?.takeIf { it.isNotBlank() },
        state.vehiclePlate?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")
    if (rows.isEmpty() && vehicleText.isBlank()) return

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.shift_trip_details_title),
                style = MaterialTheme.typography.titleSmall,
            )
            rows.forEach { (labelRes, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (vehicleText.isNotBlank()) {
                Row(
                    modifier = if (vehicleId != null) {
                        Modifier.fillMaxWidth().clickable { onVehicleClick(vehicleId) }
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.shift_trip_vehicle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = vehicleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (vehicleId != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

/** Formats a duration in seconds as `H h M min` (or `M min` under an hour). */
private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

/** Formats a distance in metres as kilometres (1 dp) once ≥ 1 km, else whole metres. */
private fun formatDistance(meters: Double): String {
    if (meters < 1000) return "${meters.toLong()} m"
    val km = (meters / 100).toLong() / 10.0
    return "$km km"
}

/** Edit-mode card to change the shift's start date/time. Seeds its pickers from the current start. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleCard(state: ShiftDetailViewModel.UiState, onReschedule: (String) -> Unit) {
    val iso = state.startDateTime
    val seedMillis = remember(iso) { iso?.let { isoDateToEpochMillisUtc(it) } }
    val seedHourMinute = remember(iso) { iso?.let { isoDateTimeToHourMinute(it) } ?: (8 to 0) }
    var dateMillis by remember(iso) { mutableStateOf(seedMillis) }
    var hour by remember(iso) { mutableStateOf(seedHourMinute.first) }
    var minute by remember(iso) { mutableStateOf(seedHourMinute.second) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.shift_reschedule_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeField(
                    text = dateMillis?.let { epochMillisToDisplayDate(it) }
                        ?: stringResource(Res.string.create_shift_pick_date),
                    onClick = { showDate = true },
                    modifier = Modifier.weight(1f),
                )
                DateTimeField(text = formatHourMinute(hour, minute), onClick = { showTime = true })
            }
            Button(
                onClick = {
                    val d = dateMillis ?: return@Button
                    onReschedule(combineDateAndTimeToIsoUtc(d, hour, minute))
                },
                enabled = !state.isEditing && dateMillis != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isEditing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.shift_reschedule_apply))
                }
            }
        }
    }
    if (showDate) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { dateMillis = dp.selectedDateMillis ?: dateMillis; showDate = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(Res.string.action_cancel)) } },
        ) { DatePicker(state = dp) }
    }
    if (showTime) {
        StopTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m -> hour = h; minute = m; showTime = false },
            onDismiss = { showTime = false },
        )
    }
}

/** The add-stop bottom sheet: reuses the create-shift recipient picker (name, phone, address). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStopSheet(
    draft: AddStopDraft,
    onDismiss: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ToastEffect(draft.error)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.shift_add_stop), style = MaterialTheme.typography.titleMedium)

            when {
                draft.loading -> CenteredSpinner(Modifier.fillMaxWidth().padding(24.dp))
                draft.packages.isEmpty() -> Text(
                    stringResource(Res.string.shift_add_stop_no_packages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> draft.packages.forEach { pkg ->
                    val selected = pkg.packageId == draft.selectedPackageId
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelectPackage(pkg.packageId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelectPackage(pkg.packageId) })
                        Column {
                            Text(
                                pkg.trackingNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(pkg.receiverName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatAddressMultiline(pkg.receiverAddress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !draft.submitting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.action_cancel)) }
                Button(
                    onClick = onConfirm,
                    enabled = !draft.submitting && draft.selectedPackageId != null,
                    modifier = Modifier.weight(1f),
                ) {
                    if (draft.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.shift_add_stop))
                    }
                }
            }
        }
    }
}

/** A tappable date/time field on a subtle rounded surface, matching the create-shift wizard. */
@Composable
private fun DateTimeField(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) {
                Text(stringResource(Res.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
        text = { TimePicker(state = timeState) },
    )
}

@Composable
private fun PackageCard(
    step: RouteStep,
    recipient: Customer?,
    status: String?,
    images: List<StorageItem>,
    isInTransit: Boolean,
    isCurrent: Boolean,
    showNavigate: Boolean,
    actionEnabled: Boolean,
    hasPhoto: Boolean,
    editMode: Boolean,
    locked: Boolean,
    removeEnabled: Boolean,
    onRemove: () -> Unit,
    onAddPhoto: () -> Unit,
    shareMessage: String?,
    onClick: (() -> Unit)?,
    onMarkDelivered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialPhone = rememberDialPhone()
    val shareText = rememberShareText()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    // Tapping the card opens the package's own detail screen; suppressed in edit mode, where a tap
    // is more likely aimed at reordering/removing the stop than drilling into the package.
    val cardModifier = if (!editMode && onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    ElevatedCard(modifier = cardModifier) {
        Column(Modifier.padding(16.dp)) {
            val address = recipient?.fullAddress.orEmpty()
            val phone = recipient?.phone?.takeIf { it.isNotBlank() }
            // The name+address block and the action buttons (if any) are laid out side by side, with
            // the buttons vertically centred against the combined height of the block (top of name to
            // bottom of address). Delete and call are true siblings here (both in this outer Row) so
            // they land on the same baseline instead of one being pinned to the name row's height.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${step.stepIndex}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = recipient?.name ?: stringResource(Res.string.shift_unknown_recipient),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (address.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatAddressMultiline(address),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!editMode) {
                        status?.let {
                            Spacer(Modifier.height(6.dp))
                            StatusBadge(it)
                        }
                    }
                }
                if (editMode) {
                    if (locked) {
                        // Delivered / in-transit stops can't be removed.
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(Res.string.shift_stop_locked),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // Kept as a plain, unfilled icon button (vs. the call button's filled circle)
                        // so the destructive action reads as lower-affordance, and spaced well clear
                        // of the call button so an imprecise tap can't hit delete instead.
                        IconButton(onClick = { showRemoveConfirm = true }, enabled = removeEnabled) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.shift_remove_stop),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (phone != null) {
                    // The number itself is hidden; tapping the icon opens the dialer pre-filled with it.
                    Spacer(Modifier.width(if (editMode && !locked) 16.dp else 8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ) {
                        IconButton(onClick = { dialPhone(phone) }) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = stringResource(Res.string.shift_call_recipient),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (!editMode && shareMessage != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ) {
                        IconButton(onClick = { shareText(shareMessage) }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(Res.string.action_share),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (showRemoveConfirm) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirm = false },
                    title = { Text(stringResource(Res.string.shift_remove_stop_confirm_title)) },
                    text = {
                        Text(
                            stringResource(
                                Res.string.shift_remove_stop_confirm_message,
                                recipient?.name ?: stringResource(Res.string.shift_unknown_recipient),
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showRemoveConfirm = false; onRemove() }) {
                            Text(stringResource(Res.string.action_remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveConfirm = false }) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                    },
                )
            }
            if (!editMode && isCurrent && showNavigate) {
                val uriHandler = LocalUriHandler.current
                navigationUrl(step.location, address)?.let { url ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                        Icon(NavigationIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(Res.string.shift_navigate))
                    }
                }
            }
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { item ->
                        AsyncImage(
                            model = item,
                            contentDescription = stringResource(Res.string.cd_package_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            if (isInTransit) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onAddPhoto, enabled = actionEnabled) {
                        Text(
                            if (hasPhoto) {
                                stringResource(Res.string.shift_photo_added)
                            } else {
                                stringResource(Res.string.shift_add_photo)
                            },
                        )
                    }
                    Button(
                        onClick = onMarkDelivered,
                        enabled = actionEnabled,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(Res.string.shift_mark_delivered)) }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(message: String, success: Boolean = false) {
    val container = if (success) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (success) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Text(
            text = message,
            color = onContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun RetryCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = stringResource(Res.string.action_retry),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}
