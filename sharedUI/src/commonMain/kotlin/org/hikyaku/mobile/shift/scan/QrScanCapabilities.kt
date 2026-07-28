package org.hikyaku.mobile.shift.scan

/**
 * Whether this platform can drive a live camera QR scan via QRKit's `QrScanner`.
 *
 * QRKit's JVM `QrCodeScanner` actual renders nothing on desktop, so the scan overlay must fall
 * back to manual tracking-number entry there. Mirrors [org.hikyaku.mobile.map.mapLayersSupported].
 */
expect val qrScanningSupported: Boolean
