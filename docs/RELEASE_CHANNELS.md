# Rolling dated release

One `latest` GitHub release replaces the former Stable and Dev channels.
Every push to main runs unit tests and builds an optimized signed APK.
Every PR runs Android lint and unit tests without APK builds.

- Version name: UTC `YYYY.MM.DD.<run number>`.
- Android versionCode: `1,000,000 + GitHub Actions run number` (prevents downgrades from old Dev versions).
- Tag: `latest`, pointing to the newest successful main commit.
- Asset: `MPEI-Lite.apk` plus SHA-256 `update.json`.
- Application ID: `com.rteats.mpeineo.dev` for continuity with Dev installs.
- Signing: existing public disposable Dev key.
- Old GitHub releases and their tags are removed after publication.

Former Stable installations have package ID `com.rteats.mpeineo` and
cannot update over the `.dev`-identity APK.
