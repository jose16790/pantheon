#!/bin/bash
# Pantheon: one command from a fresh clone to Pantheon on your Google TV / Android TV.
#
#   ./install.sh                 the whole thing, step by step (it asks before changing anything on the TV)
#   ./install.sh --build-only    just the tools and the APK (dist/Pantheon-adult-space.apk)
#   ./install.sh --tv-only       skip the build: install the APK already in dist/ and set up the TV
#
# Works on macOS (with Homebrew) and Linux. You need: the TV and this computer on the same Wi-Fi, and about 3 GB
# of disk for the Android tools. Nothing here asks for a password, and every change to the TV can be undone
# (scripts/home.sh undo, scripts/assistant.sh google, scripts/unbloat.sh undo).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
MODE="${1:-all}"
APK="$ROOT/dist/Pantheon-adult-space.apk"

b=$'\e[1m'; d=$'\e[2m'; g=$'\e[32m'; y=$'\e[33m'; r=$'\e[31m'; n=$'\e[0m'
step() { echo; echo "${b}━━ $* ${n}"; }
ok()   { echo "  ${g}✓${n} $*"; }
note() { echo "  ${d}$*${n}"; }
warn() { echo "  ${y}!${n} $*"; }
die()  { echo "  ${r}✗ $*${n}"; exit 1; }
ask()  { local a; read -r -p "  $1 [y/N] " a </dev/tty; [[ "$a" =~ ^[Yy] ]]; }
askv() { local a; read -r -p "  $1 " a </dev/tty; echo "$a"; }

OS="$(uname -s)"
if [ "$OS" = Darwin ]; then
  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
  [ -d "$JAVA_HOME" ] || JAVA_HOME=/usr/local/opt/openjdk@17                     # Intel Macs
  export ANDROID_HOME="${ANDROID_HOME:-$( [ -d /opt/homebrew ] && echo /opt/homebrew/share/android-commandlinetools || echo /usr/local/share/android-commandlinetools )}"
else
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
ADB="$ANDROID_HOME/platform-tools/adb"

echo "${b}Pantheon installer${n}  ${d}(a custom, ad-free home screen for Google TV)${n}"

# ───────────────────────────────────────────────────────────── 1. tools
if [ "$MODE" != --tv-only ]; then
  step "1/5  Tools: Java 17 and the Android SDK"
  if [ "$OS" = Darwin ]; then
    command -v brew >/dev/null || die "Homebrew is needed first: https://brew.sh (one command), then run ./install.sh again."
    [ -x "$JAVA_HOME/bin/java" ] || { note "installing Java 17…"; brew install openjdk@17 >/dev/null; }
    [ -x "$SDKM" ] || { note "installing the Android command-line tools…"; brew install --cask android-commandlinetools >/dev/null; }
  else
    if [ -z "${JAVA_HOME:-}" ]; then
      J=$(ls -d /usr/lib/jvm/*17* 2>/dev/null | head -1 || true)
      [ -n "$J" ] || die "Java 17 is needed: e.g. sudo apt install openjdk-17-jdk (or your distro's), then run ./install.sh again."
      export JAVA_HOME="$J"
    fi
    if [ ! -x "$SDKM" ]; then
      note "downloading Google's Android command-line tools…"
      mkdir -p "$ANDROID_HOME/cmdline-tools"; T="$(mktemp -d)"
      curl -fsSL -o "$T/tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
      unzip -q "$T/tools.zip" -d "$T" && mv "$T/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" && rm -rf "$T"
    fi
  fi
  [ -x "$JAVA_HOME/bin/java" ] || die "Java 17 isn't where expected ($JAVA_HOME). Set JAVA_HOME and run again."
  ok "Java: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
  ok "Android SDK: $ANDROID_HOME"

  if [ ! -d "$ANDROID_HOME/licenses" ] || [ -z "$(ls -A "$ANDROID_HOME/licenses" 2>/dev/null)" ]; then
    echo
    note "The Android SDK comes with Google's licence agreements. Read them and answer y to each to continue."
    "$SDKM" --sdk_root="$ANDROID_HOME" --licenses </dev/tty || true
  fi
  note "fetching the SDK pieces Pantheon needs (once, ~1 GB)…"
  "$SDKM" --sdk_root="$ANDROID_HOME" "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null 2>&1 \
    || die "The SDK download failed. If it says a licence isn't accepted, run ./install.sh again and answer y."
  ok "SDK ready"

  # ─────────────────────────────────────────────────────────── 2. build
  step "2/5  Building Pantheon"
  note "first build downloads Gradle and the castle pictures; a few minutes"
  ADB="$ADB" "$ROOT/adult-space/build.sh" >/tmp/pantheon-build.log 2>&1 || { tail -20 /tmp/pantheon-build.log; die "The build failed (full log: /tmp/pantheon-build.log)."; }
  ok "built $APK"
  [ "$MODE" = --build-only ] && { echo; ok "Done. Install it later with: ./install.sh --tv-only"; exit 0; }
fi
[ -f "$APK" ] || die "No APK in dist/ yet: run ./install.sh (without --tv-only) first."
[ -x "$ADB" ] || ADB="$(command -v adb || true)"; [ -n "$ADB" ] || die "adb isn't installed: run ./install.sh (without --tv-only) first."

# ───────────────────────────────────────────────────────────── 3. the TV
step "3/5  Connecting to your TV"
"$ADB" start-server >/dev/null 2>&1 || true
pick() { "$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}'; }
SERIAL="$(pick)"
if [ -z "$SERIAL" ]; then
  cat <<EOF
  On the TV (once):
    1. Settings → System → About → click ${b}Android TV OS build${n} 7 times ("You are now a developer").
    2. Settings → System → Developer options → turn on ${b}USB debugging${n}, then ${b}Wireless debugging${n}.
    3. Open Wireless debugging → ${b}Pair device with pairing code${n}. Keep that screen open.
EOF
  PAIR=$(askv "Type the IP address & port shown under the pairing code (like 192.168.0.31:43227):")
  [[ "$PAIR" =~ ^[0-9.]+:[0-9]+$ ]] || die "That doesn't look like IP:port. Run ./install.sh --tv-only to try again."
  note "Now type the 6-digit pairing code from the TV:"
  "$ADB" pair "$PAIR" </dev/tty || die "Pairing failed (the code expires when the TV's popup closes). Run ./install.sh --tv-only to try again."
  for i in 1 2 3 4 5 6; do sleep 2; SERIAL="$(pick)"; [ -n "$SERIAL" ] && break; done
  if [ -z "$SERIAL" ]; then
    CONN=$(askv "Type the IP address & port on the main Wireless debugging screen (not the pairing one):")
    "$ADB" connect "$CONN" >/dev/null || true; sleep 2; SERIAL="$(pick)"
  fi
  [ -n "$SERIAL" ] || die "Couldn't connect to the TV. Check it's on the same Wi-Fi, then run ./install.sh --tv-only."
fi
export ADB ADB_SERIAL="$SERIAL"
A() { "$ADB" -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }
ok "connected: $(A getprop ro.product.model) (Android $(A getprop ro.build.version.release))"

# ───────────────────────────────────────────────────────────── 4. install
step "4/5  Installing Pantheon"
WATCHED=$(A settings get secure enabled_accessibility_services | grep -c HomeWatch || true)   # an update, with the Home watch on?
"$ADB" -s "$SERIAL" install -r "$APK" >/dev/null || die "Install failed."
if [ "$WATCHED" -gt 0 ]; then "$ROOT/scripts/home.sh" apply >/dev/null 2>&1 && ok "Home watch re-attached (Android detaches it on every update)"; fi
"$ADB" -s "$SERIAL" shell am start -n dev.pantheon.adultspace/.MainActivity >/dev/null 2>&1 || true
ok "installed and opened. You should see the castle on your TV."

# ───────────────────────────────────────────────────────────── 5. extras
step "5/5  Make it yours (each one is optional and can be undone)"
echo
note "Home screen: the remote's Home button opens Pantheon. Google's home stays installed (switching it off can"
note "leave some TVs stuck restarting); Pantheon just keeps it out of sight. Hold Home for Google's quick settings."
ask "Make Pantheon the home screen?" && "$ROOT/scripts/home.sh" apply | tail -1 || true
echo
note "Mic button: the remote's Assistant/mic button talks to Pantheon (\"open Netflix\", \"pause\", \"volume up\"…)"
note "instead of Google Assistant."
ask "Give the mic button to Pantheon?" && "$ROOT/scripts/assistant.sh" pantheon | head -1 || true
echo
note "Clean-up: switch off the idle screensaver ads, ad services, feedback reports and a few unused Google apps."
LEFT="$("$ROOT/scripts/unbloat.sh" | grep "would switch off" || true)"
if [ -z "$LEFT" ]; then ok "nothing left to switch off on this TV"
else
  note "This is what it would switch off on your TV:"; echo "$LEFT" | sed 's/^/  /'
  ask "Switch those off?" && "$ROOT/scripts/unbloat.sh" apply | tail -1 || true
fi
echo
T=$(askv "Theme: [1] Game of Thrones  [2] Hogwarts  [3] Marvel (colours only)  (Enter: leave it; new installs start on Game of Thrones):")
case "$T" in 1) TH=got ;; 2) TH=hogwarts ;; 3) TH=marvel ;; *) TH="" ;; esac
[ -n "$TH" ] && "$ADB" -s "$SERIAL" shell am start -n dev.pantheon.adultspace/.MainActivity --es theme "$TH" >/dev/null 2>&1 && ok "theme: $TH"

echo
echo "${g}${b}All done.${n} Enjoy Pantheon."
note "Change theme any time with the crown (Up from the dock, then Left). Voice: press the mic and say \"open YouTube\"."
note "Wireless debugging switches itself off when the TV restarts; that's fine, it's only needed to install or update."
note "Update later: git pull && ./install.sh"
