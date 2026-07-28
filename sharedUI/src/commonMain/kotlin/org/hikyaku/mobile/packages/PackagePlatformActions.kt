package org.hikyaku.mobile.packages

import androidx.compose.runtime.Composable
import org.hikyaku.mobile.packages.model.PackageDetail

/**
 * Returns an action that renders a printable shipping label (title, "SHIP TO" recipient block and
 * a QR code) for [PackageDetail] and sends it to the system print dialog. No-op off Android.
 */
@Composable
expect fun rememberPrintShippingLabel(): (detail: PackageDetail) -> Unit
