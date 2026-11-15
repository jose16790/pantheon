# Credits and third-party notices

Pantheon stands on two open-source projects. Thank you to both authors.

## hogwarts-tv-home, by ikevinadams

- Repository: https://github.com/ikevinadams/hogwarts-tv-home
- Imported at commit `df626428e7f808caaf3f47d509df03b9d684ab4e`
- Licence: MIT, Copyright (c) 2026 ikevinadams
- Used for: the whole `adult-space/` base. That includes the WebView home-screen shell
  (`MainActivity`), the castle page (`hogwarts/index.html`), D-pad navigation, the
  hopping-owl animation and the asset tools (`hogwarts/tools/`).

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
