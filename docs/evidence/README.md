# Device evidence

Nothing here has been produced yet: **VOZ has not been run on a physical phone**.
This folder is where the first real evidence goes once a tester runs
[DEVICE_VALIDATION.md](../DEVICE_VALIDATION.md).

## How to produce a run

1. `scripts/device-smoke.sh <app.apk>` drives the automated part over adb and
   writes a timestamped folder (`report.md`, `home.png`, `logcat.txt`,
   `launch.txt`).
2. A human runs the spoken-command sections (B, C, E) and fills the results
   table, using [TEMPLATE.md](TEMPLATE.md).
3. Open the **Device validation result** issue (or attach the folder to the
   release PR) with the build tag/commit and the filled table.

## What must not be committed

Screen recordings and screenshots can contain personal messages, contacts or
banking screens. Run folders (`docs/evidence/<timestamp>/`) are git-ignored on
purpose: attach them to the issue instead of committing them. Only this file and
`TEMPLATE.md` are tracked.

## Acceptance criteria (see DEVICE_VALIDATION.md for the full list)

A build may be called "device-validated" only when:

- it ran on **at least two physical phones**, at least one on Android 13+;
- at least one run had **TalkBack on**;
- sections A, B, C and E were all executed, with every failure recorded (a
  recorded failure is evidence; a missing row is not);
- the automated smoke run finished with `OK` (no crash from `dev.auxdesign.voz`);
- the exact build tag/commit and the APK SHA-256 are in the report.
