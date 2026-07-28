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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.AuthViewModel
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.packages.PackageDetailScreen
import org.hikyaku.mobile.packages.PackageDetailViewModel
import org.hikyaku.mobile.packages.PackagesScreen
import org.hikyaku.mobile.packages.PackagesViewModel
import org.hikyaku.mobile.packages.add.AddPackageScreen
import org.hikyaku.mobile.packages.add.AddPackageViewModel
import org.hikyaku.mobile.shift.ShiftDetailScreen
import org.hikyaku.mobile.shift.ShiftDetailViewModel
import org.hikyaku.mobile.shift.create.CreateShiftScreen
import org.hikyaku.mobile.shift.create.CreateShiftViewModel
import org.hikyaku.mobile.vehicles.VehiclesScreen
import org.hikyaku.mobile.vehicles.VehiclesViewModel
import org.hikyaku.mobile.vehicles.add.AddVehicleScreen
import org.hikyaku.mobile.vehicles.add.AddVehicleViewModel
import org.hikyaku.mobile.vehicles.detail.VehicleDetailScreen
import org.hikyaku.mobile.vehicles.detail.VehicleDetailViewModel
import org.hikyaku.mobile.vehicles.maintenance.AddMaintenanceScreen
import org.hikyaku.mobile.vehicles.maintenance.AddMaintenanceViewModel

@Serializable
internal object HomeRoute

@Serializable
internal object CreateShiftRoute

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

/** [androidx.lifecycle.SavedStateHandle] key AddPackageRoute uses to signal PackagesRoute to refresh. */
private const val PACKAGE_CREATED_KEY = "package_created"

/** [androidx.lifecycle.SavedStateHandle] key AddVehicleRoute uses to signal VehiclesRoute to refresh. */
private const val VEHICLE_CREATED_KEY = "vehicle_created"

/** [androidx.lifecycle.SavedStateHandle] key AddMaintenanceRoute uses to signal VehicleDetailRoute to refresh. */
private const val MAINTENANCE_CREATED_KEY = "maintenance_created"

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
    val resumeSession by viewModel.resumeSession.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var drawerDestination by remember { mutableStateOf(DrawerDestination.Home) }

    // On cold start after a kill, jump straight back into an interrupted shift.
    LaunchedEffect(resumeSession) {
        val session = resumeSession ?: return@LaunchedEffect
        navController.navigate(ShiftDetailRoute(session.shiftId))
        viewModel.consumeResume()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                selected = drawerDestination,
                onHomeClick = {
                    scope.launch {
                        drawerState.close()
                        drawerDestination = DrawerDestination.Home
                        navController.navigate(HomeRoute) {
                            popUpTo(HomeRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onPackagesClick = {
                    scope.launch {
                        drawerState.close()
                        drawerDestination = DrawerDestination.Packages
                        navController.navigate(PackagesRoute) {
                            popUpTo(HomeRoute)
                            launchSingleTop = true
                        }
                    }
                },
                onVehiclesClick = {
                    scope.launch {
                        drawerState.close()
                        drawerDestination = DrawerDestination.Vehicles
                        navController.navigate(VehiclesRoute) {
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
                    onCreateShift = { navController.navigate(CreateShiftRoute) },
                    onDeleteShift = { shiftId, onResult ->
                        homeState.selectedOrgId?.let { orgId -> viewModel.deleteShift(orgId, shiftId, onResult) }
                            ?: onResult(null)
                    },
                )
            }
            composable<CreateShiftRoute>(
                // Same MapLibre-over-crossfade issue as ShiftDetailRoute (see HomeRoute's
                // popEnterTransition above): CreateShiftRoute's WarehouseMap stays opaque during
                // the default pop-exit fade and lingers over the incoming calendar. Skip it.
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val createViewModel: CreateShiftViewModel = viewModel {
                    CreateShiftViewModel(
                        orgId = homeState.selectedOrgId.orEmpty(),
                        orgSlug = homeState.selectedOrganisation?.slug.orEmpty(),
                    )
                }
                // Set by AddVehicleRoute's onDone, so returning from a successful add refreshes the vehicle list.
                val vehicleCreated by entry.savedStateHandle.getStateFlow(VEHICLE_CREATED_KEY, false).collectAsState()
                LaunchedEffect(vehicleCreated) {
                    if (vehicleCreated) {
                        createViewModel.refreshVehicles()
                        entry.savedStateHandle[VEHICLE_CREATED_KEY] = false
                    }
                }
                CreateShiftScreen(
                    viewModel = createViewModel,
                    onDone = {
                        navController.popBackStack()
                        homeState.selectedOrgId?.let(viewModel::loadShifts)
                    },
                    onCancel = {
                        createViewModel.discardDraft()
                        navController.popBackStack()
                    },
                    onAddVehicle = { navController.navigate(AddVehicleRoute) },
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
                val detailViewModel: PackageDetailViewModel = viewModel(key = trackingNumber) {
                    PackageDetailViewModel(trackingNumber = trackingNumber, orgSlug = orgSlug, orgName = orgName)
                }
                val detailState by detailViewModel.state.collectAsState()
                PackageDetailScreen(
                    state = detailState,
                    onBack = { navController.popBackStack() },
                    onRetry = detailViewModel::load,
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
            composable<AddVehicleRoute> {
                val addVehicleViewModel: AddVehicleViewModel = viewModel {
                    AddVehicleViewModel(orgId = homeState.selectedOrgId.orEmpty())
                }
                AddVehicleScreen(
                    viewModel = addVehicleViewModel,
                    onDone = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(VEHICLE_CREATED_KEY, true)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<AddPackageRoute> {
                val addPackageViewModel: AddPackageViewModel = viewModel {
                    AddPackageViewModel(orgId = homeState.selectedOrgId.orEmpty())
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
