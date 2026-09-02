package org.hikyaku.mobile.vehicles.scan

/**
 * Whether this platform can run the live CameraX + ML Kit VIN scanner.
 *
 * False on desktop, where there is no camera pipeline and no ML Kit, so the add-vehicle form hides
 * the Scan affordance entirely and leaves the VIN as plain text entry. Mirrors
 * [org.hikyaku.mobile.shift.scan.qrScanningSupported].
 */
expect val vinScanningSupported: Boolean
