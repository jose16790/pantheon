#!/bin/bash
# Give Pantheon's agent (the agentic remote and smarter voice) its keys. They're checked here, then sent straight to
# the TV, where only the Pantheon app can read them. They never go in the repo, and nothing here prints them.
#   scripts/agent-keys.sh            asks for both keys (typing stays hidden)
#   scripts/agent-keys.sh --clear    removes them from the TV (the agent switches off)
# Keys:  OpenRouter  https://openrouter.ai/keys          (Jev + DeepSeek; add a few dollars of credit)
#        TMDB        https://www.themoviedb.org/settings/api   (free; the v3 API key or the v4 read access token)
#        ElevenLabs  https://elevenlabs.io/app/settings/api-keys  (optional: the agent's voice, e.g. the Hand of the King;
#                    without it the TV's own voice speaks. Its library voices are added to your account here.)
# You can also pass them as OPENROUTER_API_KEY, TMDB_API_KEY and ELEVENLABS_API_KEY. Set ADB_SERIAL to pick a TV.
set -euo pipefail
. "$(dirname "$0")/_adb.sh"
send() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} shell "am start -n dev.pantheon.adultspace/.MainActivity --es openrouter_key '$1' --es tmdb_key '$2' --es elevenlabs_key '$3'" </dev/null >/dev/null; }
# the themes' voices (ElevenLabs public library): voice id and owner. The Hand of the King; the Headmaster's portrait.
VOICES="Ix8C14HEHgIQkJswik2o:967a5f5942acb44079855690c3a292c8637dbc54ca90436a477c5551af7d761c htZQqY7WtacRNV7s62Iy:397f6e5b26cb1a88607f90099883087353fba26ec45768706222eca2327d87fc"

if [ "${1:-}" = "--clear" ]; then send "" "" ""; echo "Keys removed from the TV."; exit 0; fi

OR="${OPENROUTER_API_KEY:-}"; TM="${TMDB_API_KEY:-}"
[ -n "$OR" ] || { read -rsp "OpenRouter API key (sk-or-…): " OR; echo; }
[ -n "$TM" ] || { read -rsp "TMDB API key or read access token: " TM; echo; }
EL="${ELEVENLABS_API_KEY-}"
[ -n "${ELEVENLABS_API_KEY+x}" ] || { read -rsp "ElevenLabs API key (optional, Enter to skip): " EL; echo; }
for v in "$OR" "$TM"; do case "$v" in *"'"*|*" "*|"") echo "That key looks wrong (empty, or has a space or quote)."; exit 1 ;; esac; done

printf "checking OpenRouter… "
code=$(curl -s -o /dev/null -w "%{http_code}" https://openrouter.ai/api/v1/key -H "Authorization: Bearer $OR")
[ "$code" = 200 ] && echo ok || { echo "refused (HTTP $code). Check the key at https://openrouter.ai/keys"; exit 1; }
printf "checking TMDB… "
if [[ "$TM" == eyJ* ]]; then code=$(curl -s -o /dev/null -w "%{http_code}" https://api.themoviedb.org/3/configuration -H "Authorization: Bearer $TM")
else code=$(curl -s -o /dev/null -w "%{http_code}" "https://api.themoviedb.org/3/configuration?api_key=$TM"); fi
[ "$code" = 200 ] && echo ok || { echo "refused (HTTP $code). Check it at https://www.themoviedb.org/settings/api"; exit 1; }

if [ -n "$EL" ]; then
  printf "checking ElevenLabs… "
  code=$(curl -s -o /dev/null -w "%{http_code}" https://api.elevenlabs.io/v1/user/subscription -H "xi-api-key: $EL")
  [ "$code" = 200 ] && echo ok || { echo "refused (HTTP $code). Check it at https://elevenlabs.io/app/settings/api-keys"; exit 1; }
  for v in $VOICES; do                          # add the themes' voices to your account (already there: no harm)
    curl -s -o /dev/null -X POST "https://api.elevenlabs.io/v1/voices/add/${v#*:}/${v%%:*}" -H "xi-api-key: $EL" \
      -H "Content-Type: application/json" -d "{\"new_name\":\"Pantheon ${v%%:*}\"}" || true
  done
fi

send "$OR" "$TM" "$EL"
echo "Sent to the TV. The agent is on: Up from the dock to the Council (or just ask the mic)."
