import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.sharedUI)

    // Pass -PdesktopTarget=windows to cross-package a Windows-runnable uber jar from Linux/macOS.
    implementation(
        when (providers.gradleProperty("desktopTarget").orNull) {
            "windows" -> compose.desktop.windows_x64
            "linux" -> compose.desktop.linux_x64
            "macos" -> compose.desktop.macos_x64
            else -> compose.desktop.currentOs
        }
    )
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "org.hikyaku.mobile.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.hikyaku.mobile"
            packageVersion = "1.0.0"
        }
    }
}