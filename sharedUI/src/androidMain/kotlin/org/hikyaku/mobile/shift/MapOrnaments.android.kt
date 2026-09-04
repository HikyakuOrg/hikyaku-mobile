package org.hikyaku.mobile.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MaplibreLogo

/** [MapOverlay.Default], minus the top-left scale bar. */
actual fun routeMapOverlay(): MapOverlay = MapOverlay {
    DisappearingCompassButton(cameraState = cameraState, modifier = Modifier.align(Alignment.TopEnd))

    // Read before entering the Row, whose scope shadows this one.
    val camera = cameraState
    val style = styleState
    Row(
        Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaplibreLogo()
        ExpandingAttributionButton(cameraState = camera, styleState = style)
    }
}
