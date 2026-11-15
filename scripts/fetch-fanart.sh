#!/bin/bash
# Downloads the Hogwarts theme's fan art (photos, owl sprites, film covers), the dock's logos and the TV banner from
# the upstream hogwarts-tv-home repository into this checkout (home/ and the app's copy), for a PERSONAL build only.
# These files are not covered by Pantheon's MIT licence and are never committed (see NOTICE.md).
#   scripts/fetch-fanart.sh           fetch from the pinned upstream commit
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UPSTREAM="https://github.com/ikevinadams/hogwarts-tv-home.git"
COMMIT="df626428e7f808caaf3f47d509df03b9d684ab4e"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

echo "Fetching fan art from $UPSTREAM @ ${COMMIT:0:7} ..."
git -C "$TMP" init -q
git -C "$TMP" remote add origin "$UPSTREAM"
git -C "$TMP" fetch -q --depth 1 origin "$COMMIT"
git -C "$TMP" checkout -q FETCH_HEAD

copy() {   # copy <upstream dir> <our dir> [find args...]: art files only, never code
  local from="$1" to="$2"; shift 2
  [ -d "$TMP/$from" ] || return 0
  (cd "$TMP/$from" && find . -type f "$@" -print0) | while IFS= read -r -d '' f; do
    mkdir -p "$(dirname "$ROOT/$to/$f")"
    cp "$TMP/$from/$f" "$ROOT/$to/$f"
  done
}
ART=( \( -name '*.jpg' -o -name '*.png' -o -name '*.webp' -o -name '*.webm' \) )
UP=hogwarts-tv/app/src/main/assets/www; WWW=android/app/src/main/assets/www
for base in "hogwarts home" "$UP $WWW"; do
  set -- $base
  copy "$1/img/logos" "$2/img/logos" "${ART[@]}"                               # the dock's logos: every theme
  copy "$1/img" "$2/themes/hogwarts/img" "${ART[@]}" -not -path './logos/*'    # the castle, the shelf, the greenhouse
  copy "$1/owl" "$2/themes/hogwarts/owl" "${ART[@]}"
  copy "$1/covers" "$2/themes/hogwarts/covers" "${ART[@]}"
done
mkdir -p "$ROOT/android/app/src/main/res/drawable-nodpi"
cp "$TMP/hogwarts-tv/app/src/main/res/drawable-nodpi/banner.jpg" "$ROOT/android/app/src/main/res/drawable-nodpi/banner.jpg"
echo "Done. Fan art is in place (git ignores it)."
