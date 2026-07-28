# OpenAPI client generation

The app's non-Supabase API models are generated from the Hikyaku swagger spec
(<https://api.hikyaku.org/api-docs>).

| Path | What it is |
| --- | --- |
| `hikyaku-openapi.published.json` | Pinned snapshot of the spec (extracted from the live `swagger-ui-init.js`, which is how it's served — there's no `.json` endpoint). |

Generation is wired up as Gradle tasks in `sharedLogic/build.gradle.kts` (see the "Hikyaku API
client models" section there), using the OpenAPI generator Gradle plugin.

## What's wired into the app

The mobile app calls **4** of the 35 documented endpoints, so only the request/response DTOs for
those are compiled. `./gradlew syncModels` regenerates the full client into a build-directory
scratch dir, then syncs the DTOs the app uses straight into
`sharedLogic/src/commonMain/kotlin/org/hikyaku/mobile/api/generated/models/` (package
`org.hikyaku.mobile.api.generated.models`) — reusable across every `sharedLogic` target
(android/iosArm64/iosSimulatorArm64/jvm).

| Endpoint | Repository | Generated DTOs used |
| --- | --- | --- |
| `GET /geocode/autocomplete` | `GeocodeRepository` | `GeoJsonFeatureCollectionDto` (+ `Feature`/`Point`/`Properties`) |
| `GET /geocode/reverse` | `RoutePoiRepository` | `GeoJsonFeatureCollectionDto` (…) |
| `POST /api/v1/routing/route` | `RoutingRepository` | `RouteRequestDto` → `RoutePreviewDto` (+ `Leg`/`Summary`) |
| `POST /api/v1/optimisation/adhoc` | `CreateShiftRepository` | `AdhocOptimisationDto` → `AdhocOptimisationResultDto` |

The repositories keep calling through the app's shared `appHttpClient` / `ApiEndpoints`; only the
wire DTOs are generated (the generator's parallel `ApiClient` runtime and the 30 unused endpoint
wrappers are intentionally **not** compiled in). Responses are mapped to the existing domain models
(`AddressSuggestion`, `RoutePoi`, `RoutePreview`).

To adopt another endpoint: add its DTOs to the `hikyakuApiAppModels` list in
`sharedLogic/build.gradle.kts`, re-run `./gradlew syncModels`, and use them in the repository.

## Regenerating

```bash
./gradlew syncModels                                # from the pinned snapshot
./gradlew refreshHikyakuApiSpec syncModels           # re-pull the spec from prod first
```

`refreshHikyakuApiSpec` overwrites `hikyaku-openapi.published.json`
