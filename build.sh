#!/bin/bash
# Build Pantheon.
#   ./build.sh                copy the page into the TV app and build the release APK
#   ./build.sh install        ...and install it on the TV adb is connected to
#   ./build.sh --regen-art    re-make the TV-sized pictures first (the themes' tools: needs python numpy + Pillow)
# Layout: home/ is the page (core/ + themes/<name>/), android/ is the TV app; the app's copy of the page
# (android/app/src/main/assets/www) mirrors home/, with the pictures at TV size. The APK is signed with your key in
# android/release/ if there is one, else your debug key; a copy lands in dist/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
HOME_DIR="$ROOT/home"
WWW="$ROOT/android/app/src/main/assets/www"

# toolchain defaults (Homebrew); anything already set wins
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

# the Hogwarts theme's pictures aren't in the repo (see NOTICE.md): fetch them once for a personal build
if [ ! -f "$ROOT/android/app/src/main/res/drawable-nodpi/banner.jpg" ] || [ ! -f "$WWW/themes/hogwarts/img/castle.jpg" ]; then
  echo "Hogwarts pictures missing: running scripts/fetch-fanart.sh"
  "$ROOT/scripts/fetch-fanart.sh"
fi

if [ "${1:-}" = "--regen-art" ]; then
  python3 "$HOME_DIR/themes/hogwarts/tools/tv_assets.py"         # the page, the castle, the owl, the covers (starts afresh)
  python3 "$HOME_DIR/themes/got/tools/got_assets.py"             # the throne room, the dragon, the sounds
  shift
fi

# the code (the pictures are already TV-sized): never ship a page whose script doesn't parse
if command -v node >/dev/null; then
  TMPJS="$(mktemp).js"
  python3 -c "import re,sys;print('\n'.join(re.findall(r'<script>(.*?)</script>', open(sys.argv[1]).read(), re.S)))" "$HOME_DIR/index.html" > "$TMPJS"
  node --check "$TMPJS"; rm -f "$TMPJS"
  for f in "$HOME_DIR/core/"*.js "$HOME_DIR/themes/"*/*.js; do node --check "$f"; done
fi
mkdir -p "$WWW/core" "$WWW/fonts"
cp "$HOME_DIR/index.html" "$HOME_DIR/config.js" "$WWW/"
cp "$HOME_DIR/core/"*.js "$WWW/core/"
cp "$HOME_DIR/fonts/"* "$WWW/fonts/"
# Game of Thrones: its scene, sprite sheets (drawn at TV size already), covers, sky, torches and sounds
G="$HOME_DIR/themes/got"; GW="$WWW/themes/got"
mkdir -p "$GW/dragon" "$GW/img/covers" "$GW/sounds"
cp "$G/scene.js" "$GW/"
cp "$G/dragon/"*.webp "$G/dragon/"*.js "$GW/dragon/"
cp "$G/img/covers/"*.jpg "$GW/img/covers/"
cp "$G/img/glass.png" "$G/img/clouds_"*.webp "$G/img/torch".* "$G/img/seal.webp" "$GW/img/"
cp "$G/sounds/"*.mp3 "$G/sounds/"*.js "$GW/sounds/"            # fire, music (and your own: scripts/add-music.sh)
# every theme's recorded voice lines (home/tools/voice_lines.py): the agent plays them while it works
for d in "$HOME_DIR/themes/"*/voice; do
  [ -d "$d" ] || continue; t="$(basename "$(dirname "$d")")"; mkdir -p "$WWW/themes/$t/voice"; cp "$d/"* "$WWW/themes/$t/voice/"
done

(cd "$ROOT/android" && ./gradlew assembleRelease --console=plain -q)
APK="$ROOT/android/app/build/outputs/apk/release/app-release.apk"
mkdir -p "$ROOT/dist"; cp "$APK" "$ROOT/dist/Pantheon.apk"
echo "$ROOT/dist/Pantheon.apk"
if [ "${1:-}" = "install" ]; then "$ANDROID_HOME/platform-tools/adb" install -r "$ROOT/dist/Pantheon.apk"; fi
