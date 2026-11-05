# Signing

All builds use the `com.rteats.mpeilite` application ID. The private signing key is not committed to Git. Android Studio reads the ignored `local-signing.properties`; the `main` GitHub Actions workflow reads the same key from repository secrets `MPEI_STABLE_KEYSTORE_B64` and `MPEI_STABLE_PASSWORD`. PR emulator builds use the runner's temporary debug key on a clean emulator.

See [Signing and retained app data](../docs/signing-and-data.md) for backup, upload, and version-code instructions. The old public `.dev` key was removed from the working tree; copies in Git history must never be used for this package.
