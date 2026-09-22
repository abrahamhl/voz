"""Builds GitHub release notes for a version from CHANGELOG.md (used by the release workflow)."""
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
version = sys.argv[1].lstrip('v')
changelog = open('CHANGELOG.md', encoding='utf-8').read()
match = re.search(rf'^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## \[|^\[[^\]]+\]: )', changelog, re.S | re.M)
if not match:
    sys.exit(f'CHANGELOG.md has no section for {version}')

print(f"""> **Demo build.** `voz-demo.apk` is a debug-signed build for sideloading and testing. It is not published on Google Play.
> Behaviour on real phones (speech, TalkBack, YouTube labels) still needs manual testing.

### Install in 4 steps
1. Download `voz-demo.apk` below (verify with `voz-demo.apk.sha256` if you like).
2. Allow the install when Android asks.
3. Android 13+: *Settings → Apps → VOZ → ⋮ → Allow restricted settings*.
4. *Settings → Accessibility → VOZ voice control → On*, then open VOZ and follow the setup.

{match.group(1).strip()}
""")
