package org.hikyaku.mobile.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_cancel
import hikyaku.sharedui.generated.resources.location_picker_confirm
import hikyaku.sharedui.generated.resources.location_picker_resolving
import hikyaku.sharedui.generated.resources.location_picker_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.hikyaku.mobile.geocode.GeocodeRepository
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position

private const val MAP_STYLE_URL = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

// Melbourne CBD — an arbitrary fallback centre when the field being picked has no address yet.
private const val DEFAULT_LAT = -37.8136
private const val DEFAULT_LON = 144.9631

private const val SETTLE_DEBOUNCE_MS = 300L

/**
 * Full-screen "drop a pin" address picker: the pin stays fixed at the centre of the screen while
 * the user drags the map underneath it (or taps to recentre), and the centred point is
 * reverse-geocoded to a human-readable address as soon as the camera settles. Confirming hands back
 * an [AddressSuggestion] shaped identically to picking a result from
 * [org.hikyaku.mobile.geocode.GeocodeRepository.autocomplete], so callers store it exactly the same
 * way (label + coordinates).
 *
 * [initialPosition] centres the map on the field's existing pick, if any; otherwise it falls back to
 * a wide view so the user can navigate to wherever they mean.
 */
@Composable
fun LocationPickerDialog(
    initialPosition: Position?,
    onDismiss: () -> Unit,
    onConfirm: (AddressSuggestion) -> Unit,
    geocodeRepository: GeocodeRepository = remember { GeocodeRepository() },
) {
    val startPosition = initialPosition ?: Position(longitude = DEFAULT_LON, latitude = DEFAULT_LAT)
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(target = startPosition, zoom = if (initialPosition != null) 16.0 else 11.0),
    )
    val scope = rememberCoroutineScope()
    var resolved by remember { mutableStateOf<AddressSuggestion?>(null) }
    var resolving by remember { mutableStateOf(true) }

    LaunchedEffect(cameraState.isCameraMoving) {
        if (cameraState.isCameraMoving) {
            resolving = true
            return@LaunchedEffect
        }
        delay(SETTLE_DEBOUNCE_MS)
        val target = cameraState.position.target
        resolved = geocodeRepository.reverseGeocode(target.latitude, target.longitude).getOrNull()
        resolving = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_cancel))
                    }
                    Text(stringResource(Res.string.location_picker_title), style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MaplibreMap(
                        modifier = Modifier.fillMaxSize(),
                        baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
                        cameraState = cameraState,
                        options = MapOptions(gestureOptions = GestureOptions.RotationLocked),
                        onMapClick = { position, _ ->
                            scope.launch {
                                cameraState.animateTo(CameraPosition(target = position, zoom = cameraState.position.zoom))
                            }
                            ClickResult.Consume
                        },
                    )
                    // The pin is a plain overlay icon (not a map marker layer) so it renders on every
                    // platform, including desktop where MapLibre Compose can't draw layers yet.
                    Icon(
                        imageVector = LocationPinIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).size(40.dp).offset(y = (-20).dp),
                    )
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (resolving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = resolved?.label ?: stringResource(Res.string.location_picker_resolving),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = { resolved?.let(onConfirm) },
                        enabled = resolved != null && !resolving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.location_picker_confirm))
                    }
                }
            }
        }
    }
}

private const val LOCATION_PIN_ICON_PATH =
    "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" +
        "M12 11.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"

private var locationPinIconCache: ImageVector? = null

/** The Material "location_on" glyph, defined inline since Compose Multiplatform doesn't ship material-icons-extended. */
internal val LocationPinIcon: ImageVector
    get() = locationPinIconCache ?: ImageVector.Builder(
        name = "LocationOn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(LOCATION_PIN_ICON_PATH),
        fill = SolidColor(Color.Black),
    ).build().also { locationPinIconCache = it }
