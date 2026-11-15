#!/bin/bash
# Build Pantheon · Adult Space.
#   ./build.sh                copy the page's code into the app and build the release APK
#   ./build.sh install        ...and install it on the TV adb is connected to
#   ./build.sh --regen-art    re-make the TV-sized pictures first (hogwarts/tools/tv_assets.py: needs python numpy + Pillow)
# The APK is signed with your key in hogwarts-tv/release/ if there is one, else your debug key; a copy lands in dist/.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
WWW="$HERE/hogwarts-tv/app/src/main/assets/www"

# toolchain defaults (Homebrew); anything already set wins
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

# the fan art isn't in the repo (see NOTICE.md): fetch it once for a personal build
if [ ! -f "$HERE/hogwarts-tv/app/src/main/res/drawable-nodpi/banner.jpg" ] || [ ! -f "$WWW/img/castle.jpg" ]; then
  echo "Fan art missing: running scripts/fetch-fanart.sh"
  "$ROOT/scripts/fetch-fanart.sh"
fi

if [ "${1:-}" = "--regen-art" ]; then
  python3 "$HERE/hogwarts/tools/tv_assets.py"
  python3 "$HERE/hogwarts/tools/got_assets.py"                # the Game of Thrones theme's backdrops and dragon
  cp "$HERE/hogwarts/themes/"*.js "$WWW/themes/"
  shift
else
  # code only: the page, its settings and the theme script (the pictures are already TV-sized)
  if command -v node >/dev/null; then                       # never ship a page whose script doesn't parse
    TMPJS="$(mktemp).js"
    python3 -c "import re,sys;print('\n'.join(re.findall(r'<script>(.*?)</script>', open(sys.argv[1]).read(), re.S)))" "$HERE/hogwarts/index.html" > "$TMPJS"
    node --check "$TMPJS"; for f in "$HERE/hogwarts/themes/"*.js; do node --check "$f"; done; rm -f "$TMPJS"
  fi
  mkdir -p "$WWW/themes" "$WWW/dragon"
  cp "$HERE/hogwarts/index.html" "$HERE/hogwarts/config.js" "$WWW/"
  cp "$HERE/hogwarts/themes/"*.js "$WWW/themes/"
  cp "$HERE/hogwarts/dragon/"*.webp "$HERE/hogwarts/dragon/"*.js "$WWW/dragon/"   # (sheets are drawn at TV size already)
  mkdir -p "$WWW/fonts" "$WWW/img/got/covers"; cp "$HERE/hogwarts/fonts/"* "$WWW/fonts/"
  cp "$HERE/hogwarts/img/got/covers/"*.jpg "$WWW/img/got/covers/"
fi

(cd "$HERE/hogwarts-tv" && ./gradlew assembleRelease --console=plain -q)
APK="$HERE/hogwarts-tv/app/build/outputs/apk/release/app-release.apk"
mkdir -p "$ROOT/dist"; cp "$APK" "$ROOT/dist/Pantheon-adult-space.apk"
echo "$ROOT/dist/Pantheon-adult-space.apk"
if [ "${1:-}" = "install" ]; then "$ANDROID_HOME/platform-tools/adb" install -r "$ROOT/dist/Pantheon-adult-space.apk"; fi
