package org.hikyaku.mobile.vehicles.maintenance

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import hikyaku.sharedui.generated.resources.cd_maintenance_photo
import hikyaku.sharedui.generated.resources.maintenance_action_choose_photos
import hikyaku.sharedui.generated.resources.maintenance_action_take_photo
import hikyaku.sharedui.generated.resources.maintenance_add_title
import hikyaku.sharedui.generated.resources.maintenance_date_placeholder
import hikyaku.sharedui.generated.resources.maintenance_label_description
import hikyaku.sharedui.generated.resources.maintenance_label_odometer
import hikyaku.sharedui.generated.resources.maintenance_section_date_serviced
import hikyaku.sharedui.generated.resources.maintenance_section_photos
import hikyaku.sharedui.generated.resources.maintenance_submit
import org.hikyaku.mobile.shift.rememberImagePicker
import org.hikyaku.mobile.shift.rememberPhotoCapture
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.hikyaku.mobile.util.epochMillisToIsoDate
import org.jetbrains.compose.resources.stringResource

/**
 * Single-page add-maintenance-record form: odometer and description are free text, and the date
 * serviced is picked via a Material3 date picker. Submits through [AddMaintenanceViewModel], which
 * persists the row via `VehicleRepository`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaintenanceScreen(
    viewModel: AddMaintenanceViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ToastEffect(state.error)

    AddMaintenanceScreenContent(
        state = state,
        onSubmit = viewModel::submit,
        onCancel = onCancel,
        onSetOdometer = viewModel::setOdometer,
        onSetDescription = viewModel::setDescription,
        onSetDateServiced = viewModel::setDateServiced,
        onAddImages = viewModel::addImages,
        onRemoveImage = viewModel::removeImage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMaintenanceScreenContent(
    state: AddMaintenanceUiState,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onSetOdometer: (String) -> Unit,
    onSetDescription: (String) -> Unit,
    onSetDateServiced: (Long?) -> Unit,
    onAddImages: (List<ByteArray>) -> Unit,
    onRemoveImage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.maintenance_add_title)) },
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
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.maintenance_submit))
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
            OutlinedTextField(
                value = state.odometer,
                onValueChange = onSetOdometer,
                label = { Text(stringResource(Res.string.maintenance_label_odometer)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onSetDescription,
                label = { Text(stringResource(Res.string.maintenance_label_description)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(Res.string.maintenance_section_date_serviced))
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.dateServicedMillis?.let(::epochMillisToIsoDate) ?: stringResource(Res.string.maintenance_date_placeholder))
            }
            SectionLabel(stringResource(Res.string.maintenance_section_photos))
            ImagesSection(state, onAddImages, onRemoveImage)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.dateServicedMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { onSetDateServiced(datePickerState.selectedDateMillis); showDatePicker = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }
}

@Preview
@Composable
private fun AddMaintenanceScreenPreview() {
    HikyakuTheme {
        AddMaintenanceScreenContent(
            state = AddMaintenanceUiState(
                odometer = "32500",
                description = "Oil change and tire rotation",
                dateServicedMillis = 1_778_544_000_000L, // 2026-05-12
            ),
            onSubmit = {},
            onCancel = {},
            onSetOdometer = {},
            onSetDescription = {},
            onSetDateServiced = {},
            onAddImages = {},
            onRemoveImage = {},
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ImagesSection(
    state: AddMaintenanceUiState,
    onAddImages: (List<ByteArray>) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    val capturePhoto = rememberPhotoCapture { bytes -> if (bytes != null) onAddImages(listOf(bytes)) }
    val pickImages = rememberImagePicker { images -> onAddImages(images) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = capturePhoto) { Text(stringResource(Res.string.maintenance_action_take_photo)) }
        OutlinedButton(onClick = pickImages) { Text(stringResource(Res.string.maintenance_action_choose_photos)) }
    }
    if (state.images.isNotEmpty()) {
        val photoDescription = stringResource(Res.string.cd_maintenance_photo)
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
