package org.hikyaku.mobile.packages

import androidx.compose.runtime.Composable
import org.hikyaku.mobile.packages.model.PackageDetail

/** Desktop has no system print dialog wired up. */
@Composable
actual fun rememberPrintShippingLabel(): (detail: PackageDetail) -> Unit = {}
