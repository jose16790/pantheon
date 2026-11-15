#!/bin/bash
# Use your own music in the Game of Thrones theme (instead of the original track that ships with Pantheon).
#   scripts/add-music.sh ~/Music/theme.m4a        any audio file ffmpeg can read (mp3, m4a, wav, flac, ogg…)
#   scripts/add-music.sh --remove                  back to the original
# It's converted to sounds/got/music-local.mp3 (levelled, gentle fade in and out so the loop is smooth), which git
# ignores: music you don't own the rights to stays on your machine. Rebuild and install to hear it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
A="$ROOT/adult-space/hogwarts/sounds/got"; B="$ROOT/adult-space/hogwarts-tv/app/src/main/assets/www/sounds/got"
if [ "${1:-}" = "--remove" ]; then rm -f "$A/music-local.mp3" "$B/music-local.mp3"; echo "Back to the original music."; exit 0; fi
[ -f "${1:-}" ] || { echo "usage: $0 <audio file> | --remove"; exit 1; }
command -v ffmpeg >/dev/null || { echo "ffmpeg is needed (brew install ffmpeg)"; exit 1; }
mkdir -p "$A" "$B"
ffmpeg -v error -y -i "$1" -vn -af "loudnorm=I=-18:TP=-1.5:LRA=11,afade=t=in:d=2,areverse,afade=t=in:d=3,areverse" -ac 2 -ar 44100 -b:a 160k "$A/music-local.mp3"
cp "$A/music-local.mp3" "$B/music-local.mp3"
echo "Your music is in place ($(ffprobe -v error -show_entries format=duration -of csv=p=0 "$A/music-local.mp3" | cut -d. -f1) s). Rebuild and install: ./install.sh --tv-only (after adult-space/build.sh)"
