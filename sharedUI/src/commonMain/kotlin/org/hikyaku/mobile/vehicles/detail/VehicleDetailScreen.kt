package org.hikyaku.mobile.vehicles.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.cd_maintenance_photo
import hikyaku.sharedui.generated.resources.cd_vehicle_photo
import hikyaku.sharedui.generated.resources.vehicle_detail_label_gross_limit
import hikyaku.sharedui.generated.resources.vehicle_detail_label_plate
import hikyaku.sharedui.generated.resources.vehicle_detail_label_vin
import hikyaku.sharedui.generated.resources.vehicle_detail_label_warehouse
import hikyaku.sharedui.generated.resources.vehicle_detail_label_year
import hikyaku.sharedui.generated.resources.vehicle_detail_no_maintenance
import hikyaku.sharedui.generated.resources.vehicle_detail_odometer_value
import hikyaku.sharedui.generated.resources.vehicle_detail_section_maintenance
import hikyaku.sharedui.generated.resources.vehicle_detail_title
import io.github.jan.supabase.storage.StorageItem
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.vehicles.model.MaintenanceRecord
import org.hikyaku.mobile.vehicles.model.VehicleDetail
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    state: VehicleDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddMaintenance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.vehicle_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.vehicle != null) {
                FloatingActionButton(onClick = onAddMaintenance) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null && state.vehicle == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                }
            }

            state.vehicle != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { VehicleHeaderCard(state.vehicle, images = state.vehicleImages) }
                item {
                    Text(
                        text = stringResource(Res.string.vehicle_detail_section_maintenance),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.maintenanceRecords.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.vehicle_detail_no_maintenance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.maintenanceRecords, key = { it.id }) { record ->
                        MaintenanceCard(record, images = state.maintenanceImages[record.id].orEmpty())
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun VehicleDetailScreenPreview() {
    HikyakuTheme {
        VehicleDetailScreen(
            state = VehicleDetailUiState(
                vehicle = VehicleDetail(
                    id = "1",
                    vehiclePlate = "ABC-1234",
                    vehicleIdentificationNumber = "1FTBW2CM5NKA12345",
                    vehicleMake = "Ford",
                    vehicleModel = "Transit 350",
                    vehicleYear = 2022,
                    vehicleGrossLimits = 4500.0,
                    vehicleTypeName = "Van",
                    warehouseName = "Downtown Warehouse",
                ),
                vehicleImages = emptyList(),
                maintenanceRecords = listOf(
                    MaintenanceRecord(
                        id = "m1",
                        odometer = 32500.0,
                        description = "Oil change and tire rotation",
                        dateServiced = "2026-05-12",
                    ),
                    MaintenanceRecord(
                        id = "m2",
                        odometer = 28000.0,
                        description = "Brake pad replacement",
                        dateServiced = "2026-02-03",
                    ),
                ),
                maintenanceImages = emptyMap(),
            ),
            onBack = {},
            onRetry = {},
            onAddMaintenance = {},
        )
    }
}

@Composable
private fun VehicleHeaderCard(vehicle: VehicleDetail, images: List<StorageItem>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (images.isNotEmpty()) {
                val photoDescription = stringResource(Res.string.cd_vehicle_photo)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { item ->
                        AsyncImage(
                            model = item,
                            contentDescription = photoDescription,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            Text(vehicle.vehicleModel, style = MaterialTheme.typography.titleLarge)
            val subtitle = listOfNotNull(vehicle.vehicleMake, vehicle.vehicleTypeName).joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vehicle.vehiclePlate?.let { Text(stringResource(Res.string.vehicle_detail_label_plate, it), style = MaterialTheme.typography.bodySmall) }
            vehicle.vehicleIdentificationNumber?.let { Text(stringResource(Res.string.vehicle_detail_label_vin, it), style = MaterialTheme.typography.bodySmall) }
            vehicle.vehicleYear?.let { Text(stringResource(Res.string.vehicle_detail_label_year, it.toString()), style = MaterialTheme.typography.bodySmall) }
            vehicle.vehicleGrossLimits?.let {
                Text(
                    stringResource(Res.string.vehicle_detail_label_gross_limit, formatDecimal(it)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            vehicle.warehouseName?.let { Text(stringResource(Res.string.vehicle_detail_label_warehouse, it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MaintenanceCard(record: MaintenanceRecord, images: List<StorageItem>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.dateServiced, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(Res.string.vehicle_detail_odometer_value, formatDecimal(record.odometer)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(record.description, style = MaterialTheme.typography.bodyMedium)
            if (images.isNotEmpty()) {
                val photoDescription = stringResource(Res.string.cd_maintenance_photo)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { item ->
                        AsyncImage(
                            model = item,
                            contentDescription = photoDescription,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** Drops a trailing `.0` for whole numbers, e.g. odometer/gross-limit values entered without decimals. */
private fun formatDecimal(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
