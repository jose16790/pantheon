# 2. A decision model decides; a language model speaks

**Status:** accepted · 2026-11-15

**Context.** Two kinds of work: bounded choices (which route is this utterance? how well does each of 30 titles fit?
which row does a headline belong in?) and open-ended language (understand a request, explain a pick, answer in
character). An LLM can do both, but bounded choices through an LLM are slow (1–3 s), costly per item, and return
free text to parse.

**Decision.** Use **Jev 1.14** (TypeSafe's decision model, via OpenRouter's Decisions API) for every bounded choice:
typed `choice` / `score` questions, many per call, with probabilities and confidence. Use a small fast chat model
(**DeepSeek V4.1 Flash**, Qwen 3.7 Flash as backup) only for words. Pin both model versions.

**Consequences.** Routing in ~0.3 s for ~$0.00002; 30 candidates scored in one ~0.3 s call; headline sorting for
~$0.00003 a refresh. Probabilities give thresholds (finance ≥ 0.75) and a natural fallback when confidence is low.
Two providers' behaviours to pin and test instead of one.
