"""Builds GitHub release notes for a version from CHANGELOG.md (used by the release workflow).

Reads the exact [version] section, or falls back to [Unreleased] for release
candidates that have not been cut into the changelog yet. The banner reflects
whether the artifacts are release-signed (VOZ_RELEASE_SIGNED=true).
"""
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
version = sys.argv[1].lstrip('v')
signed = os.environ.get('VOZ_RELEASE_SIGNED', 'false').lower() == 'true'
changelog = open('CHANGELOG.md', encoding='utf-8').read()


def section(name):
    match = re.search(
        rf'^## \[{re.escape(name)}\][^\n]*\n(.*?)(?=^## \[|^\[[^\]]+\]: )',
        changelog, re.S | re.M)
    return match.group(1).strip() if match else None


body = section(version)
if body is None:
    body = section('Unreleased')
    if body is None:
        sys.exit(f'CHANGELOG.md has no section for {version} or [Unreleased]')
    body = f'_{version} is built from the current [Unreleased] changes._\n\n{body}'

if signed:
    banner = (
        f'> **Signed release build.** `voz-{version}.apk` and `voz-{version}.aab` are signed '
        'with the release key and are the artifacts intended for distribution.\n'
        '> On-device behaviour (speech, TalkBack, third-party app labels) is still validated '
        'by hand; see `docs/DEVICE_VALIDATION.md`.')
else:
    banner = (
        '> **UNSIGNED release build (no signing key configured).** The `.apk`/`.aab` below are '
        '**not** publishable to Google Play and must be signed before shipping.\n'
        f'> `voz-{version}-demo-debug.apk` is a debug-signed build for sideloading and testing only.')

print(f"""{banner}

### Install in 4 steps
1. Download the APK below (verify it against `SHA256SUMS.txt`).
2. Allow the install when Android asks.
3. Android 13+: *Settings → Apps → VOZ → ⋮ → Allow restricted settings*.
4. *Settings → Accessibility → VOZ voice control → On*, then open VOZ and follow the setup.

{body}
""")
