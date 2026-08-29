import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openApiGenerator)
}

// Supabase credentials are no longer baked in at build time. They are fetched at
// runtime from the Hikyaku environment endpoint (or a user-supplied self-hosted
// instance) and persisted to disk via multiplatform-settings. See the `environment`
// package.

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
        }
    }
    
    jvm()

    android {
       namespace = "org.hikyaku.mobile.sharedLogic"
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
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.coil3Integration)
            implementation(libs.multiplatform.settings)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            // Exposed in public API (e.g. RouteStep.location: Point), so use `api`.
            api(libs.spatialk.geojson)
            // Exposed in public API (e.g. util/IsoTime.kt helpers used by sharedUI), so use `api`.
            api(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.location)
            implementation(libs.androidx.startup.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// --- Hikyaku API client models -----------------------------------------------------------------
//
// The app's non-Supabase API models are generated from the Hikyaku swagger spec
// (https://api.hikyaku.org/api-docs) instead of being hand-written. The spec isn't served as
// JSON — it's embedded in swagger-ui-init.js — so a pinned snapshot is checked in at
// openapi/hikyaku-openapi.published.json and refreshed on demand via `refreshHikyakuApiSpec`.
//
// The mobile app calls 4 of the 35 documented endpoints, so only those request/response DTOs
// (hikyakuApiAppModels below) are synced into commonMain — the generator's parallel `ApiClient`
// runtime and the other endpoint wrappers are deliberately left out to avoid dead code. Add to
// the list when the app adopts another endpoint. See openapi/README.md.
val hikyakuApiSpecFile = rootProject.file("openapi/hikyaku-openapi.published.json")
val hikyakuApiGeneratedDir = layout.buildDirectory.dir("hikyaku-api/generated").get().asFile
val hikyakuApiAppModels = listOf(
    "GeoJsonFeatureCollectionDto", "GeoJsonFeatureDto", "GeoJsonPointDto", "GeoJsonFeaturePropertiesDto",
    "RouteRequestDto", "RoutePreviewDto", "RouteLegDto", "RouteSummaryDto",
    "AdhocOptimisationDto", "AdhocOptimisationResultDto",
    "RunOptimisationDto", "RunOptimisationResultDto", "LatestOptimisationRunDto", "SetOffOverrideDto",
    "PendingInvitationDto", "InvitationOrganisationDto", "AcceptInvitationResultDto",
    // Instant package assignment: POST /packages, POST /packages/bulk, POST /shifts,
    // GET /shifts/{id}/version.
    "CreatePackageDto", "PackageDimensionsDto", "PackageDto", "CreatePackageResultDto",
    "AssignmentOutcomeDto", "AssignedShiftDto",
    "BulkCreatePackagesDto", "BulkCreatePackagesResultDto", "BulkCreatePackageResultDto",
    "CreateShiftDto", "ShiftDto", "ShiftVersionDto", "ShiftPlanDto", "ShiftPackageOutcomeDto",
    "AddPackagesToShiftDto",
)

val refreshHikyakuApiSpec by tasks.registering {
    group = "hikyaku-api"
    description = "Re-pulls the Hikyaku API spec from prod into the pinned snapshot (openapi/hikyaku-openapi.published.json)."
    val specFile = hikyakuApiSpecFile
    doLast {
        val js = URI("https://api.hikyaku.org/api-docs/swagger-ui-init.js").toURL().readText()
        val key = "\"swaggerDoc\":"
        val keyIndex = js.indexOf(key)
        check(keyIndex != -1) { "couldn't find swaggerDoc in swagger-ui-init.js" }
        val start = js.indexOf('{', keyIndex + key.length)
        var depth = 0
        var end = -1
        for (i in start until js.length) {
            when (js[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }
        check(end != -1) { "couldn't find end of swaggerDoc object in swagger-ui-init.js" }
        specFile.writeText(js.substring(start, end))
        logger.lifecycle("refreshed spec -> $specFile")
    }
}

val hikyakuApiGenerate by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set(hikyakuApiSpecFile.absolutePath)
    outputDir.set(hikyakuApiGeneratedDir.path)
    packageName.set("org.hikyaku.mobile.api.generated")
    additionalProperties.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "kotlinx-datetime",
        )
    )
}

val syncModels by tasks.registering {
    group = "hikyaku-api"
    description = "Regenerates the Hikyaku API client from the pinned spec and syncs the DTOs the app uses into commonMain."
    dependsOn(hikyakuApiGenerate)
    val generatedModelsDir = hikyakuApiGeneratedDir.resolve("src/commonMain/kotlin/org/hikyaku/mobile/api/generated/models")
    val destDir = layout.projectDirectory.dir("src/commonMain/kotlin/org/hikyaku/mobile/api/generated/models").asFile
    val appModels = hikyakuApiAppModels
    doLast {
        destDir.deleteRecursively()
        destDir.mkdirs()

        appModels.forEach { model ->
            val src = generatedModelsDir.resolve("$model.kt")
            check(src.exists()) {
                "generated model $model.kt not found under $generatedModelsDir — check hikyakuApiAppModels against the spec"
            }
            // openapi-generator 7.11.0 + kotlin multiplatform emits a duplicate class
            // annotation ("@Serializable@Serializable"), which doesn't compile.
            val text = src.readText().replace("@Serializable@Serializable", "@Serializable")
            destDir.resolve(src.name).writeText(text)
        }
        logger.lifecycle("synced ${appModels.size} DTOs -> src/commonMain (org.hikyaku.mobile.api.generated.models)")
    }
}