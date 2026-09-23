#!/usr/bin/env bash
# Automated on-device smoke test for VOZ.
#
# Drives everything that can be driven over adb: install, launch, a crash check,
# a screenshot, logcat capture and a best-effort accessibility-service enable.
# The spoken-command checks in docs/DEVICE_VALIDATION.md still need a human.
#
# Usage: scripts/device-smoke.sh [path/to/app.apk]
# Evidence (report.md, home.png, logcat.txt, launch.txt) is written to
# docs/evidence/<timestamp>/. Nothing is uploaded anywhere.
set -euo pipefail

PKG="dev.auxdesign.voz"
A11Y="$PKG/$PKG.a11y.VozAccessibilityService"
MAIN="$PKG/.MainActivity"
apk="${1:-}"
out="docs/evidence/$(date +%Y%m%d-%H%M%S)"

die() { echo "error: $*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb not found; install Android platform-tools and put it on PATH"
[ "$(adb get-state 2>/dev/null || true)" = "device" ] || die "no device/emulator connected (see 'adb devices')"

mkdir -p "$out"
echo "evidence directory: $out"

{
  echo "# VOZ device smoke run — $(date -Iseconds)"
  echo
  echo "| Field | Value |"
  echo "|---|---|"
  echo "| Serial | $(adb get-serialno | tr -d '\r') |"
  echo "| Model | $(adb shell getprop ro.product.model | tr -d '\r') |"
  echo "| Manufacturer | $(adb shell getprop ro.product.manufacturer | tr -d '\r') |"
  echo "| Android | $(adb shell getprop ro.build.version.release | tr -d '\r') |"
  echo "| SDK | $(adb shell getprop ro.build.version.sdk | tr -d '\r') |"
  echo "| Build | ${apk:-<already installed>} |"
} | tee "$out/report.md"

if [ -n "$apk" ]; then
  [ -f "$apk" ] || die "APK not found: $apk"
  echo "installing $apk ..."
  adb install -r -d "$apk" | tee -a "$out/report.md"
fi

adb shell input keyevent KEYCODE_HOME || true
adb shell am force-stop "$PKG" || true
adb logcat -c || true

echo "launching $MAIN ..."
adb shell am start -W -n "$MAIN" | tee "$out/launch.txt"

sleep 3
pid="$(adb shell pidof "$PKG" | tr -d '\r')"
[ -n "$pid" ] || die "$PKG is not running after launch (see $out/logcat.txt)"
echo "process $PKG alive as pid $pid" | tee -a "$out/report.md"

adb exec-out screencap -p > "$out/home.png"
echo "screenshot: $out/home.png"

# Best-effort accessibility enable. On Android 13+ the service may still wait for
# the user to confirm in Settings; that is recorded, not treated as a failure.
current="$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [ -z "$current" ] || [ "$current" = "null" ]; then
  adb shell settings put secure enabled_accessibility_services "$A11Y" || true
else
  case ":$current:" in
    *":$A11Y:"*) : ;;
    *) adb shell settings put secure enabled_accessibility_services "$current:$A11Y" || true ;;
  esac
fi
adb shell settings put secure accessibility_enabled 1 || true

sleep 5
adb logcat -d -v threadtime > "$out/logcat.txt"

if grep -A30 "FATAL EXCEPTION" "$out/logcat.txt" | grep -q "$PKG"; then
  echo "FAIL: a crash from $PKG was found in logcat" | tee -a "$out/report.md"
  grep -n -A30 "FATAL EXCEPTION" "$out/logcat.txt" | head -60
  exit 1
fi
if adb logcat -d -b crash 2>/dev/null | grep -q "$PKG"; then
  echo "FAIL: $PKG appears in the crash log buffer" | tee -a "$out/report.md"
  adb logcat -d -b crash | grep -A30 "$PKG" | head -60
  exit 1
fi

echo "OK: $PKG launched and stayed alive; no crash from the package in logcat." | tee -a "$out/report.md"
{
  echo
  echo "## Still to do by hand (spoken commands)"
  echo
  echo "Run sections B, C and E of docs/DEVICE_VALIDATION.md on this phone and"
  echo "record the results in the issue template."
} >> "$out/report.md"
echo
echo "Done. Evidence in $out — attach it to the 'Device validation result' issue."
