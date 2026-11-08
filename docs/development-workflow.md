# Android Studio and GitHub workflow

Use Android Studio for the short edit/run loop. Select the `debug` variant and the attached device, then use **Run** to install and inspect one change. Run a focused test from the editor for a quick failure signal. Android Studio uses the project Gradle Wrapper; keep its four files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) in version control.

From PowerShell at the repository root, use these repeatable checks:

```powershell
.\scripts\dev.ps1 check   # policy, architecture, Python tests, lint, JVM tests
.\scripts\dev.ps1 device  # selected accessibility and BARS tests on the adb device
.\scripts\dev.ps1 all     # both gates
```

If several devices are connected, add `-DeviceSerial <adb-serial>`. The `device` command uses the same two instrumented test classes as the PR emulator job. It refuses to run when MPEI Lite is already installed, because Gradle's connected-test lifecycle may remove the app and its data. Use a separate emulator, or pass `-AllowDataReset` only when that loss is acceptable. Run other affected instrumented tests from Android Studio or with `./gradlew.bat connectedDebugAndroidTest` before submitting a behavior change. The full instrumented suite is broader and may depend on accounts or live services; investigate individual failures instead of changing the CI gate to ignore them.

Local debug and CI release APKs use the same `com.rteats.mpeilite` application ID and signing key. Keep `local-signing.properties` and the private key available for Android Studio. The script rejects a zero-test report, since the Android Gradle task can sometimes return success after an install failure. On this physical Android 16 phone, Compose instrumentation currently fails before its assertions with a coroutine ServiceLoader error; use the PR's Android 35 emulator result for that gate. See [Signing and retained app data](signing-and-data.md).

Suggested iteration:

1. Create a short-lived branch. Sync Gradle in Android Studio and run `debug` on the device.
2. Change one behavior, add a focused regression test, run that test and inspect the app on the device. For UI work, follow `AGENTS.md`, `docs/material3/README.md`, and `docs/material3/rules.json`.
3. Run `.\scripts\dev.ps1 all` before opening a PR. If no device is available, run `check` and state which device coverage remains unverified.
4. Open a PR to `main`. GitHub repeats architecture/Material 3 checks, lint, JVM tests, and the two emulator tests. For UI PRs, list affected Material 3 rule IDs, exact source pages, and any unverified rules in the audit.
5. Merge after the required checks pass. The `main` workflow builds and publishes the rolling dev APK. Inspect the release artifact and install it when checking release-specific behavior.

In GitHub repository rules for `main`, require a pull request and the `verify` and `material3-accessibility` checks. Keep those checks required if using a merge queue; the PR workflow also handles `merge_group`. The workflows only take read permissions on PRs, and the release workflow writes only from `main`.

The local phone and the CI emulator provide different evidence. The phone checks device-specific rendering, input, and performance; CI gives a clean, repeatable baseline. Neither proves complete Material 3 compliance or covers every service flow. Record remaining unverified design rules in the UI audit.
