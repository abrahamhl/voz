#!/usr/bin/env bash
# Verifies that a release APK (and optionally its AAB) cannot reach the network
# and ships with the cloud brain switched off. Run before publishing a release;
# CI runs the same checks on every build.
#
# Usage: verify-release.sh <apk> [aab]
set -euo pipefail

fail() { echo "::error::$*" >&2; exit 1; }

find_aapt2() {
  if [ -n "${AAPT2:-}" ]; then echo "$AAPT2"; return 0; fi
  if command -v aapt2 >/dev/null 2>&1; then command -v aapt2; return 0; fi
  if command -v aapt2.exe >/dev/null 2>&1; then command -v aapt2.exe; return 0; fi
  local d candidate
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [ -n "$d" ] || continue
    candidate=$(ls -1 "$d"/build-tools/*/aapt2 "$d"/build-tools/*/aapt2.exe 2>/dev/null | sort -V | tail -n 1 || true)
    if [ -n "$candidate" ]; then echo "$candidate"; return 0; fi
  done
  return 1
}

apk="${1:-}"
aab="${2:-}"
[ -n "$apk" ] || fail "usage: verify-release.sh <apk> [aab]"
[ -f "$apk" ] || fail "APK not found: $apk"
[ -n "$aab" ] && [ ! -f "$aab" ] && fail "AAB not found: $aab"

aapt2="$(find_aapt2)" || fail "aapt2 not found; set ANDROID_HOME or install build-tools"

perm_file="$(mktemp)"
"$aapt2" dump permissions "$apk" > "$perm_file"
cat "$perm_file"
grep -q "android.permission.INTERNET" "$perm_file" \
  && fail "release APK must not request android.permission.INTERNET"

cloud_file="$(mktemp)"
"$aapt2" dump resources "$apk" | grep -A1 "bool/cloud_brain_available" > "$cloud_file" || true
cat "$cloud_file"
grep -q "cloud_brain_available" "$cloud_file" \
  || fail "cloud_brain_available bool not found in the release resource table"
grep -q "false" "$cloud_file" \
  || fail "cloud_brain_available must be false in release builds"
echo "OK: release APK has no INTERNET permission and cloud_brain_available=false"

if [ -n "$aab" ]; then
  # An AAB stores a protobuf manifest; permission strings still live in its
  # string pool. Probe a permission we do ship before trusting the absence of
  # INTERNET, so a silent change in the bundle format cannot fake a pass.
  tmp_manifest="$(mktemp)"
  unzip -p "$aab" base/manifest/AndroidManifest.xml > "$tmp_manifest"
  [ -s "$tmp_manifest" ] || fail "could not read base/manifest/AndroidManifest.xml from $aab"
  grep -qa "android.permission.SYSTEM_ALERT_WINDOW" "$tmp_manifest" \
    || fail "AAB manifest probe failed (expected permission string absent); cannot trust the result"
  grep -qa "android.permission.INTERNET" "$tmp_manifest" \
    && fail "release AAB must not request android.permission.INTERNET"
  echo "OK: release AAB does not request INTERNET (positive control passed)"
fi
