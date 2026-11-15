#!/bin/bash
# Pantheon as the TV's home screen, safely.
#   scripts/home.sh status     what the TV uses now
#   scripts/home.sh apply      Pantheon becomes the home; the Home watch keeps Google's home out of sight
#   scripts/home.sh undo       back to Google's home
#
# Google's home (launcherx), the older launcher and the setup app are NEVER switched off: on some Google TVs that
# leaves the TV stuck in a restart loop (couch-tv, docs/TROUBLESHOOTING.md, "If your TV loops after a restart").
# Instead Pantheon is the chosen home and its Home watch (an accessibility service that only notices which home
# came forward) brings Pantheon back at once. Google's home may flash up for a moment at power on or on Home.
set -euo pipefail
. "$(dirname "$0")/_adb.sh"
PKG=dev.pantheon.adultspace
HOME_ACT=$PKG/.HomeAlias
WATCH=$PKG/.home.HomeWatch
GOOGLE_HOME=com.google.android.apps.tv.launcherx/.home.HomeActivity

status() {
  echo "home (what Home opens):  $(A cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
  echo "accessibility services:  $(A settings get secure enabled_accessibility_services)"
  for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith; do
    A pm list packages -d | grep -qx "package:$p" && echo "WARNING: $p is switched OFF. Switch it back on: scripts/home.sh undo" || true
  done
}

case "${1:-status}" in
  apply)
    # safety first: the TV's own homes and setup app stay (or are put back) on
    for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith; do
      A pm list packages -d | grep -qx "package:$p" && A pm enable "$p" >/dev/null && echo "switched $p back on (the TV needs it to start)" || true
    done
    A cmd package set-home-activity "$HOME_ACT" >/dev/null
    # Pantheon puts its watch back itself if Android drops it (it does on a force-stop): that needs this permission,
    # and Pantheon told that Home mode is on (home/HomeGuard.java)
    A pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || echo "note: couldn't let Pantheon repair its watch (older build?)"
    A am start -n "$PKG/.MainActivity" --ez pantheon_home_mode true >/dev/null 2>&1 || true
    CUR=$(A settings get secure enabled_accessibility_services)
    case ":$CUR:" in
      *":$WATCH:"*) # listed already: re-list it so Android binds it again after an update
         REST=$(echo "$CUR" | tr ':' '\n' | grep -v -e "^$WATCH$" -e '^null$' -e '^$' | paste -sd: - || true)
         if [ -z "$REST" ]; then A settings delete secure enabled_accessibility_services >/dev/null; NEW=$WATCH
         else A settings put secure enabled_accessibility_services "$REST"; NEW="$REST:$WATCH"; fi
         sleep 1 ;;
      *) if [ -z "$CUR" ] || [ "$CUR" = null ]; then NEW=$WATCH; else NEW="$CUR:$WATCH"; fi ;;
    esac
    A settings put secure enabled_accessibility_services "$NEW"
    A settings put secure accessibility_enabled 1
    A input keyevent KEYCODE_HOME
    echo "Pantheon is the home screen; the Home watch is on." ;;
  undo)
    A am start -n "$PKG/.MainActivity" --ez pantheon_home_mode false >/dev/null 2>&1 || true
    A pm revoke "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || true
    A cmd package set-home-activity "$GOOGLE_HOME" >/dev/null || true
    CUR=$(A settings get secure enabled_accessibility_services)
    REST=$(echo "$CUR" | tr ':' '\n' | grep -v -e "^$WATCH$" -e '^null$' -e '^$' | paste -sd: - || true)
    if [ -z "$REST" ]; then A settings delete secure enabled_accessibility_services >/dev/null; A settings put secure accessibility_enabled 0
    else A settings put secure enabled_accessibility_services "$REST"; fi
    A input keyevent KEYCODE_HOME
    echo "Google's home is back; the Home watch is off." ;;
  status) ;;
  *) echo "usage: $0 status|apply|undo"; exit 1 ;;
esac
status
