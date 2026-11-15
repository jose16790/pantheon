#!/bin/bash
# Downloads the castle fan art (photos, owl sprites, film covers, logos, TV banner) from the
# upstream hogwarts-tv-home repository into this checkout, for a PERSONAL build only.
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

copy() {   # copy <relative dir> [find args...]: art files only, never code
  local rel="$1"; shift
  (cd "$TMP/$rel" && find . -type f "$@" -print0) | while IFS= read -r -d '' f; do
    mkdir -p "$(dirname "$ROOT/adult-space/$rel/$f")"
    cp "$TMP/$rel/$f" "$ROOT/adult-space/$rel/$f"
  done
}
ART=( \( -name '*.jpg' -o -name '*.png' -o -name '*.webp' -o -name '*.webm' \) )
copy hogwarts/img "${ART[@]}"
copy hogwarts/owl "${ART[@]}"
copy hogwarts/covers "${ART[@]}"
copy hogwarts-tv/app/src/main/assets/www/img "${ART[@]}"
copy hogwarts-tv/app/src/main/assets/www/owl "${ART[@]}"
copy hogwarts-tv/app/src/main/assets/www/covers "${ART[@]}"
mkdir -p "$ROOT/adult-space/hogwarts-tv/app/src/main/res/drawable-nodpi"
cp "$TMP/hogwarts-tv/app/src/main/res/drawable-nodpi/banner.jpg" "$ROOT/adult-space/hogwarts-tv/app/src/main/res/drawable-nodpi/banner.jpg"
echo "Done. Fan art is in place (git ignores it)."
