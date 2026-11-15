# 1. The agent runs on the TV itself

**Status:** accepted · 2026-11-15

**Context.** The agentic remote needs an LLM, a decision model and a catalogue. The usual shape is a small server (a
Pi or a laptop) that the TV calls. That's another device to keep on, patch and secure, and friends installing
Pantheon would have to run it too.

**Decision.** The agent is a Java package inside the TV app (`agent/`). All model work is remote API calls (OpenRouter,
TMDB, ElevenLabs); the TV only orchestrates and renders. Keys are set once from a laptop over adb.

**Consequences.** Nothing to host; works anywhere the TV has internet. The TV's CPU is never the bottleneck (a Council
pick is a few kB of JSON; latency is network-bound, ~3–4 s). Keys live on the device, so the control channel has to be
locked down ([0004](0004-adb-only-control-channel.md)). An external agent URL is still supported for anyone who
prefers a server.
