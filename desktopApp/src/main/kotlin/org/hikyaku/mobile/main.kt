package org.hikyaku.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.hikyaku.mobile.shift.ShiftDetailMapsHarness
import org.hikyaku.mobile.ShiftRouteMapPreviewHarness
import org.hikyaku.mobile.theme.HikyakuTheme
import org.maplibre.compose.desktop.ProvideMapHost
import org.maplibre.compose.desktop.rememberAwtComposeMapHost

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hikyaku",
    ) {
        // Every desktop map renders against a ComposeMapHost bound to this window's GPU context.
        ProvideMapHost(host = rememberAwtComposeMapHost(window)) {
            // SCRATCH: swapped in to visually QA the mapLayersSupported desktop flip.
            HikyakuTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ShiftDetailMapsHarness()
                    ShiftRouteMapPreviewHarness()
                }
            }
        }
    }
}