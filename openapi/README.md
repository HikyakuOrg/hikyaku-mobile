# OpenAPI client generation

The app's non-Supabase API models are generated from the Hikyaku swagger spec
(<https://api.hikyaku.org/api-docs>).

| Path | What it is |
| --- | --- |
| `hikyaku-openapi.published.json` | Pinned snapshot of the spec (extracted from the live `swagger-ui-init.js`, which is how it's served — there's no `.json` endpoint). |

> **Temporarily hand-copied.** The current snapshot is `hikyaku-api`'s committed `openapi.json`
> (branch `main`), copied straight in rather than pulled by `refreshHikyakuApiSpec` — that task
> scrapes the *deployed* prod spec, and prod does not yet serve `/api/v1/packages`, `/api/v1/shifts`
> or `/api/v1/vin/{vin}` (the last of these is live on staging). Once the API deploys, go back to `./gradlew refreshHikyakuApiSpec syncModels` and
> this note can go.

Generation is wired up as Gradle tasks in `sharedLogic/build.gradle.kts` (see the "Hikyaku API
client models" section there), using the OpenAPI generator Gradle plugin.

## What's wired into the app

The mobile app calls **12** of the 56 documented operations, so only the request/response DTOs for
those are compiled. `./gradlew syncModels` regenerates the full client into a build-directory
scratch dir, then syncs the DTOs the app uses straight into
`sharedLogic/src/commonMain/kotlin/org/hikyaku/mobile/api/generated/models/` (package
`org.hikyaku.mobile.api.generated.models`) — reusable across every `sharedLogic` target
(android/iosArm64/iosSimulatorArm64).

| Endpoint | Repository | Generated DTOs used |
| --- | --- | --- |
| `GET /geocode/autocomplete` | `GeocodeRepository` | `GeoJsonFeatureCollectionDto` (+ `Feature`/`Point`/`Properties`) |
| `GET /geocode/reverse` | `RoutePoiRepository` | `GeoJsonFeatureCollectionDto` (…) |
| `POST /api/v1/routing/route` | `RoutingRepository` | `RouteRequestDto` → `RoutePreviewDto` (+ `Leg`/`Summary`) |
| `POST /api/v1/optimisation/adhoc` | `CreateShiftRepository` | `AdhocOptimisationDto` → `AdhocOptimisationResultDto` |
| `POST /api/v1/optimisation/run` | `OptimisationRepository` | `RunOptimisationDto` (+ `SetOffOverrideDto`) → `RunOptimisationResultDto` |
| `GET /api/v1/optimisation/run/latest` | `OptimisationRepository` | `LatestOptimisationRunDto` |
| `POST /api/v1/packages` | `PackageRepository` | `CreatePackageDto` (+ `PackageDimensionsDto`) → `CreatePackageResultDto` (+ `PackageDto`/`AssignmentOutcomeDto`/`AssignedShiftDto`) |
| `GET /api/v1/shifts/{id}/version` | `ShiftVersionRepository` | `ShiftVersionDto` |
| `GET /api/v1/invitations/pending` | `InvitationRepository` | `PendingInvitationDto` (+ `InvitationOrganisationDto`) |
| `POST /api/v1/invitations/{id}/accept` | `InvitationRepository` | `AcceptInvitationResultDto` |
| `POST /api/v1/invitations/{id}/decline` | `InvitationRepository` | — (2xx/404 status code alone tells the caller everything the body would) |
| `GET /api/v1/vin/{vin}` | `VinDecodeRepository` | `VinDecodeResultDto` (+ `VinComponentsDto`/`WmiResultDto`/`ModelYearResultDto`/`CheckDigitResultDto`/`VehicleInfoDto`/`PlantInfoDto`/`EngineInfoDto`/`DecodeErrorDto`) |

The repositories keep calling through the app's shared `appHttpClient` / `ApiEndpoints`; only the
wire DTOs are generated (the generator's parallel `ApiClient` runtime and the 44 unused endpoint
wrappers are intentionally **not** compiled in). Responses are mapped to the existing domain models
(`AddressSuggestion`, `RoutePoi`, `RoutePreview`).

A few DTOs are synced ahead of their callers, so the paths already in `ApiEndpoints` compile against
a real contract rather than a hand-written guess: `BulkCreatePackagesDto` /
`BulkCreatePackagesResultDto` / `BulkCreatePackageResultDto` (`POST /api/v1/packages/bulk`),
`CreateShiftDto` / `ShiftDto` (`POST /api/v1/shifts`), and `ShiftPlanDto` /
`ShiftPackageOutcomeDto` / `AddPackagesToShiftDto` (`POST /api/v1/shifts/{id}/packages`). Mobile has
no repository for those yet — the dashboard drives them.

To adopt another endpoint: add its DTOs to the `hikyakuApiAppModels` list in
`sharedLogic/build.gradle.kts`, re-run `./gradlew syncModels`, and use them in the repository.

## Regenerating

```bash
./gradlew syncModels                                # from the pinned snapshot
./gradlew refreshHikyakuApiSpec syncModels           # re-pull the spec from prod first
```

`refreshHikyakuApiSpec` overwrites `hikyaku-openapi.published.json`
