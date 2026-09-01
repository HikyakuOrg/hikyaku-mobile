# Hikyaku

Hikyaku is a mobile app for delivery drivers. The Japanese word *hikyaku* (飛脚) names the
courier runners of old Japan. The app helps a modern courier do the same job: plan a route,
carry packages, and prove delivery.

With the app, a driver can do these tasks:

- Start a work shift and add packages to it.
- Get an optimised delivery route for the shift.
- Scan a package QR code to check it in or mark it delivered.
- Record proof of delivery.
- Track a shift on a map, and share a tracking link with a customer.
- Work alone, or as part of an organisation with other drivers and vehicles.

Hikyaku connects to a Hikyaku server. The default server is `app.hikyaku.org`. A user can also
point the app at a self-hosted server.

## Status

This project is under active development. Interfaces and features can still change.

## Technology

Hikyaku is a [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
project. The UI uses [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).
One code base builds the following targets:

- Android

## Prerequisites

Install these tools before you build the app:

- [JDK 21](https://adoptium.net/).
- [Android Studio](https://developer.android.com/studio), with the Kotlin Multiplatform plugin.
  Android Studio manages the Android SDK for you.

## Get the source code

Clone the repository:

```bash
git clone https://github.com/HikyakuOrg/hikyaku-mobile.git
```

Open the cloned folder in Android Studio.

## Configure local settings

The build reads local, machine-specific settings from `local.properties`, at the root of the
repository.

The app does not need an API key or a Supabase key for local development. At startup, the app
fetches its configuration — the Supabase URL and key, the API URL, and the Google sign-in
client ID — from the `/api/environment` endpoint of the configured Hikyaku server.

`local.properties` needs one more entry only when you build a signed release and upload it to
Firebase App Distribution:

```properties
firebase.serviceCredentialsFile=/absolute/path/to/firebase-service-account.json
```

## Run the app

Use the run configuration in your IDE's toolbar. You can also use this Gradle command:

- Android app: `./gradlew :androidApp:assembleDebug`

## Run the tests

Use the run button in your IDE's editor gutter. You can also use this Gradle command:

- Android tests: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`

## Generate the API client

Hikyaku generates its non-Supabase API models from the Hikyaku swagger spec. Do not write these
models by hand. Run this command to regenerate them:

```bash
./gradlew syncModels
```

For the full generation workflow, read [`openapi/README.md`](./openapi/README.md).

## Continuous integration

On every push to `main`, a GitHub Actions workflow builds a signed release APK and uploads it
to Firebase App Distribution. The workflow definition is at
[`.github/workflows/android-firebase-distribution.yml`](./.github/workflows/android-firebase-distribution.yml).
The workflow needs these repository secrets: `FIREBASE_SERVICE_ACCOUNT`,
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and
`ANDROID_KEY_PASSWORD`. You do not need these secrets for local development.

## License

Hikyaku is free software. You can redistribute it, and change it, under the terms of the
[GNU General Public License, version 3](./LICENSE.md).
