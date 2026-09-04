import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    androidLibrary {
       namespace = "org.hikyaku.mobile.sharedUI"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       // The repo's first instrumented tests. The ML Kit recognisers can only be exercised
       // against real JPEGs on a real device, not on the JVM. The source-set tree is left unset
       // on purpose so commonTest is not dragged onto the device and re-run there.
       withDeviceTest {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
           animationsDisabled = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            // MapLibre Native FFI render backend. The MapLibre Android SDK is no longer a
            // transitive dependency of maplibre-compose as of 0.15.0 — without this, the map
            // renders nothing at runtime.
            runtimeOnly(libs.maplibre.compose.runtime.vulkan.android)
            // Phone-number validation/formatting; the actual for `expect object PhoneNumbers`.
            implementation(libs.libphonenumber)
            // Shipping-label printing; the actual for `expect fun rememberPrintShippingLabel`.
            implementation(libs.androidx.print)
            // Tags the POD photo's JPEG with the courier's GPS position; used by
            // `rememberPhotoCapture`.
            implementation(libs.androidx.exifinterface)
            implementation(libs.zxing.core)
            // Google native sign-in; the actual for `expect fun rememberGoogleIdTokenLauncher`.
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
            // On-device GenAI (Gemini Nano via AICore); the actuals for `expect fun
            // rememberPodDescriber`/`rememberPodProofreader`. Both APIs return Guava
            // ListenableFuture (not a Play Services Task), hence coroutines-guava for .await().
            implementation(libs.mlkit.genai.imageDescription)
            implementation(libs.mlkit.genai.proofreading)
            implementation(libs.kotlinx.coroutinesGuava)
            // CameraX drives the in-app VIN scanner's viewfinder and frame analysis. qr-kit
            // already pulls these in, but only at <scope>runtime</scope>, so they are absent
            // from the compile classpath and must be declared here.
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            // ML Kit Vision for VIN recognition, via the UNBUNDLED (Play Services) path: the
            // OCR and barcode models live in Play Services rather than the APK, keeping ~28 MB
            // of bundled model out of the build. Inference is on-device once they are installed.
            implementation(libs.playServices.mlkit.textRecognition)
            implementation(libs.playServices.mlkit.barcodeScanning)
            // Last-resort multimodal read for a still image whose OCR and barcode both come up
            // empty; the actual for `expect fun rememberVinScanner`'s fallback. Gated on
            // checkStatus(), so it stays dormant on any build without AICore.
            implementation(libs.mlkit.genai.prompt)
            // ML Kit Vision and CameraX hand back Play Services Tasks; coroutines-guava above
            // covers the ListenableFuture side. Both bridges are needed for .await().
            implementation(libs.kotlinx.coroutinesPlayServices)
        }
        jvmMain.dependencies {
            // Phone-number validation/formatting; the actual for `expect object PhoneNumbers`.
            implementation(libs.libphonenumber)
            // MapLibre on desktop renders via MapLibre Native FFI through a per-OS/arch runtime
            // artifact (replaces the old single maplibre-native-bindings-jni + capability
            // selection). Without this, the map is blank at runtime.
            runtimeOnly(
                "org.maplibre.compose:maplibre-compose-runtime-${maplibreDesktopTarget()}:" +
                    libs.versions.maplibre.compose.get()
            )
        }
        commonMain.dependencies {
            api(projects.sharedLogic)
            implementation(libs.calendar.composeMultiplatform)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.iconsCore)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.maplibre.compose)
            implementation(libs.spatialk.geojson)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.supabase.coil3Integration)
            implementation(libs.qr.kit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Resolves the MapLibre Native FFI runtime artifact suffix for the current build host.
// Published targets (org.maplibre.compose:maplibre-compose-runtime-<target>): metal-macos-arm64,
// vulkan-linux-x64, vulkan-linux-arm64, vulkan-windows-x64, vulkan-windows-arm64. There is no
// desktop OpenGL target (Vulkan/Metal only) and no Intel-Mac target as of 0.15.0.
fun maplibreDesktopTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val hostOs = when {
        os.contains("mac") -> "macos"
        os.contains("win") -> "windows"
        else -> os.split(" ").first() // e.g. "linux"
    }
    val hostArch = when (val arch = System.getProperty("os.arch").lowercase()) {
        "x86_64", "amd64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> arch
    }
    val renderer = if (hostOs == "macos") "metal" else "vulkan"
    return "$renderer-$hostOs-$hostArch"
}