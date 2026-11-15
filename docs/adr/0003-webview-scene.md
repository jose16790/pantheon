# 3. The scene is a web page in a WebView

**Status:** accepted · 2026-11-15

**Context.** The home screen is animation-heavy (fire, sky, weather, a dragon) and themeable. Native Android views or
a game engine would be faster in principle; the upstream project (hogwarts-tv-home) is a WebView page.

**Decision.** Keep the WebView page; add a small Java bridge (`TV`) for what the page can't do (launch apps, open
titles, read feeds, talk to the agent). Each theme is a folder of assets plus a scene script.

**Consequences.** Themes are cheap to make and preview in any browser (the page runs with simulated apps); the same
code renders a launch video frame by frame in headless Chrome. The TV GPU is weak, so the scene is designed to its
budget ([ARCHITECTURE §4](../ARCHITECTURE.md#4-rendering-on-a-tv-gpu)): no blend modes, pre-baked art, compositor-only
motion, paused under panels.
