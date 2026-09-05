package org.hikyaku.mobile

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.AuthViewModel
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.navigation.LastRoute
import org.hikyaku.mobile.packages.PackageDetailScreen
import org.hikyaku.mobile.packages.PackageDetailViewModel
import org.hikyaku.mobile.packages.PackagesScreen
import org.hikyaku.mobile.packages.PackagesViewModel
import org.hikyaku.mobile.packages.add.AddPackageScreen
import org.hikyaku.mobile.packages.add.AddPackageViewModel
import org.hikyaku.mobile.shift.ShiftDetailScreen
import org.hikyaku.mobile.shift.ShiftDetailViewModel
import org.hikyaku.mobile.vehicles.VehiclesScreen
import org.hikyaku.mobile.vehicles.VehiclesViewModel
import org.hikyaku.mobile.vehicles.add.AddVehicleScreen
import org.hikyaku.mobile.vehicles.add.AddVehicleViewModel
import org.hikyaku.mobile.vehicles.detail.VehicleDetailScreen
import org.hikyaku.mobile.vehicles.detail.VehicleDetailViewModel
import org.hikyaku.mobile.vehicles.maintenance.AddMaintenanceScreen
import org.hikyaku.mobile.vehicles.maintenance.AddMaintenanceViewModel
import org.hikyaku.mobile.warehouse.WarehousesScreen
import org.hikyaku.mobile.warehouse.WarehousesViewModel
import org.hikyaku.mobile.warehouse.add.AddWarehouseScreen
import org.hikyaku.mobile.warehouse.add.AddWarehouseViewModel

@Serializable
internal object HomeRoute

@Serializable
internal data class ShiftDetailRoute(val shiftId: String)

@Serializable
internal object PackagesRoute

@Serializable
internal object AddPackageRoute

@Serializable
internal data class PackageDetailRoute(val trackingNumber: String)

@Serializable
internal object VehiclesRoute

@Serializable
internal object AddVehicleRoute

@Serializable
internal data class VehicleDetailRoute(val vehicleId: String)

@Serializable
internal data class AddMaintenanceRoute(val vehicleId: String)

@Serializable
internal object WarehousesRoute

@Serializable
internal object AddWarehouseRoute

/**
 * [androidx.lifecycle.SavedStateHandle] key used to signal PackagesRoute to refresh: set by
 * AddPackageRoute on a successful create, and by PackageDetailRoute on a successful delete.
 */
private const val PACKAGE_CREATED_KEY = "package_created"

/** [androidx.lifecycle.SavedStateHandle] key AddVehicleRoute uses to signal VehiclesRoute to refresh. */
private const val VEHICLE_CREATED_KEY = "vehicle_created"

/** [androidx.lifecycle.SavedStateHandle] key AddMaintenanceRoute uses to signal VehicleDetailRoute to refresh. */
private const val MAINTENANCE_CREATED_KEY = "maintenance_created"

/**
 * [androidx.lifecycle.SavedStateHandle] key AddWarehouseRoute uses to signal whichever screen
 * navigated to it (WarehousesRoute, or AddVehicleRoute's "add a warehouse first" prompt) to refresh.
 */
private const val WAREHOUSE_CREATED_KEY = "warehouse_created"

/**
 * Navigation for the authenticated area: the shift list (home) and a shift's detail page.
 * Lives here rather than in [App] so tapping a shift can push a detail destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavGraph(
    user: AuthState.Authenticated,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val homeState by viewModel.homeState.collectAsState()
    val shiftState by viewModel.shiftState.collectAsState()
    val routePreviews by viewModel.routePreviews.collectAsState()
    val resumeSession by viewModel.resumeSession.collectAsState()
    val initialRoute by viewModel.initialRoute.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Derived from the actual back stack (rather than tracked separately) so it stays correct
    // when the destination changes some way other than a drawer click, e.g. the system back button.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val drawerDestination = currentBackStackEntry?.toDrawerDestination() ?: DrawerDestination.Home

    // On cold start after a kill, jump straight back into an interrupted shift.
    LaunchedEffect(resumeSession) {
        val session = resumeSession ?: return@LaunchedEffect
        navController.navigate(ShiftDetailRoute(session.shiftId))
        viewModel.consumeResume()
    }

    // Remember whichever screen was on top, so a kill while backgrounded doesn't drop the
    // user back to Home. ShiftDetailRoute is excluded here since resumeSession above already
    // owns restoring into a shift.
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            entry.toLastRoute()?.let(viewModel::saveLastRoute)
        }
    }
    LaunchedEffect(initialRoute) {
        val route = initialRoute ?: return@LaunchedEffect
        route.toNavRoute()?.let { navController.navigate(it) }
        viewModel.consumeInitialRoute()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                selected = drawerDestination,
                onHomeClick = {
                    scope.launch {
                        drawerState.close()
                        navController.navigate(HomeRoute) {
                            popUpTo(HomeRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onPackagesClick = {
                    scope.launch {
                        drawerState.close()
                        navController.navigate(PackagesRoute) {
                            popUpTo(HomeRoute)
                            launchSingleTop = true
                        }
                    }
                },
                onVehiclesClick = {
                    scope.launch {
                        drawerState.close()
                        navController.navigate(VehiclesRoute) {
                            popUpTo(HomeRoute)
                            launchSingleTop = true
                        }
                    }
                },
                onWarehousesClick = {
                    scope.launch {
                        drawerState.close()
                        navController.navigate(WarehousesRoute) {
                            popUpTo(HomeRoute)
                            launchSingleTop = true
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
        ) {
            composable<HomeRoute>(
                // The MapLibre surface on ShiftDetailRoute doesn't composite with the default
                // crossfade (it stays fully opaque and renders on top), so popping back to Home
                // shows the map lingering over the calendar. Skip the animation on that pop.
                popEnterTransition = { EnterTransition.None },
            ) {
                HomeScreen(
                    user = user,
                    homeState = homeState,
                    shiftState = shiftState,
                    routePreviews = routePreviews,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSignOut = viewModel::signOut,
                    onRetryOrgs = viewModel::loadOrganisations,
                    onSelectOrg = viewModel::selectOrganisation,
                    onRefreshShifts = viewModel::refreshShifts,
                    onSaveDisplayName = viewModel::updateDisplayName,
                    onUploadAvatar = viewModel::uploadAvatar,
                    onShiftClick = { shiftId ->
                        navController.navigate(ShiftDetailRoute(shiftId))
                    },
                    onDeleteShift = { shiftId, onResult ->
                        homeState.selectedOrgId?.let { orgId -> viewModel.deleteShift(orgId, shiftId, onResult) }
                            ?: onResult(null)
                    },
                )
            }
            composable<PackagesRoute> { entry ->
                val packagesViewModel: PackagesViewModel = viewModel(key = homeState.selectedOrgId) {
                    PackagesViewModel(orgId = homeState.selectedOrgId.orEmpty())
                }
                val packagesState by packagesViewModel.state.collectAsState()
                // Set by AddPackageRoute's onDone, so returning from a successful add refreshes the list.
                val packageCreated by entry.savedStateHandle.getStateFlow(PACKAGE_CREATED_KEY, false).collectAsState()
                LaunchedEffect(packageCreated) {
                    if (packageCreated) {
                        packagesViewModel.loadFirstPage()
                        entry.savedStateHandle[PACKAGE_CREATED_KEY] = false
                    }
                }
                PackagesScreen(
                    state = packagesState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAddPackage = { navController.navigate(AddPackageRoute) },
                    onRetry = packagesViewModel::loadFirstPage,
                    onLoadMore = packagesViewModel::loadNextPage,
                    onRefresh = packagesViewModel::refresh,
                    onPackageClick = { pkg -> navController.navigate(PackageDetailRoute(pkg.trackingNumber)) },
                )
            }
            composable<PackageDetailRoute> { entry ->
                val trackingNumber = entry.toRoute<PackageDetailRoute>().trackingNumber
                val orgSlug = homeState.selectedOrganisation?.slug ?: ""
                val orgName = homeState.selectedOrganisation?.displayName ?: ""
                val orgLogoUrl = homeState.selectedOrganisation?.brandingLogoUrl
                val isPersonalOrg = homeState.selectedOrganisation?.isPersonal == true
                val detailViewModel: PackageDetailViewModel = viewModel(key = trackingNumber) {
                    PackageDetailViewModel(
                        trackingNumber = trackingNumber,
                        orgSlug = orgSlug,
                        orgName = orgName,
                        orgLogoUrl = orgLogoUrl,
                        isPersonalOrg = isPersonalOrg,
                    )
                }
                val detailState by detailViewModel.state.collectAsState()
                // A successful delete pops back to the list and asks it to refresh, the same signal
                // AddPackageRoute's onDone sends — the list doesn't care whether an entry was added
                // or removed, only that it's stale.
                LaunchedEffect(detailState.isDeleted) {
                    if (detailState.isDeleted) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(PACKAGE_CREATED_KEY, true)
                        navController.popBackStack()
                    }
                }
                PackageDetailScreen(
                    state = detailState,
                    onBack = { navController.popBackStack() },
                    onRetry = detailViewModel::load,
                    onDeletePackage = detailViewModel::deletePackage,
                    onDismissDeleteError = detailViewModel::dismissDeleteError,
                )
            }
            composable<VehiclesRoute> { entry ->
                val vehiclesViewModel: VehiclesViewModel = viewModel(key = homeState.selectedOrgId) {
                    VehiclesViewModel(orgId = homeState.selectedOrgId.orEmpty())
                }
                val vehiclesState by vehiclesViewModel.state.collectAsState()
                // Set by AddVehicleRoute's onDone, so returning from a successful add refreshes the list.
                val vehicleCreated by entry.savedStateHandle.getStateFlow(VEHICLE_CREATED_KEY, false).collectAsState()
                LaunchedEffect(vehicleCreated) {
                    if (vehicleCreated) {
                        vehiclesViewModel.loadFirstPage()
                        entry.savedStateHandle[VEHICLE_CREATED_KEY] = false
                    }
                }
                VehiclesScreen(
                    state = vehiclesState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAddVehicle = { navController.navigate(AddVehicleRoute) },
                    onRetry = vehiclesViewModel::loadFirstPage,
                    onLoadMore = vehiclesViewModel::loadNextPage,
                    onRefresh = vehiclesViewModel::refresh,
                    onVehicleClick = { vehicle -> navController.navigate(VehicleDetailRoute(vehicle.id)) },
                )
            }
            composable<VehicleDetailRoute> { entry ->
                val vehicleId = entry.toRoute<VehicleDetailRoute>().vehicleId
                val detailViewModel: VehicleDetailViewModel = viewModel(key = vehicleId) {
                    VehicleDetailViewModel(vehicleId = vehicleId)
                }
                val detailState by detailViewModel.state.collectAsState()
                // Set by AddMaintenanceRoute's onDone, so returning from a successful add refreshes the history.
                val maintenanceCreated by entry.savedStateHandle.getStateFlow(MAINTENANCE_CREATED_KEY, false).collectAsState()
                LaunchedEffect(maintenanceCreated) {
                    if (maintenanceCreated) {
                        detailViewModel.refreshMaintenance()
                        entry.savedStateHandle[MAINTENANCE_CREATED_KEY] = false
                    }
                }
                VehicleDetailScreen(
                    state = detailState,
                    onBack = { navController.popBackStack() },
                    onRetry = detailViewModel::load,
                    onAddMaintenance = { navController.navigate(AddMaintenanceRoute(vehicleId)) },
                )
            }
            composable<AddMaintenanceRoute> { entry ->
                val vehicleId = entry.toRoute<AddMaintenanceRoute>().vehicleId
                val addMaintenanceViewModel: AddMaintenanceViewModel = viewModel(key = vehicleId) {
                    AddMaintenanceViewModel(orgId = homeState.selectedOrgId.orEmpty(), vehicleId = vehicleId)
                }
                AddMaintenanceScreen(
                    viewModel = addMaintenanceViewModel,
                    onDone = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(MAINTENANCE_CREATED_KEY, true)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<AddVehicleRoute> { entry ->
                val addVehicleViewModel: AddVehicleViewModel = viewModel {
                    AddVehicleViewModel(
                        orgId = homeState.selectedOrgId.orEmpty(),
                        isPersonalOrg = homeState.selectedOrganisation?.isPersonal == true,
                    )
                }
                // Set by AddWarehouseRoute's onDone, so returning from a successful add refreshes the warehouse list.
                val warehouseCreated by entry.savedStateHandle.getStateFlow(WAREHOUSE_CREATED_KEY, false).collectAsState()
                LaunchedEffect(warehouseCreated) {
                    if (warehouseCreated) {
                        addVehicleViewModel.refreshWarehouses()
                        entry.savedStateHandle[WAREHOUSE_CREATED_KEY] = false
                    }
                }
                AddVehicleScreen(
                    viewModel = addVehicleViewModel,
                    onDone = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(VEHICLE_CREATED_KEY, true)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                    onAddWarehouse = { navController.navigate(AddWarehouseRoute) },
                )
            }
            composable<AddPackageRoute> {
                val addPackageViewModel: AddPackageViewModel = viewModel {
                    AddPackageViewModel(
                        orgId = homeState.selectedOrgId.orEmpty(),
                        orgSlug = homeState.selectedOrganisation?.slug.orEmpty(),
                        isPersonalOrg = homeState.selectedOrganisation?.isPersonal == true,
                    )
                }
                AddPackageScreen(
                    viewModel = addPackageViewModel,
                    onDone = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(PACKAGE_CREATED_KEY, true)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<WarehousesRoute> { entry ->
                val warehousesViewModel: WarehousesViewModel = viewModel(key = homeState.selectedOrgId) {
                    WarehousesViewModel(
                        orgId = homeState.selectedOrgId.orEmpty(),
                        isPersonalOrg = homeState.selectedOrganisation?.isPersonal == true,
                    )
                }
                val warehousesState by warehousesViewModel.state.collectAsState()
                // Set by AddWarehouseRoute's onDone, so returning from a successful add refreshes the list.
                val warehouseCreated by entry.savedStateHandle.getStateFlow(WAREHOUSE_CREATED_KEY, false).collectAsState()
                LaunchedEffect(warehouseCreated) {
                    if (warehouseCreated) {
                        warehousesViewModel.load()
                        entry.savedStateHandle[WAREHOUSE_CREATED_KEY] = false
                    }
                }
                WarehousesScreen(
                    state = warehousesState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAddWarehouse = { navController.navigate(AddWarehouseRoute) },
                    onRetry = warehousesViewModel::load,
                    onRefresh = warehousesViewModel::refresh,
                )
            }
            composable<AddWarehouseRoute> {
                val addWarehouseViewModel: AddWarehouseViewModel = viewModel {
                    AddWarehouseViewModel(orgId = homeState.selectedOrgId.orEmpty())
                }
                AddWarehouseScreen(
                    viewModel = addWarehouseViewModel,
                    onDone = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(WAREHOUSE_CREATED_KEY, true)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<ShiftDetailRoute>(
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val shiftId = entry.toRoute<ShiftDetailRoute>().shiftId
                val orgSlug = homeState.selectedOrganisation?.slug ?: ""
                val orgName = homeState.selectedOrganisation?.displayName ?: ""
                val orgId = homeState.selectedOrgId ?: ""
                val detailViewModel: ShiftDetailViewModel = viewModel(key = shiftId) {
                    ShiftDetailViewModel(shiftId = shiftId, orgSlug = orgSlug, orgId = orgId, orgName = orgName)
                }
                ShiftDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                    onPackageClick = { trackingNumber -> navController.navigate(PackageDetailRoute(trackingNumber)) },
                    onVehicleClick = { vehicleId -> navController.navigate(VehicleDetailRoute(vehicleId)) },
                )
            }
        }
    }
}

/**
 * Matched by route pattern string rather than a type-checking API, since
 * [NavDestination] only exposes a generic `hasRoute<T>()` helper on some targets (Android) but
 * not others in this KMP build of navigation-compose.
 */
private fun NavDestination.matchesRoute(qualifiedName: String?): Boolean = route == qualifiedName

private fun NavDestination.matchesRouteWithArg(qualifiedName: String?): Boolean =
    route?.startsWith(qualifiedName + "/") == true

/**
 * Maps the current back stack entry to a [LastRoute], or null for destinations that aren't
 * restored this way.
 */
private fun NavBackStackEntry.toLastRoute(): LastRoute? = when {
    destination.matchesRoute(HomeRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.Home)
    destination.matchesRoute(PackagesRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.Packages)
    destination.matchesRoute(AddPackageRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.AddPackage)
    destination.matchesRouteWithArg(PackageDetailRoute::class.qualifiedName) ->
        LastRoute(LastRoute.Screen.PackageDetail, toRoute<PackageDetailRoute>().trackingNumber)
    destination.matchesRoute(VehiclesRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.Vehicles)
    destination.matchesRoute(AddVehicleRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.AddVehicle)
    destination.matchesRouteWithArg(VehicleDetailRoute::class.qualifiedName) ->
        LastRoute(LastRoute.Screen.VehicleDetail, toRoute<VehicleDetailRoute>().vehicleId)
    destination.matchesRouteWithArg(AddMaintenanceRoute::class.qualifiedName) ->
        LastRoute(LastRoute.Screen.AddMaintenance, toRoute<AddMaintenanceRoute>().vehicleId)
    destination.matchesRoute(WarehousesRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.Warehouses)
    destination.matchesRoute(AddWarehouseRoute::class.qualifiedName) -> LastRoute(LastRoute.Screen.AddWarehouse)
    // ShiftDetailRoute is intentionally excluded: the resumeSession flow above owns it.
    else -> null
}

/** Which drawer section owns the current back stack entry, for highlighting the right item. */
private fun NavBackStackEntry.toDrawerDestination(): DrawerDestination = when {
    destination.matchesRoute(PackagesRoute::class.qualifiedName) -> DrawerDestination.Packages
    destination.matchesRoute(AddPackageRoute::class.qualifiedName) -> DrawerDestination.Packages
    destination.matchesRouteWithArg(PackageDetailRoute::class.qualifiedName) -> DrawerDestination.Packages
    destination.matchesRoute(VehiclesRoute::class.qualifiedName) -> DrawerDestination.Vehicles
    destination.matchesRoute(AddVehicleRoute::class.qualifiedName) -> DrawerDestination.Vehicles
    destination.matchesRouteWithArg(VehicleDetailRoute::class.qualifiedName) -> DrawerDestination.Vehicles
    destination.matchesRouteWithArg(AddMaintenanceRoute::class.qualifiedName) -> DrawerDestination.Vehicles
    destination.matchesRoute(WarehousesRoute::class.qualifiedName) -> DrawerDestination.Warehouses
    destination.matchesRoute(AddWarehouseRoute::class.qualifiedName) -> DrawerDestination.Warehouses
    // HomeRoute and ShiftDetailRoute both belong to the Home section of the drawer.
    else -> DrawerDestination.Home
}

/** Reverses [toLastRoute]; null for Home (the NavHost start destination — nothing to do). */
private fun LastRoute.toNavRoute(): Any? = when (screen) {
    LastRoute.Screen.Home -> null
    LastRoute.Screen.Packages -> PackagesRoute
    LastRoute.Screen.AddPackage -> AddPackageRoute
    LastRoute.Screen.PackageDetail -> arg?.let(::PackageDetailRoute)
    LastRoute.Screen.Vehicles -> VehiclesRoute
    LastRoute.Screen.AddVehicle -> AddVehicleRoute
    LastRoute.Screen.VehicleDetail -> arg?.let(::VehicleDetailRoute)
    LastRoute.Screen.AddMaintenance -> arg?.let(::AddMaintenanceRoute)
    LastRoute.Screen.Warehouses -> WarehousesRoute
    LastRoute.Screen.AddWarehouse -> AddWarehouseRoute
}
