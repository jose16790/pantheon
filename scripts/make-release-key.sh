#!/bin/bash
# Makes your own signing key for Pantheon's release APK (run once, by you: it asks for a password).
#   android/release/pantheon.jks           the key (keep a backup: without it, installed copies can't be updated)
#   android/release/keystore.properties    tells the build where it is (both are git-ignored, never committed)
# Note: a TV with Pantheon signed by the debug key must uninstall it once before installing a release-signed APK
# (Android refuses an update signed by a different key); re-run scripts/agent-keys.sh afterwards.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; D="$ROOT/android/release"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
[ -f "$D/pantheon.jks" ] && { echo "A key already exists at android/release/pantheon.jks. Not replacing it."; exit 1; }
mkdir -p "$D"; chmod 700 "$D"
read -rsp "A password for the key (at least 6 characters): " PW; echo
read -rsp "Again: " PW2; echo
[ "$PW" = "$PW2" ] && [ ${#PW} -ge 6 ] || { echo "Passwords don't match or are too short."; exit 1; }
export PANTHEON_KEY_PW="$PW"
"$JAVA_HOME/bin/keytool" -genkeypair -keystore "$D/pantheon.jks" -alias pantheon -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass:env PANTHEON_KEY_PW -keypass:env PANTHEON_KEY_PW -dname "CN=Pantheon, O=Pantheon" >/dev/null
umask 077
printf 'storeFile=release/pantheon.jks\nstorePassword=%s\nkeyAlias=pantheon\nkeyPassword=%s\n' "$PW" "$PW" > "$D/keystore.properties"
unset PANTHEON_KEY_PW PW PW2
echo "Key made: android/release/pantheon.jks (back it up somewhere safe). Release builds are now signed with it."
