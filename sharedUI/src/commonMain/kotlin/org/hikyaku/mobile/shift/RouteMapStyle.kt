package org.hikyaku.mobile.shift

import androidx.compose.ui.graphics.Color

/** Light CartoDB basemap shared by every embedded route map, so none of them render dark/illegible. */
internal const val MAP_STYLE_URL = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

/** Outbound leg + stop markers (deep blue) and depot marker (green), shared across route maps. */
internal val ROUTE_OUTBOUND_COLOR = Color(0xFF19398D)
internal val DEPOT_COLOR = Color(0xFF2E7D32)
