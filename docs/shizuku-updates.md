# Shizuku updates and relaunch

MPEI Neo has two installation modes in Settings > Application updates:

- **Android:** manual installation with Android confirmation. This is always the manual fallback.
- **Shizuku:** requires the separate Shizuku app, its service running, and permission granted to MPEI Neo. A silent install uses a Shizuku UserService under shell/root UID.

Automatic updates are **off by default**. They are available only in Shizuku mode. The periodic WorkManager job checks the current build channel about every 6 hours when a permitted network is available. It skips checks if Shizuku is unavailable. It never requests access or opens Android's package installer from background work.

## Why a separate UserService?

Replacing an APK kills its app process. The service runs outside that process, receives an APK file descriptor over Binder, feeds it to the Android package manager, and optionally relaunches the updated app with `am start`. It cannot depend on the app's Context or read paths in the app's private cache.

## Safety

- The Dev and Stable APKs have distinct package IDs and update channels. PR APK variants do not qualify for scheduled updates.
- SHA-256 is checked when supplied by the release metadata. Before either install path, the package ID, versionCode and APK signing certificates are compared with the installed app.
- Navigation recovery only saves coarse service routes, not student data, message content, URLs or credentials.
- Relaunch happens only when the app is in the foreground and the lock screen is not active. A background update remains silent.
- When manual Shizuku installation is unavailable or fails, the usual Android installer opens. Background tasks do **not** show that installer.
- Shizuku started through ADB usually needs to be started again after reboot.

## Test on a real device

1. Install a previous signed **Dev** build (not a PR APK). Launch it, then install a build of this PR into its PR-specific package to inspect settings and permission behavior in isolation.
2. For an actual silent update test, the feature must be included in a normally signed Dev/Stable build: PR APK package IDs intentionally cannot replace those builds. Start Shizuku and grant its permission to MPEI Neo.
3. Confirm mode selection and permissions. Download a newer signed release. Trigger manual install while on Settings. Confirm that Android does not display an install prompt and that Settings returns after relaunch.
4. Restart Shizuku, deny permission, or stop the service. Confirm manual fallback to the Android installer and no fallback activity from periodic background work.
5. Enable automatic updates, set metered/unmetered network selection, and verify WorkManager work scheduling. On an older installed build, verify a new signed release installs silently in the background; the app must not jump to the foreground while locked.
6. Confirm mismatched signing keys, package variants, version mismatches, corrupted APKs and old builds are rejected before installation.
7. Inspect diagnostic events under `UPDATE` and shell-side `MPEI_UPDATER` logcat events if relaunch fails.

**Known limitations:** Real-device testing is required for OEM-specific background-start rules, Shizuku service lifetime during replacement and streamed `pm install -S` behavior. WorkManager does not run on an exact schedule.
