package org.hikyaku.mobile.map

/**
 * Whether the current platform's MapLibre Compose renderer supports data sources and layers
 * (GeoJSON sources, [LineLayer]/[CircleLayer]/[SymbolLayer], [LocationPuck], etc.).
 *
 * These are implemented on Android/iOS but **not yet on Desktop (JVM)** in MapLibre Compose —
 * calling `rememberGeoJsonSource` or adding a layer there throws [NotImplementedError]. Guard
 * source/layer content with this flag so desktop shows the base map instead of crashing.
 */
expect val mapLayersSupported: Boolean
