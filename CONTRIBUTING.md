# Contributing to Pantheon

Thanks for wanting to help. Pantheon is small and opinionated; a few rules keep it that way.

## Getting set up

```bash
./install.sh --build-only     # tools + the APK, no TV needed
./test.sh                     # every check (run it before every PR)
```

To preview the home screen without a TV, serve `home/` with any static server and open
`index.html?theme=got` (add `&wx=thunder&at=2026-11-14T21:00` to try a weather). The page simulates the TV's apps.

## How we work

- **One change, one branch, one pull request.** `feature/…`, `fix/…`, `docs/…`. Describe what changed and how you
  checked it (on a real TV if it touches the scene or the agent).
- **`./test.sh` passes.** Add a test when you add pure logic (Java: `android/app/src/test`; JS: `tests/js`).
- **Respect the TV's budget.** No canvas blend modes, no per-frame work that could be baked once; check the frame
  rate on the device (`adb shell dumpsys gfxinfo dev.pantheon.adultspace`). See
  [ARCHITECTURE §4](docs/ARCHITECTURE.md#4-rendering-on-a-tv-gpu).
- **Big decisions get an ADR** in `docs/adr/` (context, decision, consequences: a few lines each).

## Making a theme

A theme is a folder, `home/themes/<id>/`: its `scene.js` (enter / leave / per-frame hooks, see `themes/got/scene.js`),
its art, sprites and sounds, and the `tools/` that made them; plus a `ThemeData` class in
`android/app/src/main/java/…/theme/` for colours and pictures, and a card in the theme picker (`home/core/theme.js`).
Good first themes: Marvel (stubbed), Star Wars, Lord of the Rings, Studio Ghibli.

## Art and sound

**Only original or properly licensed assets.** No film stills, posters, logos or soundtracks you don't have the
rights to. AI-generated art is fine when generated for the project; say how it was made in `NOTICE.md`.

## Secrets

Never commit keys, tokens, keystores or `.env` files. Agent keys belong on the TV (`scripts/agent-keys.sh`); the
repo has secret scanning and push protection on.

## Releases (maintainer)

`scripts/make-release-key.sh` once (your own signing key, never committed), then `scripts/release.sh vX.Y.Z`:
checks, a signed APK, a tag and a GitHub Release with notes from `CHANGELOG.md`.

## Security issues

Please don't open a public issue: see [SECURITY.md](SECURITY.md).
