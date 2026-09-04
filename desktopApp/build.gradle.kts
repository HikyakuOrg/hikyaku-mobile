import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // maplibre-compose 0.15's desktop renderer uses the Java Foreign Function & Memory API and
    // requires Java 25 at runtime (see compose.desktop.application.jvmArgs below). The Foojay
    // resolver (settings.gradle.kts) auto-provisions this JDK if it isn't already installed.
    jvmToolchain(25)
}

dependencies {
    implementation(projects.sharedUI)
    // sharedUI depends on this as `implementation`, so it isn't exposed transitively — main.kt
    // needs it directly to wire up ProvideMapHost/rememberAwtComposeMapHost.
    implementation(libs.maplibre.compose)

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
        // The compose plugin's `run`/`runDistributable` tasks don't follow the Kotlin toolchain
        // above on their own — left unset, `javaHome` defaults to whatever JDK Gradle itself is
        // running on (observed: a cached JDK 21), which then fails with UnsupportedClassVersionError
        // against class files compiled for Java 25. Point it at the same JDK 25 toolchain instead.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }.get().metadata.installationPath.asFile.absolutePath
        // Required by maplibre-compose 0.15's desktop FFI renderer.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.hikyaku.mobile"
            packageVersion = "1.0.0"
        }
    }
}