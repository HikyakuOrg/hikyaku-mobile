package org.hikyaku.mobile.shift.create

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.action_cancel
import hikyaku.sharedui.generated.resources.action_next
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.action_remove
import hikyaku.sharedui.generated.resources.address_autocomplete_no_results
import hikyaku.sharedui.generated.resources.cd_package_photo
import hikyaku.sharedui.generated.resources.create_shift_add_new_location
import hikyaku.sharedui.generated.resources.create_shift_add_new_package
import hikyaku.sharedui.generated.resources.create_shift_add_vehicle_cta
import hikyaku.sharedui.generated.resources.create_shift_label_address
import hikyaku.sharedui.generated.resources.create_shift_label_location_name
import hikyaku.sharedui.generated.resources.create_shift_label_name
import hikyaku.sharedui.generated.resources.create_shift_label_search_address
import hikyaku.sharedui.generated.resources.create_shift_no_packages_available
import hikyaku.sharedui.generated.resources.create_shift_no_vehicles_body
import hikyaku.sharedui.generated.resources.create_shift_no_vehicles_title
import hikyaku.sharedui.generated.resources.create_shift_pick_date
import hikyaku.sharedui.generated.resources.create_shift_review_from
import hikyaku.sharedui.generated.resources.create_shift_review_start
import hikyaku.sharedui.generated.resources.create_shift_review_stops
import hikyaku.sharedui.generated.resources.create_shift_review_vehicle
import hikyaku.sharedui.generated.resources.create_shift_section_start_datetime
import hikyaku.sharedui.generated.resources.create_shift_section_starting_location
import hikyaku.sharedui.generated.resources.create_shift_section_vehicle_type
import hikyaku.sharedui.generated.resources.create_shift_step_details
import hikyaku.sharedui.generated.resources.create_shift_step_packages
import hikyaku.sharedui.generated.resources.create_shift_step_review
import hikyaku.sharedui.generated.resources.create_shift_submit
import hikyaku.sharedui.generated.resources.create_shift_title
import hikyaku.sharedui.generated.resources.create_shift_use_saved_location
import hikyaku.sharedui.generated.resources.package_action_choose_photos
import hikyaku.sharedui.generated.resources.package_action_take_photo
import hikyaku.sharedui.generated.resources.package_label_delivery_notes
import hikyaku.sharedui.generated.resources.package_label_height
import hikyaku.sharedui.generated.resources.package_label_length
import hikyaku.sharedui.generated.resources.package_label_weight
import hikyaku.sharedui.generated.resources.package_label_width
import hikyaku.sharedui.generated.resources.package_section_arrival
import hikyaku.sharedui.generated.resources.package_section_dimensions
import hikyaku.sharedui.generated.resources.package_section_images
import hikyaku.sharedui.generated.resources.package_section_receiver
import hikyaku.sharedui.generated.resources.package_section_sender
import hikyaku.sharedui.generated.resources.package_submit
import hikyaku.sharedui.generated.resources.phone_label_default
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.map.mapLayersSupported
import org.hikyaku.mobile.phone.PhoneNumberField
import org.hikyaku.mobile.shift.create.model.CustomerSuggestion
import org.hikyaku.mobile.shift.create.model.SelectablePackage
import org.hikyaku.mobile.shift.create.model.VehicleOption
import org.hikyaku.mobile.shift.create.model.WarehouseOption
import org.hikyaku.mobile.shift.rememberImagePicker
import org.hikyaku.mobile.shift.rememberPhotoCapture
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.hikyaku.mobile.util.epochMillisToIsoDate
import org.hikyaku.mobile.util.formatHourMinute
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private const val MAP_STYLE_URL = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

/**
 * The personal-org "create shift" wizard: vehicle type + start time + home base, then the packages
 * to deliver (picked from the org's unassigned packages at that warehouse, or composed fresh), then
 * a review that triggers persistence + optimisation. Reachable only from the personal-org FAB on the
 * home screen; it deliberately reuses the simple driver layout rather than any dispatcher dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShiftScreen(
    viewModel: CreateShiftViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // Shift created + optimisation triggered; return to the shift list.
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ToastEffect(state.error)

    CreateShiftScreenContent(
        state = state,
        onBack = viewModel::back,
        onNext = viewModel::next,
        onCancel = onCancel,
        onAddVehicle = onAddVehicle,
        onSelectVehicle = viewModel::selectVehicle,
        onSetStartDate = viewModel::setStartDate,
        onSetStartTime = viewModel::setStartTime,
        onSelectWarehouse = viewModel::selectWarehouse,
        onStartAddWarehouse = viewModel::startAddWarehouse,
        onSetWarehouseName = viewModel::setWarehouseName,
        onWarehouseQueryChange = viewModel::onWarehouseQueryChange,
        onPickWarehouseAddress = viewModel::pickWarehouseAddress,
        onTogglePackageSelection = viewModel::togglePackageSelection,
        onStartAddPackage = viewModel::startAddPackage,
        onCancelAddPackage = viewModel::cancelAddPackage,
        onSetPackageWeight = viewModel::setPackageWeight,
        onSetPackageLength = viewModel::setPackageLength,
        onSetPackageWidth = viewModel::setPackageWidth,
        onSetPackageHeight = viewModel::setPackageHeight,
        onAddPackageImages = viewModel::addPackageImages,
        onRemovePackageImage = viewModel::removePackageImage,
        onSetPackageSenderName = viewModel::setPackageSenderName,
        onSetPackageSenderPhone = viewModel::setPackageSenderPhone,
        onSetPackageSenderCountry = viewModel::setPackageSenderCountry,
        onPackageSenderQueryChange = viewModel::onPackageSenderQueryChange,
        onPickPackageSenderAddress = viewModel::pickPackageSenderAddress,
        onPickPackageSenderSuggestion = viewModel::pickPackageSenderSuggestion,
        onSetPackageReceiverName = viewModel::setPackageReceiverName,
        onSetPackageReceiverPhone = viewModel::setPackageReceiverPhone,
        onSetPackageReceiverCountry = viewModel::setPackageReceiverCountry,
        onPackageReceiverQueryChange = viewModel::onPackageReceiverQueryChange,
        onPickPackageReceiverAddress = viewModel::pickPackageReceiverAddress,
        onPickPackageReceiverSuggestion = viewModel::pickPackageReceiverSuggestion,
        onSetPackageDeliveryNotes = viewModel::setPackageDeliveryNotes,
        onSetPackageArrivalDate = viewModel::setPackageArrivalDate,
        onSetPackageArrivalTime = viewModel::setPackageArrivalTime,
        onConfirmAddPackage = viewModel::confirmAddPackage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateShiftScreenContent(
    state: CreateShiftUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onAddVehicle: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onSetStartDate: (Long?) -> Unit,
    onSetStartTime: (Int, Int) -> Unit,
    onSelectWarehouse: (String) -> Unit,
    onStartAddWarehouse: () -> Unit,
    onSetWarehouseName: (String) -> Unit,
    onWarehouseQueryChange: (String) -> Unit,
    onPickWarehouseAddress: (AddressSuggestion) -> Unit,
    onTogglePackageSelection: (String) -> Unit,
    onStartAddPackage: () -> Unit,
    onCancelAddPackage: () -> Unit,
    onSetPackageWeight: (String) -> Unit,
    onSetPackageLength: (String) -> Unit,
    onSetPackageWidth: (String) -> Unit,
    onSetPackageHeight: (String) -> Unit,
    onAddPackageImages: (List<ByteArray>) -> Unit,
    onRemovePackageImage: (Int) -> Unit,
    onSetPackageSenderName: (String) -> Unit,
    onSetPackageSenderPhone: (String) -> Unit,
    onSetPackageSenderCountry: (String) -> Unit,
    onPackageSenderQueryChange: (String) -> Unit,
    onPickPackageSenderAddress: (AddressSuggestion) -> Unit,
    onPickPackageSenderSuggestion: (CustomerSuggestion) -> Unit,
    onSetPackageReceiverName: (String) -> Unit,
    onSetPackageReceiverPhone: (String) -> Unit,
    onSetPackageReceiverCountry: (String) -> Unit,
    onPackageReceiverQueryChange: (String) -> Unit,
    onPickPackageReceiverAddress: (AddressSuggestion) -> Unit,
    onPickPackageReceiverSuggestion: (CustomerSuggestion) -> Unit,
    onSetPackageDeliveryNotes: (String) -> Unit,
    onSetPackageArrivalDate: (Long?) -> Unit,
    onSetPackageArrivalTime: (Int, Int) -> Unit,
    onConfirmAddPackage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.create_shift_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_cancel))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.step != CreateShiftStep.Details) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(Res.string.action_back)) }
                    }
                    val noVehicles = state.step == CreateShiftStep.Details && state.vehicles.isEmpty()
                    Button(
                        onClick = onNext,
                        enabled = !state.isSubmitting && !state.isLoading && !noVehicles,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (state.step == CreateShiftStep.Review) {
                                    stringResource(Res.string.create_shift_submit)
                                } else {
                                    stringResource(Res.string.action_next)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StepProgressIndicator(
                current = state.step,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                when {
                    state.isLoading -> Box_CenteredSpinner()
                    state.step == CreateShiftStep.Details -> DetailsStep(
                        state = state,
                        onAddVehicle = onAddVehicle,
                        onSelectVehicle = onSelectVehicle,
                        onSetStartDate = onSetStartDate,
                        onSetStartTime = onSetStartTime,
                        onSelectWarehouse = onSelectWarehouse,
                        onStartAddWarehouse = onStartAddWarehouse,
                        onSetWarehouseName = onSetWarehouseName,
                        onWarehouseQueryChange = onWarehouseQueryChange,
                        onPickWarehouseAddress = onPickWarehouseAddress,
                    )
                    state.step == CreateShiftStep.Packages -> PackagesStep(
                        state = state,
                        onTogglePackageSelection = onTogglePackageSelection,
                        onStartAddPackage = onStartAddPackage,
                        onCancelAddPackage = onCancelAddPackage,
                        onSetPackageWeight = onSetPackageWeight,
                        onSetPackageLength = onSetPackageLength,
                        onSetPackageWidth = onSetPackageWidth,
                        onSetPackageHeight = onSetPackageHeight,
                        onAddPackageImages = onAddPackageImages,
                        onRemovePackageImage = onRemovePackageImage,
                        onSetPackageSenderName = onSetPackageSenderName,
                        onSetPackageSenderPhone = onSetPackageSenderPhone,
                        onSetPackageSenderCountry = onSetPackageSenderCountry,
                        onPackageSenderQueryChange = onPackageSenderQueryChange,
                        onPickPackageSenderAddress = onPickPackageSenderAddress,
                        onPickPackageSenderSuggestion = onPickPackageSenderSuggestion,
                        onSetPackageReceiverName = onSetPackageReceiverName,
                        onSetPackageReceiverPhone = onSetPackageReceiverPhone,
                        onSetPackageReceiverCountry = onSetPackageReceiverCountry,
                        onPackageReceiverQueryChange = onPackageReceiverQueryChange,
                        onPickPackageReceiverAddress = onPickPackageReceiverAddress,
                        onPickPackageReceiverSuggestion = onPickPackageReceiverSuggestion,
                        onSetPackageDeliveryNotes = onSetPackageDeliveryNotes,
                        onSetPackageArrivalDate = onSetPackageArrivalDate,
                        onSetPackageArrivalTime = onSetPackageArrivalTime,
                        onConfirmAddPackage = onConfirmAddPackage,
                    )
                    state.step == CreateShiftStep.Review -> ReviewStep(state)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Preview
@Composable
private fun CreateShiftScreenDetailsStepPreview() {
    HikyakuTheme {
        CreateShiftScreenContent(
            state = CreateShiftUiState(
                step = CreateShiftStep.Details,
                vehicles = listOf(
                    VehicleOption(id = "v1", label = "Toyota HiAce", vehicleTypeId = "van"),
                    VehicleOption(id = "v2", label = "Honda Wave 110", vehicleTypeId = "motorcycle"),
                ),
                selectedVehicleId = "v1",
                warehouses = listOf(
                    WarehouseOption(
                        id = "w1",
                        name = "Main Warehouse",
                        address = "123 Orchard Road, Singapore",
                        lat = 1.3048,
                        lng = 103.8318,
                    ),
                ),
                selectedWarehouseId = "w1",
                startHour = 9,
                startMinute = 30,
            ),
            onBack = {},
            onNext = {},
            onCancel = {},
            onAddVehicle = {},
            onSelectVehicle = {},
            onSetStartDate = {},
            onSetStartTime = { _, _ -> },
            onSelectWarehouse = {},
            onStartAddWarehouse = {},
            onSetWarehouseName = {},
            onWarehouseQueryChange = {},
            onPickWarehouseAddress = {},
            onTogglePackageSelection = {},
            onStartAddPackage = {},
            onCancelAddPackage = {},
            onSetPackageWeight = {},
            onSetPackageLength = {},
            onSetPackageWidth = {},
            onSetPackageHeight = {},
            onAddPackageImages = {},
            onRemovePackageImage = {},
            onSetPackageSenderName = {},
            onSetPackageSenderPhone = {},
            onSetPackageSenderCountry = {},
            onPackageSenderQueryChange = {},
            onPickPackageSenderAddress = {},
            onPickPackageSenderSuggestion = {},
            onSetPackageReceiverName = {},
            onSetPackageReceiverPhone = {},
            onSetPackageReceiverCountry = {},
            onPackageReceiverQueryChange = {},
            onPickPackageReceiverAddress = {},
            onPickPackageReceiverSuggestion = {},
            onSetPackageDeliveryNotes = {},
            onSetPackageArrivalDate = {},
            onSetPackageArrivalTime = { _, _ -> },
            onConfirmAddPackage = {},
        )
    }
}

@Preview
@Composable
private fun CreateShiftScreenPackagesStepPreview() {
    HikyakuTheme {
        CreateShiftScreenContent(
            state = CreateShiftUiState(
                step = CreateShiftStep.Packages,
                availablePackages = listOf(
                    SelectablePackage(
                        id = "p1",
                        trackingNumber = "HKY-00123",
                        receiverName = "Jane Tan",
                        receiverAddress = "45 River Valley Road, Singapore",
                    ),
                    SelectablePackage(
                        id = "p2",
                        trackingNumber = "HKY-00124",
                        receiverName = "Wei Ming Lee",
                        receiverAddress = "88 Tampines Ave 4, Singapore",
                    ),
                ),
                selectedPackageIds = setOf("p1"),
            ),
            onBack = {},
            onNext = {},
            onCancel = {},
            onAddVehicle = {},
            onSelectVehicle = {},
            onSetStartDate = {},
            onSetStartTime = { _, _ -> },
            onSelectWarehouse = {},
            onStartAddWarehouse = {},
            onSetWarehouseName = {},
            onWarehouseQueryChange = {},
            onPickWarehouseAddress = {},
            onTogglePackageSelection = {},
            onStartAddPackage = {},
            onCancelAddPackage = {},
            onSetPackageWeight = {},
            onSetPackageLength = {},
            onSetPackageWidth = {},
            onSetPackageHeight = {},
            onAddPackageImages = {},
            onRemovePackageImage = {},
            onSetPackageSenderName = {},
            onSetPackageSenderPhone = {},
            onSetPackageSenderCountry = {},
            onPackageSenderQueryChange = {},
            onPickPackageSenderAddress = {},
            onPickPackageSenderSuggestion = {},
            onSetPackageReceiverName = {},
            onSetPackageReceiverPhone = {},
            onSetPackageReceiverCountry = {},
            onPackageReceiverQueryChange = {},
            onPickPackageReceiverAddress = {},
            onPickPackageReceiverSuggestion = {},
            onSetPackageDeliveryNotes = {},
            onSetPackageArrivalDate = {},
            onSetPackageArrivalTime = { _, _ -> },
            onConfirmAddPackage = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Step progress header
// ---------------------------------------------------------------------------

private val wizardSteps = listOf(CreateShiftStep.Details, CreateShiftStep.Packages, CreateShiftStep.Review)

@Composable
private fun stepLabel(step: CreateShiftStep): String = when (step) {
    CreateShiftStep.Details -> stringResource(Res.string.create_shift_step_details)
    CreateShiftStep.Packages -> stringResource(Res.string.create_shift_step_packages)
    CreateShiftStep.Review -> stringResource(Res.string.create_shift_step_review)
}

/**
 * The horizontal stepper shown above the wizard body: numbered circles joined by connecting lines,
 * with a label under each. Completed steps fill in (and show a check), the current step is
 * highlighted, and upcoming steps stay muted. The connector between two steps fills once the left
 * step is done, so progress "flows" left-to-right as the user advances.
 */
@Composable
private fun StepProgressIndicator(current: CreateShiftStep, modifier: Modifier = Modifier) {
    val currentIndex = wizardSteps.indexOfFirst { it == current }.coerceAtLeast(0)
    Row(modifier, verticalAlignment = Alignment.Top) {
        wizardSteps.forEachIndexed { index, step ->
            val completed = index < currentIndex
            val active = index == currentIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StepConnector(visible = index > 0, filled = currentIndex >= index, modifier = Modifier.weight(1f))
                    StepCircle(number = index + 1, completed = completed, active = active)
                    StepConnector(
                        visible = index < wizardSteps.lastIndex,
                        filled = currentIndex > index,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stepLabel(step),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active || completed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StepCircle(number: Int, completed: Boolean, active: Boolean) {
    val background by animateColorAsState(
        targetValue = if (completed || active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "stepCircleBackground",
    )
    val content = if (completed || active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (completed) "✓" else number.toString(),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StepConnector(visible: Boolean, filled: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "stepConnector",
    )
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .then(if (visible) Modifier.background(color) else Modifier),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsStep(
    state: CreateShiftUiState,
    onAddVehicle: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onSetStartDate: (Long?) -> Unit,
    onSetStartTime: (Int, Int) -> Unit,
    onSelectWarehouse: (String) -> Unit,
    onStartAddWarehouse: () -> Unit,
    onSetWarehouseName: (String) -> Unit,
    onWarehouseQueryChange: (String) -> Unit,
    onPickWarehouseAddress: (AddressSuggestion) -> Unit,
) {
    SectionLabel(stringResource(Res.string.create_shift_section_vehicle_type))
    if (state.vehicles.isEmpty()) {
        NoVehiclesCard(onAddVehicle = onAddVehicle)
    } else {
        VehicleDropdown(state = state, onSelectVehicle = onSelectVehicle)
    }

    SectionLabel(stringResource(Res.string.create_shift_section_start_datetime))
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateTimeField(
            text = state.startDateMillis?.let { epochMillisToIsoDate(it) }
                ?: stringResource(Res.string.create_shift_pick_date),
            onClick = { showDate = true },
            modifier = Modifier.weight(1f),
        )
        DateTimeField(
            text = formatHourMinute(state.startHour, state.startMinute),
            onClick = { showTime = true },
        )
    }
    if (showDate) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = state.startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { onSetStartDate(dp.selectedDateMillis); showDate = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) { DatePicker(state = dp) }
    }
    if (showTime) {
        TimePickerDialog(
            initialHour = state.startHour,
            initialMinute = state.startMinute,
            onConfirm = { h, m -> onSetStartTime(h, m); showTime = false },
            onDismiss = { showTime = false },
        )
    }

    SectionLabel(stringResource(Res.string.create_shift_section_starting_location))
    if (state.warehouses.isNotEmpty() && !state.addingWarehouse) {
        state.warehouses.forEach { w ->
            OptionRow(
                selected = w.id == state.selectedWarehouseId,
                text = "${w.name} — ${w.address}",
                onClick = { onSelectWarehouse(w.id) },
            )
        }
        state.warehouses.firstOrNull { it.id == state.selectedWarehouseId }?.let { selected ->
            WarehouseMap(lat = selected.lat, lng = selected.lng)
        }
        TextButton(onClick = onStartAddWarehouse) {
            Text(stringResource(Res.string.create_shift_add_new_location))
        }
    } else {
        OutlinedTextField(
            value = state.warehouseName,
            onValueChange = onSetWarehouseName,
            label = { Text(stringResource(Res.string.create_shift_label_location_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AddressAutocomplete(
            label = stringResource(Res.string.create_shift_label_search_address),
            query = state.warehouseQuery,
            suggestions = state.warehouseSuggestions,
            searching = state.warehouseSearching,
            hasSelection = state.pickedWarehouse != null,
            onQueryChange = onWarehouseQueryChange,
            onPick = onPickWarehouseAddress,
        )
        state.pickedWarehouse?.let { picked ->
            WarehouseMap(lat = picked.lat, lng = picked.lon)
        }
        if (state.warehouses.isNotEmpty()) {
            TextButton(onClick = { state.warehouses.firstOrNull()?.let { onSelectWarehouse(it.id) } }) {
                Text(stringResource(Res.string.create_shift_use_saved_location))
            }
        }
    }
}

/**
 * The packages step: a checklist of the org's unassigned packages already sitting at the shift's
 * chosen warehouse, plus an inline form to compose a brand-new one (sender, receiver, dimensions,
 * arrival) when none of the existing ones fit. A package created here is persisted immediately
 * (same write path as the standalone add-package screen) and auto-selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackagesStep(
    state: CreateShiftUiState,
    onTogglePackageSelection: (String) -> Unit,
    onStartAddPackage: () -> Unit,
    onCancelAddPackage: () -> Unit,
    onSetPackageWeight: (String) -> Unit,
    onSetPackageLength: (String) -> Unit,
    onSetPackageWidth: (String) -> Unit,
    onSetPackageHeight: (String) -> Unit,
    onAddPackageImages: (List<ByteArray>) -> Unit,
    onRemovePackageImage: (Int) -> Unit,
    onSetPackageSenderName: (String) -> Unit,
    onSetPackageSenderPhone: (String) -> Unit,
    onSetPackageSenderCountry: (String) -> Unit,
    onPackageSenderQueryChange: (String) -> Unit,
    onPickPackageSenderAddress: (AddressSuggestion) -> Unit,
    onPickPackageSenderSuggestion: (CustomerSuggestion) -> Unit,
    onSetPackageReceiverName: (String) -> Unit,
    onSetPackageReceiverPhone: (String) -> Unit,
    onSetPackageReceiverCountry: (String) -> Unit,
    onPackageReceiverQueryChange: (String) -> Unit,
    onPickPackageReceiverAddress: (AddressSuggestion) -> Unit,
    onPickPackageReceiverSuggestion: (CustomerSuggestion) -> Unit,
    onSetPackageDeliveryNotes: (String) -> Unit,
    onSetPackageArrivalDate: (Long?) -> Unit,
    onSetPackageArrivalTime: (Int, Int) -> Unit,
    onConfirmAddPackage: () -> Unit,
) {
    SectionLabel(stringResource(Res.string.create_shift_step_packages))
    when {
        state.packagesLoading -> Box_CenteredSpinner()
        state.availablePackages.isEmpty() && !state.showAddPackageForm -> Text(
            stringResource(Res.string.create_shift_no_packages_available),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> state.availablePackages.forEach { pkg ->
            PackageOptionRow(
                pkg = pkg,
                selected = pkg.id in state.selectedPackageIds,
                onClick = { onTogglePackageSelection(pkg.id) },
            )
        }
    }

    if (state.showAddPackageForm) {
        NewPackageForm(
            state = state,
            onCancelAddPackage = onCancelAddPackage,
            onSetPackageWeight = onSetPackageWeight,
            onSetPackageLength = onSetPackageLength,
            onSetPackageWidth = onSetPackageWidth,
            onSetPackageHeight = onSetPackageHeight,
            onAddPackageImages = onAddPackageImages,
            onRemovePackageImage = onRemovePackageImage,
            onSetPackageSenderName = onSetPackageSenderName,
            onSetPackageSenderPhone = onSetPackageSenderPhone,
            onSetPackageSenderCountry = onSetPackageSenderCountry,
            onPackageSenderQueryChange = onPackageSenderQueryChange,
            onPickPackageSenderAddress = onPickPackageSenderAddress,
            onPickPackageSenderSuggestion = onPickPackageSenderSuggestion,
            onSetPackageReceiverName = onSetPackageReceiverName,
            onSetPackageReceiverPhone = onSetPackageReceiverPhone,
            onSetPackageReceiverCountry = onSetPackageReceiverCountry,
            onPackageReceiverQueryChange = onPackageReceiverQueryChange,
            onPickPackageReceiverAddress = onPickPackageReceiverAddress,
            onPickPackageReceiverSuggestion = onPickPackageReceiverSuggestion,
            onSetPackageDeliveryNotes = onSetPackageDeliveryNotes,
            onSetPackageArrivalDate = onSetPackageArrivalDate,
            onSetPackageArrivalTime = onSetPackageArrivalTime,
            onConfirmAddPackage = onConfirmAddPackage,
        )
    } else {
        TextButton(onClick = onStartAddPackage) {
            Text(stringResource(Res.string.create_shift_add_new_package))
        }
    }
}

@Composable
private fun PackageOptionRow(pkg: SelectablePackage, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column {
            Text(pkg.trackingNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${pkg.receiverName} — ${pkg.receiverAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewPackageForm(
    state: CreateShiftUiState,
    onCancelAddPackage: () -> Unit,
    onSetPackageWeight: (String) -> Unit,
    onSetPackageLength: (String) -> Unit,
    onSetPackageWidth: (String) -> Unit,
    onSetPackageHeight: (String) -> Unit,
    onAddPackageImages: (List<ByteArray>) -> Unit,
    onRemovePackageImage: (Int) -> Unit,
    onSetPackageSenderName: (String) -> Unit,
    onSetPackageSenderPhone: (String) -> Unit,
    onSetPackageSenderCountry: (String) -> Unit,
    onPackageSenderQueryChange: (String) -> Unit,
    onPickPackageSenderAddress: (AddressSuggestion) -> Unit,
    onPickPackageSenderSuggestion: (CustomerSuggestion) -> Unit,
    onSetPackageReceiverName: (String) -> Unit,
    onSetPackageReceiverPhone: (String) -> Unit,
    onSetPackageReceiverCountry: (String) -> Unit,
    onPackageReceiverQueryChange: (String) -> Unit,
    onPickPackageReceiverAddress: (AddressSuggestion) -> Unit,
    onPickPackageReceiverSuggestion: (CustomerSuggestion) -> Unit,
    onSetPackageDeliveryNotes: (String) -> Unit,
    onSetPackageArrivalDate: (Long?) -> Unit,
    onSetPackageArrivalTime: (Int, Int) -> Unit,
    onConfirmAddPackage: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(Res.string.package_section_dimensions))
            OutlinedTextField(
                value = state.packageWeight,
                onValueChange = onSetPackageWeight,
                label = { Text(stringResource(Res.string.package_label_weight)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.packageLength,
                    onValueChange = onSetPackageLength,
                    label = { Text(stringResource(Res.string.package_label_length)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.packageWidth,
                    onValueChange = onSetPackageWidth,
                    label = { Text(stringResource(Res.string.package_label_width)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.packageHeight,
                    onValueChange = onSetPackageHeight,
                    label = { Text(stringResource(Res.string.package_label_height)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel(stringResource(Res.string.package_section_images))
            val capturePhoto = rememberPhotoCapture { bytes -> if (bytes != null) onAddPackageImages(listOf(bytes)) }
            val pickImages = rememberImagePicker { images -> onAddPackageImages(images) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = capturePhoto) { Text(stringResource(Res.string.package_action_take_photo)) }
                OutlinedButton(onClick = pickImages) { Text(stringResource(Res.string.package_action_choose_photos)) }
            }
            if (state.packageImages.isNotEmpty()) {
                val photoDescription = stringResource(Res.string.cd_package_photo)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.packageImages.size) { index ->
                        Box(Modifier.size(72.dp)) {
                            AsyncImage(
                                model = state.packageImages[index],
                                contentDescription = photoDescription,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                                    .clickable { onRemovePackageImage(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(Res.string.action_remove),
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel(stringResource(Res.string.package_section_sender))
            PackagePartyFields(
                customer = state.packageSender,
                onNameChange = onSetPackageSenderName,
                onPhoneChange = onSetPackageSenderPhone,
                onCountryChange = onSetPackageSenderCountry,
                onQueryChange = onPackageSenderQueryChange,
                onPickAddress = onPickPackageSenderAddress,
                onPickSuggestion = onPickPackageSenderSuggestion,
            )

            SectionLabel(stringResource(Res.string.package_section_receiver))
            PackagePartyFields(
                customer = state.packageReceiver,
                onNameChange = onSetPackageReceiverName,
                onPhoneChange = onSetPackageReceiverPhone,
                onCountryChange = onSetPackageReceiverCountry,
                onQueryChange = onPackageReceiverQueryChange,
                onPickAddress = onPickPackageReceiverAddress,
                onPickSuggestion = onPickPackageReceiverSuggestion,
            )

            OutlinedTextField(
                value = state.packageDeliveryNotes,
                onValueChange = onSetPackageDeliveryNotes,
                label = { Text(stringResource(Res.string.package_label_delivery_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            SectionLabel(stringResource(Res.string.package_section_arrival))
            var showDate by remember { mutableStateOf(false) }
            var showTime by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateTimeField(
                    text = state.packageArrivalDateMillis?.let { epochMillisToIsoDate(it) }
                        ?: stringResource(Res.string.create_shift_pick_date),
                    onClick = { showDate = true },
                    modifier = Modifier.weight(1f),
                )
                DateTimeField(
                    text = formatHourMinute(state.packageArrivalHour, state.packageArrivalMinute),
                    onClick = { showTime = true },
                )
            }
            if (showDate) {
                val dp = rememberDatePickerState(initialSelectedDateMillis = state.packageArrivalDateMillis)
                DatePickerDialog(
                    onDismissRequest = { showDate = false },
                    confirmButton = {
                        TextButton(onClick = { onSetPackageArrivalDate(dp.selectedDateMillis); showDate = false }) {
                            Text(stringResource(Res.string.action_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDate = false }) { Text(stringResource(Res.string.action_cancel)) }
                    },
                ) { DatePicker(state = dp) }
            }
            if (showTime) {
                TimePickerDialog(
                    initialHour = state.packageArrivalHour,
                    initialMinute = state.packageArrivalMinute,
                    onConfirm = { h, m -> onSetPackageArrivalTime(h, m); showTime = false },
                    onDismiss = { showTime = false },
                )
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirmAddPackage,
                    enabled = !state.isCreatingPackage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isCreatingPackage) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.package_submit))
                    }
                }
                OutlinedButton(
                    onClick = onCancelAddPackage,
                    enabled = !state.isCreatingPackage,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun PackagePartyFields(
    customer: CustomerDraft,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onPickAddress: (AddressSuggestion) -> Unit,
    onPickSuggestion: (CustomerSuggestion) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = customer.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.create_shift_label_name)) },
            singleLine = true,
            trailingIcon = if (customer.nameSearching) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        customer.nameSuggestions.forEach { s ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable { onPickSuggestion(s) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium)
                s.phoneE164?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.address?.label?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }
    }
    Spacer(Modifier.height(8.dp))
    PhoneNumberField(
        nationalNumber = customer.phone,
        countryIso = customer.countryIso,
        onNationalNumberChange = onPhoneChange,
        onCountrySelected = onCountryChange,
        label = stringResource(Res.string.phone_label_default),
    )
    Spacer(Modifier.height(8.dp))
    AddressAutocomplete(
        label = stringResource(Res.string.create_shift_label_address),
        query = customer.addressQuery,
        suggestions = customer.suggestions,
        searching = customer.searching,
        hasSelection = customer.picked != null,
        onQueryChange = onQueryChange,
        onPick = onPickAddress,
    )
}

@Composable
private fun ReviewStep(state: CreateShiftUiState) {
    val vehicle = state.vehicles.firstOrNull { it.id == state.selectedVehicleId }
    val warehouse = state.warehouses.firstOrNull { it.id == state.resolvedWarehouseId }
        ?.let { "${it.name} — ${it.address}" } ?: "—"
    val selectedPackages = state.availablePackages.filter { it.id in state.selectedPackageIds }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryRow(stringResource(Res.string.create_shift_review_vehicle), vehicle?.label ?: "—")
            SummaryRow(
                stringResource(Res.string.create_shift_review_start),
                state.startDateMillis?.let {
                    "${epochMillisToIsoDate(it)} ${formatHourMinute(state.startHour, state.startMinute)}"
                } ?: "—",
            )
            SummaryRow(stringResource(Res.string.create_shift_review_from), warehouse)
            SummaryRow(stringResource(Res.string.create_shift_review_stops), selectedPackages.size.toString())
        }
    }
    SectionLabel(stringResource(Res.string.create_shift_step_packages))
    selectedPackages.forEachIndexed { index, pkg ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${index + 1}. ${pkg.trackingNumber}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${pkg.receiverName} — ${pkg.receiverAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun AddressAutocomplete(
    label: String,
    query: String,
    suggestions: List<AddressSuggestion>,
    searching: Boolean,
    hasSelection: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (AddressSuggestion) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = if (searching) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!searching && !hasSelection && suggestions.isEmpty() && query.trim().length >= ADDRESS_MIN_QUERY_LENGTH) {
            Text(
                text = stringResource(Res.string.address_autocomplete_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            )
        } else {
            suggestions.take(8).forEach { s ->
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp, horizontal = 4.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

/** Mirrors [org.hikyaku.mobile.shift.create.CreateShiftViewModel]'s geocode debounce threshold. */
private const val ADDRESS_MIN_QUERY_LENGTH = 3

/**
 * A compact preview map centred on [lat]/[lng] with a single marker, shown once the user has a
 * starting location. Re-centres (and moves the marker) when the chosen location changes. Reuses the
 * same MapLibre base style as the shift-detail route map.
 */
@Composable
private fun WarehouseMap(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(target = Position(longitude = lng, latitude = lat), zoom = 14.0),
    )
    LaunchedEffect(lat, lng) {
        cameraState.animateTo(CameraPosition(target = Position(longitude = lng, latitude = lat), zoom = 14.0))
    }
    Box(modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = cameraState,
            options = MapOptions(gestureOptions = GestureOptions.AllDisabled),
        ) {
            // Desktop MapLibre Compose can't render sources/layers yet; show the base map only there.
            if (mapLayersSupported) {
                val markerSource = rememberGeoJsonSource(
                    GeoJsonData.Features(Feature(Point(longitude = lng, latitude = lat), properties = null)),
                )
                CircleLayer(
                    id = "warehouse-marker",
                    source = markerSource,
                    color = const(Color(0xFF19398D)),
                    radius = const(8.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
            }
        }
    }
}

/**
 * A tappable, Google-Calendar-style date/time field: text sits on a subtle rounded surface. Used as
 * a pair (date on the left, time on the right) for picking the shift start.
 */
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

/**
 * Shown in place of [VehicleDropdown] when the org has no vehicles: a shift can't be created
 * without one, so this explains why and sends the user straight to the add-vehicle screen rather
 * than letting them discover the problem only after tapping "Next".
 */
@Composable
private fun NoVehiclesCard(onAddVehicle: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.create_shift_no_vehicles_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.create_shift_no_vehicles_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddVehicle) { Text(stringResource(Res.string.create_shift_add_vehicle_cta)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDropdown(state: CreateShiftUiState, onSelectVehicle: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.vehicles.firstOrNull { it.id == state.selectedVehicleId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicle.label) },
                    onClick = { onSelectVehicle(vehicle.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Box_CenteredSpinner() {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
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

