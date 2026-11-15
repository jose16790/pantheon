# Credits and third-party notices

Pantheon is designed and built by Jose ([jose16790](https://github.com/jose16790)). Its Game of Thrones theme is
Pantheon's own, and so will its Marvel theme be. Pantheon stands on two open-source projects. Thank you to both
authors.

## hogwarts-tv-home, by ikevinadams

- Repository: https://github.com/ikevinadams/hogwarts-tv-home
- Imported at commit `df626428e7f808caaf3f47d509df03b9d684ab4e`
- Licence: MIT, Copyright (c) 2026 ikevinadams
- Used for: the home-screen base. That includes the WebView home-screen shell
  (`MainActivity`), the castle page (`hogwarts/index.html`), D-pad navigation, the
  hopping-owl animation and the asset tools (`hogwarts/tools/`), and the whole Hogwarts theme.
- Modified by Pantheon: the voice agent and mic button, the theme picker and the Game of Thrones scene,
  the place and weather under the clock, the headlines, the Home takeover and its self-repair, the unbloat,
  the dock's apps, and the installer.

The upstream licence notes that it covers the code only: *"The pictures and clips are fan
art for personal use, and the film posters and app logos belong to their owners; none of
them are covered by this licence."* For that reason Pantheon does **not** redistribute any
of those images. `scripts/fetch-fanart.sh` downloads them from the upstream repository onto
your own machine for a personal build.

## couch-tv, by Seagey

- Repository: https://github.com/Seagey/couch-tv
- Licence: MIT, Copyright (c) 2026 Seagey
- Used for: the design of the "agentic remote". It is text in, with a model-backed agent
  that controls the TV behind it (`on-tv/android/.../Ask.kt`, `AskWire.kt`,
  `bridge/agentic.py`). Pantheon's home-network address check in `AgentClient.java` is
  adapted from `AskWire.isLocalHost`.

## Pantheon's own artwork (the Game of Thrones theme, by Jose)

Everything in `home/themes/got/` was made for Pantheon. The throne room (`img/throne.jpg`), the library shelf
(`img/shelf.jpg`), the eight season covers (`img/covers/`), the dragon (`dragon/`) and its fire breath were generated with
AI image and video models (via Sume: GPT Image 2.5 and Seedance 2.5) from original descriptions. No HBO artwork, stills
or posters were used. The same goes for the room by day, at golden hour and under cloud (`img/throne_{day,golden,overcast}.jpg`),
the cloud panoramas (`img/clouds_*.webp`), the torch flame and its crackle (`img/torch.webp`, `sounds/fire.mp3`), and the
score (`sounds/music.mp3`, an original track made with Sume Music 1.0; not the show's theme). They're included in this repository under the same MIT licence as the code. They depict an unofficial
fan theme; the names and the world they evoke belong to their owners (below).

## Headlines

The headlines panel shows the top stories from Google News' public RSS feeds (national, world and business, for the
TV's country). Only each story's headline, source and time are shown; they belong to their publishers.

## The agentic remote

- Film and series data, posters and where each title streams: **[TMDB](https://www.themoviedb.org)**. This product uses
  the TMDB API but is not endorsed or certified by TMDB. TMDB's watch-provider data comes from JustWatch.
- Decisions: **Jev** by TypeSafe AI; words: **DeepSeek V4.1 Flash** (Qwen 3.7 Flash as a backup), both through
  [OpenRouter](https://openrouter.ai), with your own key.

## Weather and astronomy

- Weather data from [Open-Meteo](https://open-meteo.com), under CC BY 4.0. The TV's rough location comes from
  [ipapi.co](https://ipapi.co), or [ipwho.is](https://ipwho.is) as a fallback.
- The sun and moon positions and the moon's phase (`home/core/weather.js`) follow the formulas of
  [SunCalc](https://github.com/mourner/suncalc) by Vladimir Agafonkin (BSD 2-Clause), re-derived for this project.

## Fonts

- **Cinzel** (`home/fonts/Cinzel.ttf`), by the Cinzel Project Authors, under the SIL Open Font License 1.1
  (`home/fonts/Cinzel-OFL.txt`). Used for the season captions.

## Trademarks

Harry Potter, Hogwarts, Hedwig, Game of Thrones and Marvel are trademarks of their
respective owners. Pantheon is an unofficial, non-commercial fan project. It is not
affiliated with or endorsed by any of them. Theme names only describe the look a user can
choose to build for their own TV.

## Full upstream licence texts

### hogwarts-tv-home

```
MIT License

Copyright (c) 2026 ikevinadams

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

This licence covers the code. The pictures and clips are fan art for personal use, and the film posters and app
logos belong to their owners; none of them are covered by this licence.
```

### couch-tv

See https://github.com/Seagey/couch-tv/blob/main/LICENSE. A copy is reproduced below.

```
MIT License

Copyright (c) 2026 Seagey

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
