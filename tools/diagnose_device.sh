#!/usr/bin/env bash
#
# Diagnose device script — DEVELOPMENT / DIAGNOSTICS ONLY (not part of the APK).
#
# Collects read-only facts about the target Android Box to understand why the
# Status Bar Clock disappears when another app (Google Maps / YouTube) is in
# the foreground.
#
# This script NEVER modifies the system. All commands are read-only.
#
# Usage:
#   tools/diagnose_device.sh [state]
#
#   state = home | maps | youtube   (default: home)
#   Run once with the launcher shown, once with Google Maps in the foreground,
#   once with YouTube in the foreground.
#
#   ADB=/path/to/adb tools/diagnose_device.sh maps
set -u

# --- Locate adb ----------------------------------------------------------------
ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then
    ADB="adb"
  elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
  else
    echo "ERROR: adb not found. Export ADB=/path/to/adb and retry." >&2
    exit 1
  fi
fi

STATE="${1:-home}"
case "$STATE" in
  home|maps|youtube) ;;
  *) echo "ERROR: unknown state '$STATE' (use home|maps|youtube)" >&2; exit 1 ;;
esac

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$ROOT_DIR/diagnostics/$STATE/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$RUN_DIR"

echo "== adb =="
"$ADB" version | head -n 1

echo "== devices ==" | tee "$RUN_DIR/00_adb_devices.txt"
"$ADB" devices -l | tee -a "$RUN_DIR/00_adb_devices.txt"

DEVICE_COUNT=$("$ADB" devices | awk 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "ERROR: no device connected through ADB." >&2
  echo "Connect the Android Box and enable USB/network debugging, then retry." >&2
  exit 2
fi

# --- Collect (read-only) ---------------------------------------------------------
run() {
  local name="$1"; shift
  echo "== $name =="
  "$ADB" shell "$@" > "$RUN_DIR/$name.txt" 2>&1
  cat "$RUN_DIR/$name.txt"
}

run 01_getprop_release       getprop ro.build.version.release
run 02_getprop_sdk           getprop ro.build.version.sdk
run 03_getprop_manufacturer  getprop ro.product.manufacturer
run 04_getprop_model         getprop ro.product.model
run 05_getprop_fingerprint   getprop ro.build.fingerprint
run 06_pm_path_systemui      pm path com.android.systemui
run 07_settings_clock_seconds settings get secure clock_seconds
run 08_settings_icon_blacklist settings get secure icon_blacklist

echo "== 09_settings_list_clock =="
"$ADB" shell settings list secure > "$RUN_DIR/09_settings_list_secure_raw.txt" 2>&1
grep -i clock "$RUN_DIR/09_settings_list_secure_raw.txt" > "$RUN_DIR/09_settings_list_clock.txt" \
  || echo "(no clock-related secure settings found)" > "$RUN_DIR/09_settings_list_clock.txt"
cat "$RUN_DIR/09_settings_list_clock.txt"

run 10_dumpsys_statusbar dumpsys statusbar
run 11_dumpsys_window    dumpsys window windows

echo ""
echo "Done. Output saved in:"
echo "  $RUN_DIR"
