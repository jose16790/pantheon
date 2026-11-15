# Security policy

## Reporting a vulnerability

Please report privately through GitHub: **Security → Report a vulnerability** on this repository (private advisory).
Don't open a public issue for anything that could be exploited. Expect a reply within a week.

## What matters here

Pantheon runs as a TV's home screen, holds API keys for the agent, and can launch apps and change the volume. In
scope, for example:

- any way for **another app on the TV** to set the agent's keys, read them, make the agent act or spend credits
  (configuration is meant to be adb-only: `control/AdbControl`, guarded by `android.permission.DUMP`);
- any way to make the agent run something outside its typed action set (launch, key, volume, search, theme, sleep);
- keys or personal memory leaving the TV other than in the requests described in
  [ARCHITECTURE §6](docs/ARCHITECTURE.md#6-security-and-privacy);
- prompt injection from fetched content (headlines, web results) that leads to an action.

The security model is described in [docs/ARCHITECTURE.md §6](docs/ARCHITECTURE.md#6-security-and-privacy) and
[ADR 0004](docs/adr/0004-adb-only-control-channel.md).
