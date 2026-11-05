# Signing and retained app data

The app now uses one application ID, `com.rteats.mpeilite`, and one new signing key for Android Studio and GitHub CI. This is a breaking package change from the old `com.rteats.mpeineo` and `.dev` builds. Android treats the new ID as a separate app; old local data does not transfer. On 2026-11-06, version 84 of the new package was installed and launched on the connected phone, then the old `com.rteats.mpeineo` package was removed. Android accepts future in-place updates when the ID and certificate match and the new version code is at least as high as the installed code.

The new certificate SHA-256 is `F7:6A:49:1B:22:71:07:F9:D2:B1:A3:71:14:0A:5A:ED:86:01:B8:66:9A:23:FB:2B:C6:1A:41:04:F4:7D:BE:3B`. The `main` workflow checks this fingerprint before publishing an APK.

## Where the key lives

| Location | What goes there |
| --- | --- |
| `C:/Users/user/Documents/MPEI-Lite-Keys/stable-2026.p12` | New private signing key, generated on this machine. Keep an encrypted backup outside this PC. |
| `local-signing.properties` at the repository root | Key path, alias, password, and local version code. Ignored by Git. Back it up securely with the key. |
| GitHub repository secrets `MPEI_STABLE_KEYSTORE_B64` and `MPEI_STABLE_PASSWORD` | CI copies of the same key and password. Do not commit the private key. |
| `local-signing.properties.example` | Safe template that can be committed. |

The key-generation script refuses to overwrite an existing key. Do not rerun it for this app identity. GitHub secrets cannot be retrieved later, so keep your own backup of the `.p12` and password.

## Android Studio loop

Sync Gradle, select the `debug` build variant, and choose the connected device. **Run** installs `com.rteats.mpeilite` using the new key and preserves that app's data on later runs. The local version code is currently 84. When a CI release with a higher code is installed, raise `versionCode` in the ignored `local-signing.properties` to at least that value before the next local run.

Equivalent PowerShell commands from the repository root:

```powershell
.\gradlew.bat assembleDebug
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

## GitHub setup

GitHub CLI is not authenticated on this machine, so the two repository secrets have not yet been uploaded. After authenticating an account with access to `rteats/mpei-lite`, run:

```powershell
gh auth login
.\scripts\upload_personal_signing.ps1
```

The script reads the ignored local properties, uploads the key and password through standard input, and prints neither value. The changed repository files and Gradle Wrapper must then be committed and pushed. Once the PR checks pass and the change reaches `main`, the main workflow builds and publishes `MPEI-Lite.apk` for `com.rteats.mpeilite` using those secrets. It fails early if either secret is missing and rejects an APK signed by a different certificate.

To check the local key without revealing its password, run `./gradlew.bat signingReport` and compare the SHA-256 above. Do not put a `.p12`, a base64 copy, or the filled-in properties file into the repository.
