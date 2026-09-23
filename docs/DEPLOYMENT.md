# Debug and deployment runbook

## Local debug build

Use the private JDK/SDK setup documented in `.opencode/EXECUTION_STATE.md`:

```powershell
$env:JAVA_HOME = 'C:\Users\2fabr\.voz-toolchain\jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'C:\Users\2fabr\.voz-toolchain\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat --no-daemon :app:assembleDebug
```

Install on one connected device and launch it:

```powershell
adb devices
adb install -r -d app\build\outputs\apk\debug\app-debug.apk
adb shell am start -W -n dev.auxdesign.voz/.MainActivity
```

For automated launch/crash/screenshot/logcat evidence, use:

```bash
bash scripts/device-smoke.sh app/build/outputs/apk/debug/app-debug.apk
```

Then complete `docs/DEVICE_VALIDATION.md`. The smoke script cannot prove that
speech recognition, TalkBack, permissions or third-party app labels work.

## Release candidate

The tag must exactly match `versionName` in `app/build.gradle.kts`:

```powershell
git tag v0.2.0-rc1
git push origin v0.2.0-rc1
```

The release workflow builds both `assembleRelease` and `bundleRelease`, checks
the APK/AAB network boundary, creates checksums and publishes an explicit
`-UNSIGNED` artifact if signing secrets are absent. Do not upload an unsigned
artifact to Play.

Configure signing only through secrets or environment variables. Never commit a
keystore, password or certificate private key:

```powershell
$env:VOZ_KEYSTORE_FILE = 'C:\secure\voz-release.keystore'
$env:VOZ_KEYSTORE_PASSWORD = '<secret from the password manager>'
$env:VOZ_KEY_ALIAS = '<alias>'
$env:VOZ_KEY_PASSWORD = '<secret from the password manager>'
.\gradlew.bat --no-daemon :app:assembleRelease :app:bundleRelease
```

Run `scripts/verify-release.sh` against the resulting APK and AAB before any
manual distribution.

## Play deployment gate

Play deployment is intentionally not automated from this repository yet. Before
uploading an AAB, the maintainer must confirm:

- the AAB is signed with the production upload key;
- the exact AAB hash is in the release packet;
- physical-device evidence exists for that build;
- the accessibility declaration, microphone foreground-service declaration,
  privacy URL and Data Safety form are complete;
- the store listing does not promise features absent from the tested artifact.

If any item is missing, keep the candidate as a GitHub prerelease or internal
artifact, not a public Play release.
