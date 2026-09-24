package org.hikyaku.mobile.packages

import androidx.compose.runtime.Composable
import org.hikyaku.mobile.packages.model.PackageDetail

/**
 * Returns an action that renders a printable shipping label (title, "SHIP TO" recipient block and
 * a QR code) for [PackageDetail] and sends it to the system print dialog. No-op off Android.
 *
 * When [logoUrl] is set, the org logo is drawn in the middle of the QR code, as on the package
 * detail screen. A logo that fails to load leaves a plain QR code.
 */
@Composable
expect fun rememberPrintShippingLabel(logoUrl: String?): (detail: PackageDetail) -> Unit
