#!/usr/bin/env bash
#
# Compare status bar diagnostics between Home / Google Maps / YouTube states.
#
# Prerequisites: run tools/diagnose_device.sh home, ... maps, ... youtube first.
# This script only compares already-collected output; it does NOT touch the
# device and does NOT modify the system.
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIAG_DIR="$ROOT_DIR/diagnostics"

latest() {
  ls -d "$1"/*/ 2>/dev/null | sort | tail -n 1
}

HOME_RUN=$(latest "$DIAG_DIR/home")
MAPS_RUN=$(latest "$DIAG_DIR/maps")
YOUTUBE_RUN=$(latest "$DIAG_DIR/youtube")

for d in "$HOME_RUN" "$MAPS_RUN" "$YOUTUBE_RUN"; do
  if [ -z "$d" ] || [ ! -d "$d" ]; then
    echo "Missing diagnostics run. Run first:" >&2
    echo "  tools/diagnose_device.sh home" >&2
    echo "  tools/diagnose_device.sh maps" >&2
    echo "  tools/diagnose_device.sh youtube" >&2
    exit 1
  fi
done

echo "Comparing:"
echo "  home:    $HOME_RUN"
echo "  maps:    $MAPS_RUN"
echo "  youtube: $YOUTUBE_RUN"
echo ""

show() {
  local label="$1"; local file="$2"
  echo "===== $label ====="
  for d in "$HOME_RUN" "$MAPS_RUN" "$YOUTUBE_RUN"; do
    echo "--- $(basename "$(dirname "$d")") ($(basename "$d") ) ---"
    cat "$d/$file" 2>/dev/null || echo "(missing)"
  done
  echo ""
}

show "clock_seconds"       07_settings_clock_seconds.txt
show "icon_blacklist"      08_settings_icon_blacklist.txt
show "clock secure keys"   09_settings_list_clock.txt
show "systemui package"    06_pm_path_systemui.txt

echo "===== dumpsys statusbar: home vs maps (diff) ====="
diff "$HOME_RUN/10_dumpsys_statusbar.txt" "$MAPS_RUN/10_dumpsys_statusbar.txt" | head -n 100 || true
echo ""

echo "===== dumpsys statusbar: home vs youtube (diff) ====="
diff "$HOME_RUN/10_dumpsys_statusbar.txt" "$YOUTUBE_RUN/10_dumpsys_statusbar.txt" | head -n 100 || true
echo ""

echo "===== dumpsys window: home vs maps (diff, first 100 lines) ====="
diff "$HOME_RUN/11_dumpsys_window.txt" "$MAPS_RUN/11_dumpsys_window.txt" | head -n 100 || true
echo ""
echo "Hint: look for visibility flags, immersive mode, or SystemUI clock"
echo "properties that change between states."
