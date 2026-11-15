# Shared by Pantheon's TV scripts: which adb, which TV.  ADB_SERIAL picks a TV when more than one is connected.
ADB="${ADB:-$(command -v adb || echo /opt/homebrew/share/android-commandlinetools/platform-tools/adb)}"
# no TV named and adb lists more than one (often the same TV twice: by its IP and by its network name): use the first
if [ -z "${ADB_SERIAL:-}" ] && [ "$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l)" -gt 1 ]; then
  ADB_SERIAL="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
A() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} shell "$@" </dev/null | tr -d '\r'; }
"$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} get-state >/dev/null 2>&1 || { echo "No TV connected over adb (see README: Install)."; exit 1; }
