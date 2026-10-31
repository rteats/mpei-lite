# MPEI Lite

Unofficial Android companion for Moscow Power Engineering Institute.
Built with Kotlin, Jetpack Compose and Material 3.

## Current screens

- Schedule: raw week JSON and group, teacher, room lookup with local cache.
- Mail: OWA Inbox/Sent, compose, attachments and notifications (unchanged).
- Map: cached campus map; independent background download of the Lefortovo OSM extract with bounded network attempts and visible error details.
- Settings: appearance, mail, APK updates, diagnostic ZIP export.

Bottom navbar only. Global search, top tabs, pager and visual timetable are
suspended. BARS is disabled until rewritten from browser HAR analysis.
The old BARS source is retained as inactive reference code.

## Releases and tests

One rolling release, no Stable/Dev channels. Each PR runs architecture
checks, Python and JVM tests, and Android lint without building an APK.
After the PR is merged, a main push only assembles and publishes the signed APK
as `latest`; it does not repeat PR tests or lint. Main must receive changes
through checked PRs, since direct pushes bypass these checks.

Version names follow Moscow time (`Europe/Moscow`):
`YYYY.MM.DD.<daily published-build counter>`, starting at 1 each Moscow day.
Android versionCode remains 1,000,000 + the monotonic GitHub run number,
so existing devices accept upgrades.
The former Dev package ID `com.rteats.mpeineo.dev` and signing key remain
unchanged for in-place upgrades. Former Stable apps have a different package ID.
After publishing, the workflow removes older GitHub releases and tags.

## Diagnostics

Settings > Diagnostics offers a persistent bounded log, crash reports,
copying and ZIP export. Inspect archives before sharing sensitive content.

Requirements: JDK 17, Android SDK 37, Gradle 9.6.
Local test/build: `gradle testRollingDebugUnitTest assembleRollingRelease`.
