#!/bin/bash
# Publishes a release: checks, a signed APK, a tag, and a GitHub Release with the APK (and the demo videos, if made).
#   scripts/release.sh v1.0.0
# Needs your key (scripts/make-release-key.sh) and gh signed in. Notes come from CHANGELOG.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
V="${1:?usage: scripts/release.sh vX.Y.Z}"; REPO=jose16790/pantheon-tv-of-the-gods
[ -f android/release/keystore.properties ] || { echo "No release key yet: run scripts/make-release-key.sh first."; exit 1; }
[ "$(git branch --show-current)" = main ] && [ -z "$(git status --porcelain)" ] || { echo "Release from a clean main."; exit 1; }
git pull -q --ff-only origin main
./test.sh
./build.sh
BT=$(ls -d "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/build-tools/* | tail -1)
"$BT/apksigner" verify --print-certs dist/Pantheon.apk | grep -q "CN=Pantheon" || { echo "The APK isn't signed with the release key."; exit 1; }
cp dist/Pantheon.apk "dist/Pantheon-$V.apk"
NOTES=$(awk -v v="${V#v}" '$0 ~ "^## "v {on=1; next} on && /^## / {exit} on' CHANGELOG.md)
ASSETS=("dist/Pantheon-$V.apk")
for f in brag-output/brag.mp4 brag-output/brag-vertical.mp4; do [ -f "$f" ] && ASSETS+=("$f"); done
if gh release view "$V" --repo $REPO >/dev/null 2>&1; then gh release upload "$V" "${ASSETS[@]}" --repo $REPO --clobber
else git tag -a "$V" -m "Pantheon $V" && git push -q origin "$V" && gh release create "$V" "${ASSETS[@]}" --repo $REPO --title "Pantheon $V" --notes "$NOTES"; fi
echo "Released $V: https://github.com/$REPO/releases/tag/$V"
