# MPEI Lite

An unofficial Android client for Moscow Power Engineering Institute (MPEI).
Built with Kotlin, Jetpack Compose, and Material 3, with Android dynamic colors.

## Services

- **Пары (Schedule):** group, teacher and classroom timetables; favorites, offline cache, next lesson and room links.
- **БАРС (BARS):** authenticated grade and attendance reports, discipline activities, and a local 4.2 scenario calculator.
- **Почта (Mail):** OWA-backed Inbox/Sent, drafts, writing, attachments, notifications and cached messages.
- **Корпуса (Campus):** an offline map with building and classroom navigation.

The four tabs are direct destinations. Settings is secondary. Search belongs to
the selected service. There are no custom screen morphs, Back-slide controllers,
or privileged installer services.

## Reliability

The app keeps one BARS WebView alive across tab changes to preserve the official
authenticated session. Credentials and 2FA remain on university pages. BARS
grades and Mail content use local caches and show data during recoverable outages.

APK updates are checked manually. The downloaded APK is verified before the
standard Android package installer opens. Dev and Stable use separate package
IDs and signing keys; do not change these identifiers for a visual rebrand.

## Architecture

One Android module with a small manual application container, repositories,
feature ViewModels, Compose screens and pure domain calculations. Avoid extra
dependency-injection or navigation frameworks without a measured need.

See [architecture](docs/ARCHITECTURE.md), [UI behavior](docs/UI_COMPONENTS.md),
and [release channels](docs/RELEASE_CHANNELS.md).

## Build and test

Use JDK 17, Android SDK 37 and Gradle 9.6.

```bash
gradle testDevDebugUnitTest compileDevDebugAndroidTestKotlin lintDevDebug assembleDevRelease
```

PRs run validation in CI; merges to `main` publish a rolling Dev APK.
Stable release builds require production signing secrets and a device/emulator
verification stage.
