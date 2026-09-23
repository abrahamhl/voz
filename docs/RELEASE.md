# Release process

How a tag becomes a GitHub release, what CI guarantees, and how to sign.

## Cutting a release

1. Set the version in `app/build.gradle.kts` (`versionName`) and bump `versionCode`.
2. Move the relevant `CHANGELOG.md` entries from `[Unreleased]` into a new
   `## [<version>]` section (or leave them under `[Unreleased]` for a release
   candidate — the notes generator falls back to it).
3. Commit, then tag and push:
   ```bash
   git tag v<version>
   git push origin v<version>
   ```
4. The `release` workflow builds, verifies and publishes the artifacts.

The workflow refuses to run if the tag (`v0.2.0-rc1` → `0.2.0-rc1`) does not
match `versionName`, so a mistyped tag cannot ship the wrong build.

## What the release workflow produces

| Artifact | When | Notes |
|---|---|---|
| `voz-<version>.apk` | signing key configured | release-signed, for sideloading and manual distribution |
| `voz-<version>.aab` | signing key configured | release-signed, the Play upload format |
| `voz-<version>-UNSIGNED.apk` / `.aab` | no signing key | **not** publishable; must be signed before shipping |
| `voz-<version>-demo-debug.apk` | no signing key | debug-signed, sideload/testing only |
| `dependency-inventory.txt` | always | resolved `releaseRuntimeClasspath` tree |
| `SHA256SUMS.txt` | always | checksums for every asset |

A debug build is never published as the production artifact. When a signed
release exists, only the signed APK/AAB are attached.

## What CI verifies before publishing

- `:core:test` and `:app:testDebugUnitTest` pass.
- `:app:lintDebug` passes.
- The release APK requests **no** `android.permission.INTERNET`.
- `cloud_brain_available` is `false` in the release resource table.
- The release AAB does not request `INTERNET` (probe-verified against a
  permission the app does ship, so a bundle-format change cannot fake a pass).
- `scripts/secret-scan.sh` finds no credentials in tracked files.

`scripts/verify-release.sh <apk> [aab]` runs the same checks locally.

## Signing

Signing is driven by environment variables; **no secret is ever committed**.

```bash
export VOZ_KEYSTORE_FILE=/path/to/release.keystore
export VOZ_KEYSTORE_PASSWORD=...
export VOZ_KEY_ALIAS=...
export VOZ_KEY_PASSWORD=...
./gradlew :app:assembleRelease :app:bundleRelease
```

If `VOZ_KEYSTORE_FILE` points at a missing file, or any of the other variables
is blank, the build stays **unsigned** instead of silently falling back to the
Android debug key. Store the CI keystore as a base64 secret:

```bash
base64 -w0 release.keystore   # paste into the VOZ_KEYSTORE_BASE64 secret
```

Required repository secrets: `VOZ_KEYSTORE_BASE64`, `VOZ_KEYSTORE_PASSWORD`,
`VOZ_KEY_ALIAS`, `VOZ_KEY_PASSWORD`. Until they are set, releases are published
with the `-UNSIGNED` suffix and a warning; they cannot be uploaded to Play.

## Still open

- No release has been validated on a physical device — see
  [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md).
- The cloud engine is disabled in release builds but its code and HTTP dependency
  are still packaged; truly excluding them is tracked as a hardening item.
