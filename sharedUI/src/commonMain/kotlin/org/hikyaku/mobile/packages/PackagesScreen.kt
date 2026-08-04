package org.hikyaku.mobile.packages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.cd_open_navigation_menu
import hikyaku.sharedui.generated.resources.package_2
import hikyaku.sharedui.generated.resources.package_no_packages
import hikyaku.sharedui.generated.resources.package_optimise_button
import hikyaku.sharedui.generated.resources.package_overview_title
import org.hikyaku.mobile.packages.model.PackageSummary
import org.hikyaku.mobile.packages.optimisation.OptimisationProgressDialog
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagesScreen(
    state: PackagesUiState,
    onOpenDrawer: () -> Unit,
    onAddPackage: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onPackageClick: (PackageSummary) -> Unit,
    onOptimise: () -> Unit,
    onDismissOptimisation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    ToastEffect(state.optimisationToast)
    state.optimisation?.let { progress ->
        OptimisationProgressDialog(progress = progress, onClose = onDismissOptimisation)
    }

    // Requests the next page once the user has scrolled near the end of what's loaded.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= state.packages.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.hasMore, state.packages.size) {
        if (shouldLoadMore && state.hasMore && state.packages.isNotEmpty()) onLoadMore()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.package_overview_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(Res.string.cd_open_navigation_menu))
                    }
                },
                actions = {
                    TextButton(onClick = onOptimise) {
                        Text(stringResource(Res.string.package_optimise_button))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPackage) {
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
                    state.error != null && state.packages.isEmpty() -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        PackagesErrorCard(message = state.error, onRetry = onRetry)
                    }

                    state.packages.isEmpty() -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.package_no_packages),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.packages, key = { it.id }) { pkg ->
                            PackageCard(pkg, onClick = { onPackageClick(pkg) })
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
private fun PackagesScreenPreview() {
    HikyakuTheme {
        PackagesScreen(
            state = PackagesUiState(
                packages = listOf(
                    PackageSummary(id = "1", trackingNumber = "TRK-2024-0001", createdAt = "2024-01-15T10:30:00"),
                    PackageSummary(id = "2", trackingNumber = "TRK-2024-0002", createdAt = "2024-01-16T14:20:00"),
                    PackageSummary(id = "3", trackingNumber = "TRK-2024-0003", createdAt = "2024-01-17T09:05:00"),
                ),
                hasMore = false,
            ),
            onOpenDrawer = {},
            onAddPackage = {},
            onRetry = {},
            onLoadMore = {},
            onRefresh = {},
            onPackageClick = {},
            onOptimise = {},
            onDismissOptimisation = {},
        )
    }
}

@Composable
private fun PackageCard(pkg: PackageSummary, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.package_2),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(pkg.trackingNumber, style = MaterialTheme.typography.titleMedium)
                Text(
                    pkg.createdDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PackagesErrorCard(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}
