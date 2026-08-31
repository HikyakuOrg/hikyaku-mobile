package org.hikyaku.mobile.vehicles.add

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import hikyaku.sharedui.generated.resources.action_remove
import hikyaku.sharedui.generated.resources.cd_vehicle_photo
import hikyaku.sharedui.generated.resources.vehicle_action_choose_photos
import hikyaku.sharedui.generated.resources.vehicle_action_take_photo
import hikyaku.sharedui.generated.resources.vehicle_add_title
import hikyaku.sharedui.generated.resources.vehicle_add_warehouse_cta
import hikyaku.sharedui.generated.resources.vehicle_label_gross_limits
import hikyaku.sharedui.generated.resources.vehicle_label_make
import hikyaku.sharedui.generated.resources.vehicle_label_model
import hikyaku.sharedui.generated.resources.vehicle_label_plate
import hikyaku.sharedui.generated.resources.vehicle_label_vin
import hikyaku.sharedui.generated.resources.vehicle_label_year
import hikyaku.sharedui.generated.resources.vehicle_no_warehouses_body
import hikyaku.sharedui.generated.resources.vehicle_no_warehouses_title
import hikyaku.sharedui.generated.resources.vehicle_section_photos
import hikyaku.sharedui.generated.resources.vehicle_section_type
import hikyaku.sharedui.generated.resources.vehicle_section_warehouse
import hikyaku.sharedui.generated.resources.vehicle_submit
import org.hikyaku.mobile.shift.rememberImagePicker
import org.hikyaku.mobile.shift.rememberPhotoCapture
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.hikyaku.mobile.vehicles.model.VehicleTypeOption
import org.hikyaku.mobile.vehicles.model.VehicleWarehouseOption
import org.jetbrains.compose.resources.stringResource

/**
 * Single-page add-vehicle form covering every persisted `vehicles` column: model, make, plate, VIN
 * and year are free text, gross limits is numeric, the vehicle type (routing profile) is a required
 * dropdown, and the home warehouse is a required radio-button pick. Submits through
 * [AddVehicleViewModel], which persists the row via `VehicleRepository`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    viewModel: AddVehicleViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onAddWarehouse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ToastEffect(state.error)

    AddVehicleScreenContent(
        state = state,
        onSubmit = viewModel::submit,
        onCancel = onCancel,
        onAddWarehouse = onAddWarehouse,
        onSetModel = viewModel::setModel,
        onSetMake = viewModel::setMake,
        onSetPlate = viewModel::setPlate,
        onSetVin = viewModel::setVin,
        onSetYear = viewModel::setYear,
        onSetGrossLimits = viewModel::setGrossLimits,
        onSelectVehicleType = viewModel::selectVehicleType,
        onSelectWarehouse = viewModel::selectWarehouse,
        onAddImages = viewModel::addImages,
        onRemoveImage = viewModel::removeImage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVehicleScreenContent(
    state: AddVehicleUiState,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onAddWarehouse: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetMake: (String) -> Unit,
    onSetPlate: (String) -> Unit,
    onSetVin: (String) -> Unit,
    onSetYear: (String) -> Unit,
    onSetGrossLimits: (String) -> Unit,
    onSelectVehicleType: (String) -> Unit,
    onSelectWarehouse: (String) -> Unit,
    onAddImages: (List<ByteArray>) -> Unit,
    onRemoveImage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.vehicle_add_title)) },
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
                        Text(stringResource(Res.string.vehicle_submit))
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
                OutlinedTextField(
                    value = state.model,
                    onValueChange = onSetModel,
                    label = { Text(stringResource(Res.string.vehicle_label_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.make,
                    onValueChange = onSetMake,
                    label = { Text(stringResource(Res.string.vehicle_label_make)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.plate,
                        onValueChange = onSetPlate,
                        label = { Text(stringResource(Res.string.vehicle_label_plate)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.year,
                        onValueChange = onSetYear,
                        label = { Text(stringResource(Res.string.vehicle_label_year)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = state.vin,
                    onValueChange = onSetVin,
                    label = { Text(stringResource(Res.string.vehicle_label_vin)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.grossLimits,
                    onValueChange = onSetGrossLimits,
                    label = { Text(stringResource(Res.string.vehicle_label_gross_limits)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel(stringResource(Res.string.vehicle_section_type))
                VehicleTypeDropdown(state, onSelectVehicleType)
                SectionLabel(stringResource(Res.string.vehicle_section_warehouse))
                if (state.warehouses.isEmpty()) {
                    NoWarehousesCard(onAddWarehouse = onAddWarehouse)
                } else {
                    WarehouseOptions(state, onSelectWarehouse)
                }
                SectionLabel(stringResource(Res.string.vehicle_section_photos))
                ImagesSection(state, onAddImages, onRemoveImage)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Preview
@Composable
private fun AddVehicleScreenPreview() {
    HikyakuTheme {
        AddVehicleScreenContent(
            state = AddVehicleUiState(
                model = "Transit 350",
                make = "Ford",
                plate = "ABC-1234",
                vin = "1FTBW2CM5NKA12345",
                year = "2022",
                grossLimits = "4500",
                vehicleTypes = listOf(
                    VehicleTypeOption(id = "vt1", name = "Van"),
                    VehicleTypeOption(id = "vt2", name = "Truck"),
                ),
                selectedVehicleTypeId = "vt1",
                warehouses = listOf(
                    VehicleWarehouseOption(id = "w1", name = "Downtown Warehouse", address = "123 Main St"),
                    VehicleWarehouseOption(id = "w2", name = "Airport Depot", address = "456 Cargo Way"),
                ),
                selectedWarehouseId = "w1",
            ),
            onSubmit = {},
            onCancel = {},
            onAddWarehouse = {},
            onSetModel = {},
            onSetMake = {},
            onSetPlate = {},
            onSetVin = {},
            onSetYear = {},
            onSetGrossLimits = {},
            onSelectVehicleType = {},
            onSelectWarehouse = {},
            onAddImages = {},
            onRemoveImage = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleTypeDropdown(state: AddVehicleUiState, onSelectVehicleType: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.vehicleTypes.firstOrNull { it.id == state.selectedVehicleTypeId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.vehicleTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = { onSelectVehicleType(type.id); expanded = false },
                )
            }
        }
    }
}

/**
 * Shown in place of [WarehouseOptions] when the org has no warehouses: a vehicle can't be created
 * without a home warehouse, so this explains why and sends the user straight to the add-warehouse
 * screen rather than letting them discover the problem only after submitting.
 */
@Composable
private fun NoWarehousesCard(onAddWarehouse: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.vehicle_no_warehouses_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.vehicle_no_warehouses_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddWarehouse) { Text(stringResource(Res.string.vehicle_add_warehouse_cta)) }
        }
    }
}

@Composable
private fun WarehouseOptions(state: AddVehicleUiState, onSelectWarehouse: (String) -> Unit) {
    state.warehouses.forEach { w ->
        OptionRow(
            selected = w.id == state.selectedWarehouseId,
            text = "${w.name} — ${w.address}",
            onClick = { onSelectWarehouse(w.id) },
        )
    }
}

@Composable
private fun OptionRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

@Composable
private fun ImagesSection(
    state: AddVehicleUiState,
    onAddImages: (List<ByteArray>) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    val capturePhoto = rememberPhotoCapture { bytes -> if (bytes != null) onAddImages(listOf(bytes)) }
    val pickImages = rememberImagePicker { images -> onAddImages(images) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = capturePhoto) { Text(stringResource(Res.string.vehicle_action_take_photo)) }
        OutlinedButton(onClick = pickImages) { Text(stringResource(Res.string.vehicle_action_choose_photos)) }
    }
    if (state.images.isNotEmpty()) {
        val photoDescription = stringResource(Res.string.cd_vehicle_photo)
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
