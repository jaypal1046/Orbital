#!/system/bin/sh
# Orbital Dynamic Autonomous Shell Engine
# Zero Accessibility Service - 100% Banking and Payment Safe
# Provides identical capabilities to AccessibilityService via rootless ADB / Shizuku / Local Shell

set -e

CMD="$1"
shift || true

case "$CMD" in
  dump)
    TMP_FILE="/data/local/tmp/orbital_ui_dump.xml"
    /system/bin/uiautomator dump "$TMP_FILE" >/dev/null 2>&1
    if [ -f "$TMP_FILE" ]; then
      cat "$TMP_FILE"
      rm -f "$TMP_FILE"
    else
      echo "<hierarchy rotation=\"0\"></hierarchy>"
    fi
    ;;

  tap)
    X="$1"
    Y="$2"
    /system/bin/input tap "$X" "$Y"
    ;;

  type)
    TEXT="$*"
    /system/bin/input text "$TEXT"
    ;;

  swipe)
    X1="$1"
    Y1="$2"
    X2="$3"
    Y2="$4"
    DUR="${5:-300}"
    /system/bin/input swipe "$X1" "$Y1" "$X2" "$Y2" "$DUR"
    ;;

  key)
    KEY="$1"
    case "$KEY" in
      BACK|back) KEY=4 ;;
      HOME|home) KEY=3 ;;
      ENTER|enter) KEY=66 ;;
      RECENTS|recents|APP_SWITCH) KEY=187 ;;
      TAB|tab) KEY=61 ;;
      DELETE|delete|BACKSPACE) KEY=67 ;;
      VOLUME_UP) KEY=24 ;;
      VOLUME_DOWN) KEY=25 ;;
    esac
    /system/bin/input keyevent "$KEY"
    ;;

  screenshot)
    OUT="${1:-/data/local/tmp/orbital_screenshot.png}"
    /system/bin/screencap -p "$OUT"
    ;;

  foreground)
    dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | head -n 1 || true
    ;;

  launch)
    PKG="$1"
    monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
    ;;

  *)
    echo "Orbital Shell Engine: unrecognized command '$CMD'"
    exit 1
    ;;
esac
