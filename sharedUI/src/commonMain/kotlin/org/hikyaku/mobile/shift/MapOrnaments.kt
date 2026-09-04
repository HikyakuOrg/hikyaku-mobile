package org.hikyaku.mobile.shift

import org.maplibre.compose.overlay.MapOverlay

/**
 * Overlay controls for the route map. Android hides the top-left scale bar; desktop draws
 * [MapOverlay.Default] unchanged.
 */
expect fun routeMapOverlay(): MapOverlay
