package org.hikyaku.mobile.vehicles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.cd_open_navigation_menu
import hikyaku.sharedui.generated.resources.vehicle_no_vehicles
import hikyaku.sharedui.generated.resources.vehicle_overview_title
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.vehicles.model.VehicleSummary
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    state: VehiclesUiState,
    onOpenDrawer: () -> Unit,
    onAddVehicle: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onVehicleClick: (VehicleSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Requests the next page once the user has scrolled near the end of what's loaded.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= state.vehicles.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.hasMore, state.vehicles.size) {
        if (shouldLoadMore && state.hasMore && state.vehicles.isNotEmpty()) onLoadMore()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.vehicle_overview_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(Res.string.cd_open_navigation_menu))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVehicle) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                when {
                    state.error != null && state.vehicles.isEmpty() -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        VehiclesErrorCard(message = state.error, onRetry = onRetry)
                    }

                    state.vehicles.isEmpty() -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.vehicle_no_vehicles),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.vehicles, key = { it.id }) { vehicle ->
                            VehicleCard(vehicle, onClick = { onVehicleClick(vehicle) })
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun VehiclesScreenPreview() {
    HikyakuTheme {
        VehiclesScreen(
            state = VehiclesUiState(
                vehicles = listOf(
                    VehicleSummary(
                        id = "1",
                        vehicleModel = "Sprinter 2500",
                        vehicleMake = "Mercedes-Benz",
                        vehicleTypeName = "Van",
                    ),
                    VehicleSummary(
                        id = "2",
                        vehicleModel = "Transit 350",
                        vehicleMake = "Ford",
                        vehicleTypeName = "Van",
                    ),
                    VehicleSummary(
                        id = "3",
                        vehicleModel = "NV200",
                        vehicleMake = "Nissan",
                        vehicleTypeName = "Cargo Van",
                    ),
                ),
            ),
            onOpenDrawer = {},
            onAddVehicle = {},
            onRetry = {},
            onLoadMore = {},
            onRefresh = {},
            onVehicleClick = {},
        )
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleSummary, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = vehicle.vehicleModel.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(vehicle.vehicleModel, style = MaterialTheme.typography.titleMedium)
                Text(
                    vehicle.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehiclesErrorCard(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}
