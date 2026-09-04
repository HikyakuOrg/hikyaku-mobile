package org.hikyaku.mobile.map

/**
 * Whether the current platform's MapLibre Compose renderer supports data sources and layers
 * (GeoJSON sources, [LineLayer]/[CircleLayer]/[SymbolLayer], [LocationPuck], etc.).
 *
 * Desktop (JVM) gained a full sources/layers implementation in maplibre-compose 0.14 (previously
 * calling `rememberGeoJsonSource` or adding a layer there threw [NotImplementedError]), but this
 * flag is kept `false` there pending its own verification pass — flipping it is a rendering
 * behavior change, not something the 0.15 ornament/location migration requires. Guard source/layer
 * content with this flag so a platform that still lacks the capability shows the base map instead
 * of crashing.
 */
expect val mapLayersSupported: Boolean
