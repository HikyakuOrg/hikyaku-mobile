package org.hikyaku.mobile

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.hikyaku.mobile.packages.add.AddPackageScreenHarness

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hikyaku",
    ) {
        AddPackageScreenHarness()
    }
}
