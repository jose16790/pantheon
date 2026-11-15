# 6. Memory is local, small and erasable

**Status:** accepted · 2026-11-15

**Context.** Follow-ups ("and what about San Francisco?") and personal picks need memory; unbounded transcripts would
grow cost and latency and hold more personal data than needed.

**Decision.** `agent/Memory`, in the app's private storage: the last 6 exchanges for 15 minutes, the Council's last
picks for an hour, the last 15 titles opened, and at most 40 short facts. After each exchange the chat model decides
whether anything is worth keeping (never passwords, money or health). Recall and forget by voice.

**Consequences.** A few hundred words of context per request at most; memory shapes answers, picks and Jev's scoring.
Memory goes in the system prompt and the question stays the user message, so web search searches the question.
