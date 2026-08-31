package org.hikyaku.mobile.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.cd_open_navigation_menu
import hikyaku.sharedui.generated.resources.warehouse_no_warehouses
import hikyaku.sharedui.generated.resources.warehouse_overview_title
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.warehouse.model.WarehouseOption
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehousesScreen(
    state: WarehousesUiState,
    onOpenDrawer: () -> Unit,
    onAddWarehouse: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.warehouse_overview_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(Res.string.cd_open_navigation_menu))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canAddWarehouse) {
                FloatingActionButton(onClick = onAddWarehouse) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
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
                    state.error != null && state.warehouses.isEmpty() -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        WarehousesErrorCard(message = state.error, onRetry = onRetry)
                    }

                    state.warehouses.isEmpty() -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.warehouse_no_warehouses),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.warehouses, key = { it.id }) { warehouse -> WarehouseCard(warehouse) }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WarehousesScreenPreview() {
    HikyakuTheme {
        WarehousesScreen(
            state = WarehousesUiState(
                warehouses = listOf(
                    WarehouseOption(id = "w1", name = "Home", address = "123 Orchard Road, Singapore", lat = 1.3048, lng = 103.8318),
                ),
                canAddWarehouse = false,
            ),
            onOpenDrawer = {},
            onAddWarehouse = {},
            onRetry = {},
            onRefresh = {},
        )
    }
}

@Composable
private fun WarehouseCard(warehouse: WarehouseOption) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(warehouse.name, style = MaterialTheme.typography.titleMedium)
            Text(
                warehouse.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WarehousesErrorCard(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}
