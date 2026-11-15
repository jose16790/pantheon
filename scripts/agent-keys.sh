#!/bin/bash
# Give Pantheon's agent (the agentic remote and smarter voice) its keys. They're checked here, then sent straight to
# the TV, where only the Pantheon app can read them. They never go in the repo, and nothing here prints them.
#   scripts/agent-keys.sh            asks for both keys (typing stays hidden)
#   scripts/agent-keys.sh --clear    removes them from the TV (the agent switches off)
# Keys:  OpenRouter  https://openrouter.ai/keys          (Jev + DeepSeek; add a few dollars of credit)
#        TMDB        https://www.themoviedb.org/settings/api   (free; the v3 API key or the v4 read access token)
# You can also pass them as OPENROUTER_API_KEY and TMDB_API_KEY. Set ADB_SERIAL to pick a TV.
set -euo pipefail
. "$(dirname "$0")/_adb.sh"
send() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} shell "am start -n dev.pantheon.adultspace/.MainActivity --es openrouter_key '$1' --es tmdb_key '$2'" </dev/null >/dev/null; }

if [ "${1:-}" = "--clear" ]; then send "" ""; echo "Keys removed from the TV."; exit 0; fi

OR="${OPENROUTER_API_KEY:-}"; TM="${TMDB_API_KEY:-}"
[ -n "$OR" ] || { read -rsp "OpenRouter API key (sk-or-…): " OR; echo; }
[ -n "$TM" ] || { read -rsp "TMDB API key or read access token: " TM; echo; }
for v in "$OR" "$TM"; do case "$v" in *"'"*|*" "*|"") echo "That key looks wrong (empty, or has a space or quote)."; exit 1 ;; esac; done

printf "checking OpenRouter… "
code=$(curl -s -o /dev/null -w "%{http_code}" https://openrouter.ai/api/v1/key -H "Authorization: Bearer $OR")
[ "$code" = 200 ] && echo ok || { echo "refused (HTTP $code). Check the key at https://openrouter.ai/keys"; exit 1; }
printf "checking TMDB… "
if [[ "$TM" == eyJ* ]]; then code=$(curl -s -o /dev/null -w "%{http_code}" https://api.themoviedb.org/3/configuration -H "Authorization: Bearer $TM")
else code=$(curl -s -o /dev/null -w "%{http_code}" "https://api.themoviedb.org/3/configuration?api_key=$TM"); fi
[ "$code" = 200 ] && echo ok || { echo "refused (HTTP $code). Check it at https://www.themoviedb.org/settings/api"; exit 1; }

send "$OR" "$TM"
echo "Sent to the TV. The agent is on: Up from the dock to the Council (or just ask the mic)."
