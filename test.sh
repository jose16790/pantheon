#!/bin/bash
# Pantheon's checks, run locally (no CI): ./test.sh
#   1. JavaScript: every page script parses (including index.html's inline ones), and the weather wizard's maths
#      matches known astronomy (tests/js)
#   2. Java: unit tests for the agent's pure logic (android/app/src/test)
#   3. Shell: every script parses
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}" ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
ok() { printf "  \033[32m✓\033[0m %s\n" "$1"; }

echo "JavaScript"
TMP="$(mktemp).js"
python3 -c "import re,sys;print('\n'.join(re.findall(r'<script>(.*?)</script>', open(sys.argv[1]).read(), re.S)))" home/index.html > "$TMP"
node --check "$TMP"; rm -f "$TMP"
for f in home/config.js home/core/*.js home/themes/*/*.js; do node --check "$f"; done
ok "all page scripts parse"
node --test tests/js/*.test.mjs 2>&1 | grep -E "^# (pass|fail)" | sed 's/^# /  /'
node --test tests/js/*.test.mjs >/dev/null 2>&1 && ok "weather and astronomy tests"

echo "Java"
(cd android && ./gradlew testReleaseUnitTest --console=plain -q)
n=$(grep -ho 'tests="[0-9]*"' android/app/build/test-results/testReleaseUnitTest/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc)
ok "$n unit tests (agent, news)"

echo "Shell"
for f in build.sh install.sh scripts/*.sh; do bash -n "$f"; done
ok "all scripts parse"
echo "All good."
