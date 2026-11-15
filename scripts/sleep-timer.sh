#!/bin/bash
# The TV's sleep timer: after N minutes with no button pressed, the TV goes to sleep. On Pantheon's home screen a
# 30-second countdown shows first (any button stops it); in other apps the TV's own timer does it (a playing video
# keeps the TV awake). Sets both to match.
#   scripts/sleep-timer.sh 30        30 minutes (default)
#   scripts/sleep-timer.sh off       Pantheon's countdown off (the TV's own timer goes back to 20 minutes)
# Uses the TV adb is connected to (set ADB_SERIAL to pick one).
set -euo pipefail
. "$(dirname "$0")/_adb.sh"
M="${1:-30}"
if [ "$M" = off ]; then
  A settings put system screen_off_timeout 600000; A settings put secure sleep_timeout 1200000
  A am broadcast -n dev.pantheon.adultspace/.control.AdbControl --ei sleep_minutes 0 >/dev/null 2>&1
  echo "Pantheon's countdown is off; the TV's own timer is back to 10 minutes (screen) / 20 (sleep)."; exit 0
fi
case "$M" in ''|*[!0-9]*) echo "usage: $0 <minutes>|off"; exit 1 ;; esac
MS=$(( (M + 1) * 60000 ))          # the TV's own timer a minute later, so Pantheon's countdown comes first
A settings put system screen_off_timeout "$MS"
A settings put secure sleep_timeout "$MS"
A am broadcast -n dev.pantheon.adultspace/.control.AdbControl --ei sleep_minutes "$M" >/dev/null 2>&1
echo "Sleep timer: $M minutes (countdown for the last 30 seconds on Pantheon's home screen)."
