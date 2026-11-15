#!/bin/bash
# Switch off the TV's ads, tracking and unused Google apps. Reversible: `pm disable-user`, never uninstall.
#   scripts/unbloat.sh             show what it would switch off (changes nothing)
#   scripts/unbloat.sh apply       switch them off, and remember them for undo
#   scripts/unbloat.sh undo        switch everything it switched off back on
#
# The safety check (adapted from couch-tv's tv-home/tools/apply.sh, MIT) refuses anything the TV may need: a home
# screen, the setup app, a persistent process, a core part of Android, Play Services, the Play Store, the remote,
# voice, Bluetooth, HDMI CEC, the keyboard and TV inputs, whatever the list below says.
set -euo pipefail
. "$(dirname "$0")/_adb.sh"
HERE="$(cd "$(dirname "$0")" && pwd)"
SERIAL=$("$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} get-serialno | tr -c 'A-Za-z0-9._-\n' _)
LIST="$HERE/tvs/$SERIAL.disabled"; mkdir -p "$HERE/tvs"

# package | why it goes
OFF="
com.google.android.apps.tv.dreamx           | Ambient mode: the idle screensaver with ads and recommendations
com.google.android.backdrop                 | Backdrop: the older ambient screensaver
com.google.android.adservices.api           | Ad services (ad topics and measurement for apps)
com.google.android.feedback                 | Sends crash and feedback reports to Google
com.android.tv.feedbackconsent              | Feedback consent prompts
com.google.android.videos                   | Google TV / Play Movies app (shop and rent; your streaming apps don't need it)
com.google.android.play.games               | Google Play Games
com.android.gallery3d                       | Gallery (photo viewer)
com.google.android.marvin.talkback          | TalkBack screen reader (switch back on if you need it)
"

needed() {   # 0 = the TV may need it: never switched off
  case "$1" in
    com.google.android.apps.tv.launcherx|com.google.android.tvlauncher|com.google.android.tungsten.setupwraith) return 0 ;;
    android|dev.pantheon.*|com.android.systemui|com.android.tv.settings|com.android.vending|com.google.android.gms|com.google.android.gsf) return 0 ;;
    *webview*|com.google.android.ext.*|com.google.android.providers.*|com.android.providers.*|*setupwizard*|*.setup|*provision*) return 0 ;;
    *bluetooth*|*pair*|*remote*|*rcu*|*voice*|*assistant*|*katniss*|com.google.android.tts|*cec*|*inputmethod*|*inputdevice*|*tvinput*|*tvprovider*|*networkstack*|*permissioncontroller*|*packageinstaller*) return 0 ;;
  esac
  A cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
  A pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -q " $1/" && return 0
  A dumpsys package "$1" | grep -q -E "(pkgFlags|flags)=\[.*PERSISTENT" && return 0
  return 1
}

MODE="${1:-dry}"
case "$MODE" in
  dry|apply)
    present=$(A pm list packages | sed 's/package://')
    disabled=$(A pm list packages -d | sed 's/package://')
    echo "$OFF" | while IFS='|' read -r p why; do
      p=$(echo "$p" | sed 's/^ *//; s/ *$//'); why=$(echo "$why" | sed 's/^ *//; s/ *$//'); [ -z "$p" ] && continue
      if ! echo "$present" | grep -qx "$p"; then continue; fi
      if needed "$p"; then echo "  keep  $p  (the TV may need it)"; continue; fi
      if echo "$disabled" | grep -qx "$p"; then echo "  off   $p  (already)"; continue; fi
      if [ "$MODE" = dry ]; then echo "  would switch off  $p  — $why"
      else
        if A pm disable-user --user 0 "$p" | grep -q "new state: disabled"; then
          grep -qx "$p" "$LIST" 2>/dev/null || echo "$p" >> "$LIST"; echo "  switched off  $p  — $why"
        else echo "  could not switch off $p (the TV refused)"; fi
      fi
    done
    if [ "$MODE" = apply ] && A pm list packages -d | grep -qx package:com.google.android.apps.tv.dreamx; then
      A settings put secure screensaver_components com.android.dreams.basic/.Colors
      A settings put secure screensaver_enabled 0
      echo "  screensaver: off (ambient mode is gone; the TV still sleeps on its own timer)"
    fi
    [ "$MODE" = dry ] && echo "Nothing changed. Run: scripts/unbloat.sh apply" || true ;;
  undo)
    [ -f "$LIST" ] || { echo "Nothing to undo for this TV."; exit 0; }
    while read -r p; do [ -n "$p" ] && A pm enable "$p" >/dev/null && echo "  switched back on  $p"; done < "$LIST"
    A pm list packages | grep -qx package:com.google.android.apps.tv.dreamx && {
      A settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
      A settings put secure screensaver_enabled 1; echo "  screensaver: ambient mode again"; }
    rm -f "$LIST" ;;
  *) echo "usage: $0 [apply|undo]"; exit 1 ;;
esac
