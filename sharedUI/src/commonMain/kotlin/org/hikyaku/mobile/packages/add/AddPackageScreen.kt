package org.hikyaku.mobile.packages.add

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.action_cancel
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.action_remove
import hikyaku.sharedui.generated.resources.address_autocomplete_no_results
import hikyaku.sharedui.generated.resources.address_pick_on_map
import hikyaku.sharedui.generated.resources.cd_package_photo
import hikyaku.sharedui.generated.resources.create_shift_add_new_location
import hikyaku.sharedui.generated.resources.create_shift_label_address
import hikyaku.sharedui.generated.resources.create_shift_label_location_name
import hikyaku.sharedui.generated.resources.create_shift_label_name
import hikyaku.sharedui.generated.resources.create_shift_label_search_address
import hikyaku.sharedui.generated.resources.create_shift_pick_date
import hikyaku.sharedui.generated.resources.create_shift_use_saved_location
import hikyaku.sharedui.generated.resources.package_action_choose_photos
import hikyaku.sharedui.generated.resources.package_action_take_photo
import hikyaku.sharedui.generated.resources.package_add_title
import hikyaku.sharedui.generated.resources.package_label_delivery_notes
import hikyaku.sharedui.generated.resources.package_label_height
import hikyaku.sharedui.generated.resources.package_label_length
import hikyaku.sharedui.generated.resources.package_label_weight
import hikyaku.sharedui.generated.resources.package_label_width
import hikyaku.sharedui.generated.resources.package_outcome_assigned
import hikyaku.sharedui.generated.resources.package_outcome_assigned_eta
import hikyaku.sharedui.generated.resources.package_outcome_deferred
import hikyaku.sharedui.generated.resources.package_outcome_evicted
import hikyaku.sharedui.generated.resources.package_outcome_new_shift
import hikyaku.sharedui.generated.resources.package_outcome_new_shift_eta
import hikyaku.sharedui.generated.resources.package_outcome_reason_allowance
import hikyaku.sharedui.generated.resources.package_outcome_reason_auto_assign
import hikyaku.sharedui.generated.resources.package_outcome_reason_deadline
import hikyaku.sharedui.generated.resources.package_outcome_reason_no_capacity
import hikyaku.sharedui.generated.resources.package_outcome_reason_no_driver
import hikyaku.sharedui.generated.resources.package_outcome_reason_no_geocode
import hikyaku.sharedui.generated.resources.package_outcome_skipped
import hikyaku.sharedui.generated.resources.package_outcome_title
import hikyaku.sharedui.generated.resources.package_section_arrival
import hikyaku.sharedui.generated.resources.package_section_dimensions
import hikyaku.sharedui.generated.resources.package_section_images
import hikyaku.sharedui.generated.resources.package_section_receiver
import hikyaku.sharedui.generated.resources.package_section_sender
import hikyaku.sharedui.generated.resources.package_section_warehouse
import hikyaku.sharedui.generated.resources.package_submit
import hikyaku.sharedui.generated.resources.warehouse_personal_org_limit_notice
import org.hikyaku.mobile.api.generated.models.AssignmentOutcomeDto
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.map.LocationPickerDialog
import org.hikyaku.mobile.map.LocationPinIcon
import org.hikyaku.mobile.phone.PhoneNumberField
import org.hikyaku.mobile.shift.create.CustomerDraft
import org.hikyaku.mobile.shift.create.model.CustomerSuggestion
import org.hikyaku.mobile.shift.rememberImagePicker
import org.hikyaku.mobile.shift.rememberPhotoCapture
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.hikyaku.mobile.util.epochMillisToDisplayDate
import org.hikyaku.mobile.util.formatHourMinute
import org.jetbrains.compose.resources.stringResource
import org.maplibre.spatialk.geojson.Position

/**
 * Single-page add-package form: physical dimensions, optional photos (camera or gallery), sender
 * and receiver details (address-autocompleted, same as the create-shift customer step), delivery
 * notes, starting warehouse, and the scheduled arrival date/time. Submits through
 * [AddPackageViewModel], which persists everything via `PackageRepository`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackageScreen(
    viewModel: AddPackageViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ToastEffect(state.error)

    // The package is already created by the time this shows; the panel exists so the driver sees
    // whether it landed on a shift before the form closes.
    state.assignment?.let { assignment ->
        AssignmentOutcomeDialog(assignment = assignment, onDismiss = viewModel::dismissAssignment)
    }

    AddPackageScreenContent(
        state = state,
        onSubmit = viewModel::submit,
        onCancel = onCancel,
        onSetWeight = viewModel::setWeight,
        onSetLength = viewModel::setLength,
        onSetWidth = viewModel::setWidth,
        onSetHeight = viewModel::setHeight,
        onAddImages = viewModel::addImages,
        onRemoveImage = viewModel::removeImage,
        onSetSenderName = viewModel::setSenderName,
        onSetSenderPhone = viewModel::setSenderPhone,
        onSetSenderCountry = viewModel::setSenderCountry,
        onSenderQueryChange = viewModel::onSenderQueryChange,
        onPickSenderAddress = viewModel::pickSenderAddress,
        onPickSenderSuggestion = viewModel::pickSenderSuggestion,
        onSetReceiverName = viewModel::setReceiverName,
        onSetReceiverPhone = viewModel::setReceiverPhone,
        onSetReceiverCountry = viewModel::setReceiverCountry,
        onReceiverQueryChange = viewModel::onReceiverQueryChange,
        onPickReceiverAddress = viewModel::pickReceiverAddress,
        onPickReceiverSuggestion = viewModel::pickReceiverSuggestion,
        onSetDeliveryNotes = viewModel::setDeliveryNotes,
        onSelectWarehouse = viewModel::selectWarehouse,
        onStartAddWarehouse = viewModel::startAddWarehouse,
        onSetWarehouseName = viewModel::setWarehouseName,
        onWarehouseQueryChange = viewModel::onWarehouseQueryChange,
        onPickWarehouseAddress = viewModel::pickWarehouseAddress,
        onSetArrivalDate = viewModel::setArrivalDate,
        onSetArrivalTime = viewModel::setArrivalTime,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPackageScreenContent(
    state: AddPackageUiState,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onSetWeight: (String) -> Unit,
    onSetLength: (String) -> Unit,
    onSetWidth: (String) -> Unit,
    onSetHeight: (String) -> Unit,
    onAddImages: (List<ByteArray>) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSetSenderName: (String) -> Unit,
    onSetSenderPhone: (String) -> Unit,
    onSetSenderCountry: (String) -> Unit,
    onSenderQueryChange: (String) -> Unit,
    onPickSenderAddress: (AddressSuggestion) -> Unit,
    onPickSenderSuggestion: (CustomerSuggestion) -> Unit,
    onSetReceiverName: (String) -> Unit,
    onSetReceiverPhone: (String) -> Unit,
    onSetReceiverCountry: (String) -> Unit,
    onReceiverQueryChange: (String) -> Unit,
    onPickReceiverAddress: (AddressSuggestion) -> Unit,
    onPickReceiverSuggestion: (CustomerSuggestion) -> Unit,
    onSetDeliveryNotes: (String) -> Unit,
    onSelectWarehouse: (String) -> Unit,
    onStartAddWarehouse: () -> Unit,
    onSetWarehouseName: (String) -> Unit,
    onWarehouseQueryChange: (String) -> Unit,
    onPickWarehouseAddress: (AddressSuggestion) -> Unit,
    onSetArrivalDate: (Long?) -> Unit,
    onSetArrivalTime: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.package_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting && !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.package_submit))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DimensionsSection(state, onSetWeight, onSetLength, onSetWidth, onSetHeight)
                ImagesSection(state, onAddImages, onRemoveImage)
                SectionLabel(stringResource(Res.string.package_section_sender))
                CustomerFields(
                    customer = state.sender,
                    onNameChange = onSetSenderName,
                    onPhoneChange = onSetSenderPhone,
                    onCountryChange = onSetSenderCountry,
                    onQueryChange = onSenderQueryChange,
                    onPickAddress = onPickSenderAddress,
                    onPickSuggestion = onPickSenderSuggestion,
                )
                SectionLabel(stringResource(Res.string.package_section_receiver))
                CustomerFields(
                    customer = state.receiver,
                    onNameChange = onSetReceiverName,
                    onPhoneChange = onSetReceiverPhone,
                    onCountryChange = onSetReceiverCountry,
                    onQueryChange = onReceiverQueryChange,
                    onPickAddress = onPickReceiverAddress,
                    onPickSuggestion = onPickReceiverSuggestion,
                )
                OutlinedTextField(
                    value = state.deliveryNotes,
                    onValueChange = onSetDeliveryNotes,
                    label = { Text(stringResource(Res.string.package_label_delivery_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                WarehouseSection(
                    state = state,
                    onSelectWarehouse = onSelectWarehouse,
                    onStartAddWarehouse = onStartAddWarehouse,
                    onSetWarehouseName = onSetWarehouseName,
                    onWarehouseQueryChange = onWarehouseQueryChange,
                    onPickWarehouseAddress = onPickWarehouseAddress,
                )
                ArrivalSection(state, onSetArrivalDate, onSetArrivalTime)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Preview
@Composable
private fun AddPackageScreenPreview() {
    HikyakuTheme {
        AddPackageScreenContent(
            state = AddPackageUiState(),
            onSubmit = {},
            onCancel = {},
            onSetWeight = {},
            onSetLength = {},
            onSetWidth = {},
            onSetHeight = {},
            onAddImages = {},
            onRemoveImage = {},
            onSetSenderName = {},
            onSetSenderPhone = {},
            onSetSenderCountry = {},
            onSenderQueryChange = {},
            onPickSenderAddress = {},
            onPickSenderSuggestion = {},
            onSetReceiverName = {},
            onSetReceiverPhone = {},
            onSetReceiverCountry = {},
            onReceiverQueryChange = {},
            onPickReceiverAddress = {},
            onPickReceiverSuggestion = {},
            onSetDeliveryNotes = {},
            onSelectWarehouse = {},
            onStartAddWarehouse = {},
            onSetWarehouseName = {},
            onWarehouseQueryChange = {},
            onPickWarehouseAddress = {},
            onSetArrivalDate = {},
            onSetArrivalTime = { _, _ -> },
        )
    }
}

@Composable
private fun DimensionsSection(
    state: AddPackageUiState,
    onSetWeight: (String) -> Unit,
    onSetLength: (String) -> Unit,
    onSetWidth: (String) -> Unit,
    onSetHeight: (String) -> Unit,
) {
    SectionLabel(stringResource(Res.string.package_section_dimensions))
    OutlinedTextField(
        value = state.weight,
        onValueChange = onSetWeight,
        label = { Text(stringResource(Res.string.package_label_weight)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.length,
            onValueChange = onSetLength,
            label = { Text(stringResource(Res.string.package_label_length)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.width,
            onValueChange = onSetWidth,
            label = { Text(stringResource(Res.string.package_label_width)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.height,
            onValueChange = onSetHeight,
            label = { Text(stringResource(Res.string.package_label_height)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ImagesSection(
    state: AddPackageUiState,
    onAddImages: (List<ByteArray>) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    SectionLabel(stringResource(Res.string.package_section_images))
    val capturePhoto = rememberPhotoCapture { bytes -> if (bytes != null) onAddImages(listOf(bytes)) }
    val pickImages = rememberImagePicker { images -> onAddImages(images) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = capturePhoto) { Text(stringResource(Res.string.package_action_take_photo)) }
        OutlinedButton(onClick = pickImages) { Text(stringResource(Res.string.package_action_choose_photos)) }
    }
    if (state.images.isNotEmpty()) {
        val photoDescription = stringResource(Res.string.cd_package_photo)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.images.size) { index ->
                Box(Modifier.size(88.dp)) {
                    AsyncImage(
                        model = state.images[index],
                        contentDescription = photoDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                            .clickable { onRemoveImage(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.action_remove),
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerFields(
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
                Modifier.fillMaxWidth().clickable { onPickSuggestion(s) }.padding(vertical = 8.dp, horizontal = 4.dp),
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
    )
    Spacer(Modifier.height(8.dp))
    AddressAutocomplete(
        label = stringResource(Res.string.create_shift_label_address),
        query = customer.addressQuery,
        suggestions = customer.suggestions,
        searching = customer.searching,
        hasSelection = customer.picked != null,
        initialMapPosition = customer.picked?.let { Position(longitude = it.lon, latitude = it.lat) },
        onQueryChange = onQueryChange,
        onPick = onPickAddress,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseSection(
    state: AddPackageUiState,
    onSelectWarehouse: (String) -> Unit,
    onStartAddWarehouse: () -> Unit,
    onSetWarehouseName: (String) -> Unit,
    onWarehouseQueryChange: (String) -> Unit,
    onPickWarehouseAddress: (AddressSuggestion) -> Unit,
) {
    SectionLabel(stringResource(Res.string.package_section_warehouse))
    if (state.warehouses.isNotEmpty() && !state.addingWarehouse) {
        state.warehouses.forEach { w ->
            OptionRow(
                selected = w.id == state.selectedWarehouseId,
                text = "${w.name} — ${w.address}",
                onClick = { onSelectWarehouse(w.id) },
            )
        }
        if (state.canAddWarehouse) {
            TextButton(onClick = onStartAddWarehouse) {
                Text(stringResource(Res.string.create_shift_add_new_location))
            }
        } else {
            Text(
                stringResource(Res.string.warehouse_personal_org_limit_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
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
            initialMapPosition = state.pickedWarehouse?.let { Position(longitude = it.lon, latitude = it.lat) },
            onQueryChange = onWarehouseQueryChange,
            onPick = onPickWarehouseAddress,
        )
        if (state.warehouses.isNotEmpty()) {
            TextButton(onClick = { state.warehouses.firstOrNull()?.let { onSelectWarehouse(it.id) } }) {
                Text(stringResource(Res.string.create_shift_use_saved_location))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrivalSection(
    state: AddPackageUiState,
    onSetArrivalDate: (Long?) -> Unit,
    onSetArrivalTime: (Int, Int) -> Unit,
) {
    SectionLabel(stringResource(Res.string.package_section_arrival))
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateTimeField(
            text = state.arrivalDateMillis?.let { epochMillisToDisplayDate(it) }
                ?: stringResource(Res.string.create_shift_pick_date),
            onClick = { showDate = true },
            modifier = Modifier.weight(1f),
        )
        DateTimeField(
            text = formatHourMinute(state.arrivalHour, state.arrivalMinute),
            onClick = { showTime = true },
        )
    }
    if (showDate) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = state.arrivalDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { onSetArrivalDate(dp.selectedDateMillis); showDate = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) { DatePicker(state = dp) }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = state.arrivalHour,
            initialMinute = state.arrivalMinute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { onSetArrivalTime(timeState.hour, timeState.minute); showTime = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun AddressAutocomplete(
    label: String,
    query: String,
    suggestions: List<AddressSuggestion>,
    searching: Boolean,
    hasSelection: Boolean,
    initialMapPosition: Position?,
    onQueryChange: (String) -> Unit,
    onPick: (AddressSuggestion) -> Unit,
) {
    var showLocationPicker by remember { mutableStateOf(false) }
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
        TextButton(onClick = { showLocationPicker = true }) {
            Icon(LocationPinIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.address_pick_on_map))
        }
        if (!searching && !hasSelection && suggestions.isEmpty() && query.trim().length >= ADDRESS_MIN_QUERY_LENGTH) {
            Text(
                text = stringResource(Res.string.address_autocomplete_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            )
        } else {
            suggestions.take(5).forEach { s ->
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp, horizontal = 4.dp),
                )
                HorizontalDivider()
            }
        }
    }
    if (showLocationPicker) {
        LocationPickerDialog(
            initialPosition = initialMapPosition,
            onDismiss = { showLocationPicker = false },
            onConfirm = {
                onPick(it)
                showLocationPicker = false
            },
        )
    }
}

/** Mirrors [org.hikyaku.mobile.packages.add.AddPackageViewModel]'s geocode debounce threshold. */
private const val ADDRESS_MIN_QUERY_LENGTH = 3

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

@Composable
private fun OptionRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}


/**
 * Confirms the created package and, crucially, says what happened to it: `POST /api/v1/packages`
 * assigns it to a shift on the spot, so the driver can be told "stop 4, arriving about 14:20"
 * instead of the old "saved, come back tomorrow". A queued or skipped outcome is not an error —
 * the package exists either way — so this is a confirmation, not a failure dialog.
 */
@Composable
private fun AssignmentOutcomeDialog(assignment: PackageAssignmentDisplay, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.package_outcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(assignmentHeadline(assignment))
                assignmentReason(assignment)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (assignment.evictedCount > 0) {
                    Text(
                        text = stringResource(Res.string.package_outcome_evicted, assignment.evictedCount.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) } },
    )
}

/** The one-line verdict: on a shift (and where), queued, or not attempted. */
@Composable
private fun assignmentHeadline(assignment: PackageAssignmentDisplay): String {
    val stop = assignment.stopNumber?.toString()
    val eta = assignment.estimatedArrival
    return when {
        stop == null && assignment.outcome == AssignmentOutcomeDto.Outcome.skipped ->
            stringResource(Res.string.package_outcome_skipped)
        stop == null -> stringResource(Res.string.package_outcome_deferred)
        assignment.openedNewShift && eta != null ->
            stringResource(Res.string.package_outcome_new_shift_eta, stop, eta)
        assignment.openedNewShift -> stringResource(Res.string.package_outcome_new_shift, stop)
        eta != null -> stringResource(Res.string.package_outcome_assigned_eta, stop, eta)
        else -> stringResource(Res.string.package_outcome_assigned, stop)
    }
}

/** The supporting "why it isn't on a shift" line, or null when it is on one (or no reason was given). */
@Composable
private fun assignmentReason(assignment: PackageAssignmentDisplay): String? {
    if (assignment.isAssigned) return null
    return when (assignment.reason) {
        AssignmentOutcomeDto.Reason.no_capacity -> stringResource(Res.string.package_outcome_reason_no_capacity)
        AssignmentOutcomeDto.Reason.no_free_driver_vehicle -> stringResource(Res.string.package_outcome_reason_no_driver)
        AssignmentOutcomeDto.Reason.shift_allowance_exhausted -> stringResource(Res.string.package_outcome_reason_allowance)
        AssignmentOutcomeDto.Reason.no_geocode -> stringResource(Res.string.package_outcome_reason_no_geocode)
        AssignmentOutcomeDto.Reason.auto_assign_disabled -> stringResource(Res.string.package_outcome_reason_auto_assign)
        AssignmentOutcomeDto.Reason.deadline_infeasible -> stringResource(Res.string.package_outcome_reason_deadline)
        null -> null
    }
}
