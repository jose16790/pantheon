# 4. Configuration and test commands only over adb

**Status:** accepted · 2026-11-15

**Context.** `MainActivity` must be exported (it is the home screen). It used to accept the agent's API keys and test
commands (`ask`, `council`, `sleep_test`, …) as intent extras, so **any app on the TV** could swap the keys, spend
credits or make the agent act.

**Decision.** Move them to `control/AdbControl`, an exported broadcast receiver guarded by
`android.permission.DUMP`. The adb shell holds DUMP; it is a signature/privileged/development permission no
third-party app can be granted. Scripts use `am broadcast -n dev.pantheon.adultspace/.control.AdbControl …`. The
main activity keeps only harmless requests (theme, go home).

**Consequences.** The laptop workflow is unchanged (scripts wrap it). Verified on the device: the old `am start` path
is ignored; the built manifest declares the permission.
