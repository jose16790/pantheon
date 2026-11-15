#!/bin/bash
# Make Pantheon the TV's assistant (the remote's mic button opens Pantheon), or give the button back to Google.
#   scripts/assistant.sh pantheon     the mic button -> Pantheon (everywhere on the TV)
#   scripts/assistant.sh google       the mic button -> Google Assistant again (as it was)
#   scripts/assistant.sh status       who has it now
# Uses the TV adb is connected to (set ADB_SERIAL to pick one).
set -euo pipefail
. "$(dirname "$0")/_adb.sh"
ROLE=android.app.role.ASSISTANT
GOOGLE_ASSISTANT=com.google.android.katniss
GOOGLE_RECOGNIZER=com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService

case "${1:-status}" in
  pantheon) A cmd role add-role-holder "$ROLE" dev.pantheon.adultspace ;;
  google)   A cmd role add-role-holder "$ROLE" "$GOOGLE_ASSISTANT"
            A settings put secure voice_recognition_service "$GOOGLE_RECOGNIZER" ;;
  status)   ;;
  *) echo "usage: $0 pantheon|google|status"; exit 1 ;;
esac
echo "assistant:   $(A cmd role get-role-holders "$ROLE")"
echo "recognizer:  $(A settings get secure voice_recognition_service)"
