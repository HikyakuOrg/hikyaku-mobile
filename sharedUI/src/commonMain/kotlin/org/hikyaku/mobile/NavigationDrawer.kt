package org.hikyaku.mobile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.nav_home
import hikyaku.sharedui.generated.resources.nav_packages
import hikyaku.sharedui.generated.resources.nav_vehicles
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.stringResource

/**
 * M3 modal navigation drawer content shown after sign-in.
 * https://m3.material.io/components/navigation-drawer/overview
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerContent(
    onHomeClick: () -> Unit,
    onPackagesClick: () -> Unit,
    onVehiclesClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: DrawerDestination = DrawerDestination.Home,
) {
    ModalDrawerSheet(modifier = modifier) {
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_home)) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            selected = selected == DrawerDestination.Home,
            onClick = onHomeClick,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_packages)) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            selected = selected == DrawerDestination.Packages,
            onClick = onPackagesClick,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_vehicles)) },
            icon = { Icon(Icons.Filled.Build, contentDescription = null) },
            selected = selected == DrawerDestination.Vehicles,
            onClick = onVehiclesClick,
        )
    }
}

/** Which drawer destination is currently shown, so its [NavigationDrawerItem] highlights. */
enum class DrawerDestination { Home, Packages, Vehicles }

@Preview
@Composable
private fun AppDrawerContentPreview() {
    HikyakuTheme {
        AppDrawerContent(
            onHomeClick = {},
            onPackagesClick = {},
            onVehiclesClick = {},
            selected = DrawerDestination.Home,
        )
    }
}
