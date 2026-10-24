# Release channels

MPEI Lite uses two Android application channels.

## Dev

- App name: **MPEI Lite Dev**
- Application ID: `com.rteats.mpeineo.dev`
- Update channel: `dev`
- Built automatically on every push to `main`
- Signed with the public disposable key in `ci/mpei-neo-dev.p12.b64`
- Published as the rolling GitHub prerelease tagged `dev`
- Version format: `0.5.0-dev.<workflow run number>`

The rolling dev release is intended for rapid device testing. Keep the legacy APK asset name for update-manifest compatibility. The `dev` tag is force-moved
to the newest main commit and the fixed `MPEI-Neo-Dev.apk` asset is replaced.

## Stable

- App name: **MPEI Lite**
- Application ID: `com.rteats.mpeineo`
- Update channel: `stable`
- Signed only with the protected production signing secrets
- Built by `.github/workflows/stable-release.yml`
- Runs unit tests, lint and an emulator smoke test before publishing
- Version format: semantic `MAJOR.MINOR.PATCH`, for example `0.5.0`

A stable release can be started either by pushing a `vMAJOR.MINOR.PATCH` tag or through
the manual workflow dispatch input. Stable Android `versionCode` is derived as:

`major * 1,000,000 + minor * 1,000 + patch`.

Dev and stable are separate Android apps and can be installed side by side.
Their app data, WebView cookies and BARS sessions are intentionally independent.

## Continuous integration and Actions hygiene

- PR verification runs JVM unit tests, Kotlin instrumentation-test compilation,
  Android lint, an optimized release build, and small maintenance-script tests.
  It does not start an emulator or upload an APK for every PR.
- Every merge to main builds the rolling Dev APK and runs unit tests again.
- Stable release alone runs an emulator smoke check before publishing.
- The Dev job prunes unused Gradle caches and at most 75 completed, stale
  Actions runs per build. All Stable release runs are kept, as are the 10 newest
  Dev and PR verification runs; unrecognized workflows are never deleted.
  Cache and run cleanup are best-effort and cannot fail a successful build.
- Historical branch pruning no longer starts a workflow on every push.
