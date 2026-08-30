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
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
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
        }
        jvmMain.dependencies {
            // Phone-number validation/formatting; the actual for `expect object PhoneNumbers`.
            implementation(libs.libphonenumber)
            // MapLibre on desktop renders via MapLibre Native through a JNI bindings module
            // that bundles the platform-specific native library (libmaplibre-jni.so/.dylib/.dll).
            // Without this, the map is blank at runtime with:
            //   UnsatisfiedLinkError: Native library not found in JAR: /<os>/<arch>/<renderer>/...
            // Select exactly the one capability matching the host OS/arch/renderer.
            runtimeOnly(libs.maplibre.native.bindings.jni.get().toString()) {
                capabilities {
                    requireCapability(
                        "org.maplibre.compose:maplibre-native-bindings-jni-${maplibreDesktopTarget()}"
                    )
                }
            }
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
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Resolves the MapLibre Native JNI bindings capability for the current build host.
// Published targets: macos-aarch64-metal, linux-amd64-opengl, linux-amd64-vulkan,
// windows-amd64-opengl, windows-amd64-vulkan.
fun maplibreDesktopTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val hostOs = when {
        os.contains("mac") -> "macos"
        os.contains("win") -> "windows"
        else -> os.split(" ").first() // e.g. "linux"
    }
    val hostArch = when (val arch = System.getProperty("os.arch").lowercase()) {
        "x86_64", "amd64" -> "amd64"
        "aarch64", "arm64" -> "aarch64"
        else -> arch
    }
    val renderer = if (hostOs == "macos") "metal" else "opengl"
    return "$hostOs-$hostArch-$renderer"
}