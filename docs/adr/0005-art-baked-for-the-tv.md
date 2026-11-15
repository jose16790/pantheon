# 5. Art is baked for the TV, not computed on it

**Status:** accepted · 2026-11-15

**Context.** Shading layers, full-resolution pictures and per-frame effects cost the TV's GPU and memory; the browser
preview can afford them, the TV can't.

**Decision.** Each theme's tools write TV copies into the app (`android/app/src/main/assets/www`, mirroring `home/`):
1936 px wide, the page's static shading baked in, sprite sheets trimmed and packed, and an OLED black point applied
per picture (near-blacks to true black, eased; `home/tools/oled.py`). The browser keeps the full-resolution originals.

**Consequences.** ~42 fps on an MT8696 with live fire, sky, fog and lighting. The TV copies are committed so friends
can build without Python image tooling.
