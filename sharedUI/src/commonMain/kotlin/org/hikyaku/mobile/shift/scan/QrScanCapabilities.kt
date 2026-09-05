package org.hikyaku.mobile.shift.scan

/**
 * Whether this platform can drive a live camera QR scan via QRKit's `QrScanner`.
 *
 * Where QRKit has no working `QrCodeScanner` actual the scan overlay must fall back to manual
 * tracking-number entry. Mirrors [org.hikyaku.mobile.map.mapLayersSupported].
 */
expect val qrScanningSupported: Boolean
