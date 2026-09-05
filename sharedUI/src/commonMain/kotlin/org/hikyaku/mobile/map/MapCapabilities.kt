package org.hikyaku.mobile.map

/**
 * Whether the current platform's MapLibre Compose renderer supports data sources and layers
 * (GeoJSON sources, [LineLayer]/[CircleLayer]/[SymbolLayer], [LocationPuck], etc.).
 *
 * Guard source/layer content with this flag so a platform that lacks the capability shows the
 * base map instead of crashing.
 */
expect val mapLayersSupported: Boolean
