# MPEI Neo

An unofficial MPEI Android client built with Jetpack Compose and Material 3. Its main
services are **Schedule**, **BARS**, **Mail**, and **Campus map**.

## Everyday navigation

- **Home**: four customizable service tiles and one actionable next-lesson
  preview. New-grade, unread-mail, and sign-in states are shown as short tile
  hints instead of additional Home dashboards.
- **Schedule**: cached agendas, group/teacher/classroom favorites, week
  refresh, and direct lesson-to-room navigation.
- **BARS**: cached semester grades, per-discipline controls, optional 4.2
  planning, and attendance/weekly/Summary reports. The official BARS WebView
  handles authentication. The numeric forecast is not an official promise of
  an automatic exam grade.
- **Mail**: native Inbox/Sent, attachments, replies/composition, notification
  polling, and an embedded official OWA browser.
- **Campus**: offline Canvas-based map with building selection, pan, zoom, and
  inertial dragging.

Search opens inside the active service. Home search defaults to timetable
targets. A shared search app bar provides a consistent entry point, but no
cross-service result mixer or scope selector is shown. Schedule retains
favorite/target selection and category filters; Campus also provides its own
alphabetic building selector.

Settings provide theme, app update, mail, and BARS preferences. Diagnostic
export and logs are available separately; advanced OWA and performance probes
are collapsed unless requested.

## Data and architecture

The app uses one Android module, Compose Material 3, a manual application
container, DataStore/preferences, internal caches, and platform WebViews.
No dependency-injection framework is required.

- Timetable data comes from the public MPEI timetable API on `ts.mpei.ru`.
  The endpoint currently requires cleartext HTTP, permitted only for this host
  in Android network security configuration.
- BARS credentials and 2FA codes are entered on the university web pages, not
  stored by MPEI Neo. Grades and BARS reports use the existing authenticated
  session and local snapshots.
- Mail uses the authenticated university OWA session and the OWA
  `service.svc` protocol. Headers and bodies use an encrypted per-account
  cache. Notifications use WorkManager.
- Service stack/gesture decisions live in `ServiceRouteStack.kt`; the main
  Compose shell owns transition presentation. BARS WebView extraction flags
  live in `BarsWebFlowState.kt` and persist while its WebView remains mounted.
- The offline map needs no external MapKit SDK.

See [UI components](docs/UI_COMPONENTS.md) for service behavior and
[offline map details](docs/OFFLINE_CAMPUS_MAP.md).

## Verification and builds

The project uses JDK 17, Android SDK 37, and Gradle 9.6. Local checks:

```bash
gradle testDevDebugUnitTest compileDevDebugAndroidTestKotlin lintDevDebug assembleDevRelease
```

Pull requests to `main` trigger the **PR APK** workflow, which runs unit
tests, compiles Android UI tests, runs lint, and builds an installable
`com.rteats.mpeineo.dev` APK.

Every successful push to `main` triggers **Dev APK**, which runs unit tests,
builds an R8-minified signed Dev APK, and publishes it to the rolling
[Dev release](https://github.com/rteats/mpei-neo/releases/tag/dev).
The Dev app uses its own application ID and a public disposable test key;
its installation is independent of a stable release.

The release signing key and other private secrets must never be committed.
