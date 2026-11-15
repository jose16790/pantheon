# Pantheon

A custom, ad-free Android TV home screen with themes that come alive, live weather, headlines, and a voice agent
behind the remote's microphone. Designed and built by **Jose** ([jose16790](https://github.com/jose16790)).

| Theme | |
|---|---|
| **Game of Thrones** (the default) | **Pantheon's own theme, fully built by Jose:** a firelit throne room that follows the real weather and daylight outside, Drogon breathing fire on the app you pick, a royal greeting, the Ravens' headlines, and the eight seasons as rustic books. All its artwork, sound and code were made for Pantheon. |
| **Hogwarts** | The animated castle from [hogwarts-tv-home](https://github.com/ikevinadams/hogwarts-tv-home) by **ikevinadams**, which Pantheon is built on. Pantheon has modified it with several new features (below). |
| **Marvel** | Coming next, also Pantheon's own. Only its colours exist so far. |

**What Pantheon adds, in every theme:**

- **The Small Council:** an agentic remote that picks what to watch from your services (time, genres, "something
  like…"), using TMDB, Jev and DeepSeek. Voice uses it too.

- **It's the home screen, for real.** The Home button and turning on the TV go to Pantheon, never Google's home, and
  the ads and bloat are switched off (reversibly).
- **Voice:** the remote's mic button is Pantheon's. Simple commands ("open YouTube", "volume to 30", "switch to
  Game of Thrones") run on the TV itself, and everything else goes to your own agent. The design follows the
  agentic remote in [couch-tv](https://github.com/Seagey/couch-tv) by **Seagey**.
- **Theme picker:** the crown in the top row switches the whole home, or just say "switch to Hogwarts".
- **Where you are:** the place, temperature and weather under the clock, wherever the TV is.
- **Headlines:** the top national, world and finance stories for the TV's country, rotating through the top ten.
  With the agent's key, Jev sorts every story into its row (so the Iron Bank shows only finance).
- **One-command install** for friends, below.

> Unofficial fan project. Not affiliated with Warner Bros., HBO, Marvel/Disney, Google or the app makers whose logos
> the dock shows. See [NOTICE.md](NOTICE.md).

## Quick start (for friends)

You need a Mac (with [Homebrew](https://brew.sh)) or Linux, and your TV on the same Wi-Fi.

```bash
git clone https://github.com/jose16790/pantheon-tv-of-the-gods.git && cd pantheon-tv-of-the-gods && ./install.sh
```

The installer sets up the tools, builds Pantheon, shows you how to turn on wireless debugging on the TV, pairs with
it (you type the 6-digit code from the TV), installs Pantheon, and then asks, one at a time, whether to make it your
home screen, give it the mic button, clean out the ads, and which theme to start with. Run it in a normal terminal
window, since it asks questions. To update later: `git pull && ./install.sh`.

## Layout

Pantheon is an engine and its themes. Each theme lives in its own folder with everything it needs, so a new theme
(Marvel is next) is a new folder.

```
home/                       the home screen: one page, shared by every theme
  index.html                the shell: clock, dock, top row, sections, the remote's keys, the mascot
  config.js                 your dock's apps and where the library's titles open
  core/
    theme.js                applies a theme (colours, pictures, scene) and the theme picker
    weather.js              the weather wizard: the weather outside, the sun's and moon's positions, the moon's phase
    news.js                 the headlines panel
    council.js              the Small Council: the agentic remote's panel
    conversation.js         the voice conversation on a scroll (The King / The Hand)
    sleep.js                the sleep timer's countdown
  fonts/                    Cinzel (SIL OFL)
  img/logos/                the dock's app logos (fetched)
  themes/
    got/                    Game of Thrones, Pantheon's own
      scene.js              Drogon and his fire, torches, sky, room light, fog, dust, sound, the greeting scroll
      img/ dragon/ sounds/  its artwork, sprites and sound (made for Pantheon; img/src/ has the originals)
      tools/                how they were made, and their TV-sized copies (got_assets.py)
    hogwarts/               Hogwarts, from hogwarts-tv-home
      img/ owl/ covers/     the castle, Hedwig and the films (fetched: scripts/fetch-fanart.sh)
      tools/                upstream's picture tools, and the TV-sized copies (tv_assets.py)
android/                    the Android TV app: a full-screen WebView and a JS bridge called `TV`
  app/src/main/assets/www/  the app's copy of home/, with pictures at TV size (made by build.sh)
  app/src/main/java/dev/pantheon/adultspace/
    MainActivity.java       the WebView, the bridge, remote keys
    voice/                  mic → speech-to-text → local commands or the agent; the agent's actions
    assist/                 Pantheon as the TV's assistant (the remote's mic button)
    home/                   the Home watch and its self-repair (Pantheon stays the home screen)
    weather/Weather.java    where the TV is (from its internet connection) and the weather there
    news/News.java          the headlines, by the TV's country
    agent/                  the agentic remote and smarter voice: TMDB, Jev and DeepSeek (via OpenRouter)
    theme/                  ThemeData: GameOfThronesTheme, HogwartsTheme, MarvelTheme; ThemeManager (default: got)
build.sh                    copies home/ into the app and builds dist/Pantheon.apk
install.sh                  one-command setup: tools, build, pairing, install, home screen, unbloat, theme
scripts/                    home.sh, unbloat.sh, assistant.sh, agent-keys.sh, sleep-timer.sh, add-music.sh, fetch-fanart.sh
docs/hogwarts-tv-home/      the original castle project's README, guide and prompt, kept for reference
```

## Build by hand

(`./install.sh` does all of this.) Requirements: JDK 17 and the Android SDK (platform 35). On macOS with Homebrew:

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
```

Then:

```bash
./build.sh                      # fetches the Hogwarts pictures on first run, builds dist/Pantheon.apk
./build.sh --regen-art          # also re-makes the TV-sized pictures (needs python3 with numpy and Pillow)
```

## Install on a Google TV (Wi-Fi ADB)

On the TV: *Settings → System → About →* click *Android TV OS build* 7 times. Then *Settings → System → Developer
options →* turn on *USB debugging*, then *Wireless debugging*, and note the IP and port it shows.

```bash
adb connect <TV-IP>:5555          # Android 14 Google TV: use "adb pair" first with the pairing code
adb install -r dist/Pantheon.apk
```

The release build offers itself as a home screen. Choose it when you press Home, or set it as the default.

## Pantheon as the home screen

```bash
scripts/home.sh apply      # Home on the remote opens Pantheon        (undo: scripts/home.sh undo)
scripts/unbloat.sh         # list the ads, tracking and unused Google apps it would switch off
scripts/unbloat.sh apply   # switch them off (reversible)            (undo: scripts/unbloat.sh undo)
```

Google's home is **never switched off**: on some Google TVs that leaves the TV stuck restarting (learnt by
couch-tv). Pantheon is the chosen home instead, and its **Home watch** (`home/HomeWatch.java`, an accessibility
service) brings Pantheon back whenever Google's home screen comes forward, at power on or on a short Home
press. Google's home may flash up for a moment. **Holding Home** still opens Google's dashboard, with the
Settings gear. A **crash guard** stands the watch aside for 10 minutes if Pantheon crashes twice in 5, so the TV
can't get stuck.

Pantheon's top row (Up from the dock): **Wi-Fi**, **TV settings**, **Themes** (the crown), **Sound** (Game of Thrones),
**Ask** (voice), **Customise**.

`unbloat.sh` switches off ambient mode (the idle screensaver with ads), ad services, feedback reporting, the
Google TV / Play Movies shop, Play Games, Gallery and TalkBack. It refuses anything the TV needs: the homes, the
setup app, Play Services, the Play Store, the remote, Bluetooth, voice, the keyboard, HDMI and Wi-Fi.

Android silently switches an app's accessibility service off when the app is force-stopped, which would let the
TV wake to Google's home. So `home.sh apply` also lets Pantheon repair its own watch (it grants the one permission
that needs, `WRITE_SECURE_SETTINGS`, which only adb can grant): Pantheon checks the watch at boot, after an update,
when it opens, and, while it's your assistant, every time the screen turns on (`home/HomeGuard.java`).

**Sleep timer.** After 30 minutes with no button pressed, the TV goes to sleep. On Pantheon's home screen a
30-second countdown shows first ("The night grows long, Your Grace"); any button stops it. You can also just say
"turn off the TV". Set the time (the TV's own timer is set to match, for other apps):

```bash
scripts/sleep-timer.sh 30       # minutes; "off" turns Pantheon's countdown off
```

Note: wireless debugging switches itself off when the TV restarts. Turn it back on in Developer options (the
Mac stays paired).

## Voice

**Simple commands run on the TV itself**, with no agent and no network (`voice/LocalCommands.java`):

| Say | Does |
|---|---|
| "open YouTube" / "launch Disney Plus" / "go to Crave" | opens that app (any installed app, by name) |
| "go home" | the home screen |
| "pause" / "play" / "next" / "previous" | media keys for whatever is playing |
| "volume up" / "louder" / "mute" | volume, one step |
| "set volume to 30" / "volume up by three" / "turn it down 5" | volume to a level (percent) or by steps |
| "search for Dune" / "find The Bear" | the TV's own search |

**Everything else goes to the agent** (below). Its reply can include actions for the TV to run
(`voice/Actions.java`), so the agent decides and the TV acts:

```json
{"reply": "Opening Disney+", "actions": [{"type": "launch", "app": "Disney+"}]}
```

Action types: `launch {app}`, `key {key: home|play_pause|play|pause|next|previous|volume_up|volume_down|mute}`,
`search {query}`.

Move the agent without rebuilding:

```bash
adb shell am start -n dev.pantheon.adultspace/.MainActivity --es agent_url http://192.168.0.100:8080/agent
```

## Voice agent

| What | Where |
|---|---|
| Endpoint | saved with `--es agent_url …` (above); built-in default `agent_url` in `res/values/strings.xml` |
| Plain http | allowed only to home-network addresses (checked in `AgentClient.java`) |
| Request | `POST {"prompt", "source":"voice", "space":"adult", "theme", "device", "ts"}` |
| Response | logged in full. Shown on screen if JSON with `reply`/`text`/`message`, or as plain text |
| Logs | `adb logcat -s Pantheon/Agent Pantheon/Voice Pantheon/Local Pantheon/Actions Pantheon/Assist Pantheon/Keys` |

Ways to start listening:

1. **The remote's mic / Assistant button.** Google TV gives this button to the system assistant (Google Assistant),
   so Pantheon registers as an assistant too (`assist/`). Hand it the button, and give it back, with:

   ```bash
   scripts/assistant.sh pantheon     # mic button -> Pantheon, everywhere on the TV
   scripts/assistant.sh google       # mic button -> Google Assistant again
   ```

   With Pantheon on screen, the castle listens and shows the banner. Over another app, a small listener opens
   and shows the reply as a toast. Speech-to-text is still Google's: Pantheon forwards it, so voice typing elsewhere
   keeps working.
2. **The mic icon.** From the dock press **Up** to reach the top-right icons, **Left** to the mic, then **OK**.
3. The search key, or a remote's bookmark/star key, if it sends one.

A quick stand-in for the Pi while testing (any machine at that address):

```bash
python3 -c "
from http.server import BaseHTTPRequestHandler, HTTPServer; import json
class H(BaseHTTPRequestHandler):
    def do_POST(s):
        b=json.loads(s.rfile.read(int(s.headers['Content-Length']))); print(b)
        out=json.dumps({'reply':'Heard: '+b['prompt']}).encode()
        s.send_response(200); s.send_header('Content-Type','application/json'); s.end_headers(); s.wfile.write(out)
HTTPServer(('0.0.0.0',8080),H).serve_forever()"
```

## The Small Council (the agentic remote)

"What should I watch?", answered from **your** services. Up from the dock to the screen-and-star icon (*The Small
Council* in Game of Thrones, *The Sorting Hat* in Hogwarts), or just say it to the mic: "something light for 40
minutes", "like Severance but funnier".

- **Time you have:** 20 minutes to 3 hours, or any length (series are matched by their episode length).
- **Movie or series**, **genre pills**, and **"something like…"** in your own words.
- **Picks:** eight cards with the poster, the service it streams on for you, and one line on why. OK opens it.

How it decides, all on the TV (`android/.../agent/`):

| Step | Who | |
|---|---|---|
| Understand your words, suggest fitting titles | **DeepSeek V4.1 Flash** (OpenRouter) | ~1 s |
| Find candidates on your installed services, in the TV's country, that fit your time | **TMDB** | ~1–2 s |
| Score every candidate against your request in one call | **Jev 1.14**, TypeSafe's decision model (OpenRouter) | ~0.3 s |
| One line on why each pick fits | **DeepSeek** | after the cards appear |

### The Hand of the King (voice)

Press the mic and ask anything. Simple commands still run instantly on the TV; everything else is routed by Jev:

| You say | The Hand |
|---|---|
| "something light for 40 minutes", "like Severance but funnier" | summons the Small Council, then announces its picks |
| "put on Paddington" | opens it in the app that has it |
| "turn the volume to 15", "switch to Hogwarts" | does it |
| "any flight delays from Atlanta today?" | answers from the FAA's live airport status |
| "what's going on around the world?" | answers from today's headlines |
| "tell me about NVIDIA's GR00T", anything else | searches the web, then answers |

**He remembers.** The last few exchanges (15 minutes), so follow-ups work ("and what about San Francisco?", "tell me
more", "play the second one"); and up to 40 short facts about you, learnt from what you say ("follows NVIDIA and AI
news") or told directly ("remember that I don't like horror"). Ask "what do you know about me?", or have him forget one
thing or everything. The facts shape his answers and the Council's picks (and titles you've opened aren't suggested
again). It's all kept only on the TV, in Pantheon's private storage.

He answers in character (loyal, shrewd, dry; "Your Grace") in a deep, expressive voice: ElevenLabs v3, which
performs the chuckles and sighs he writes in. Recorded lines ("At once, Your Grace.") play instantly while he works;
live answers take a few seconds. In Hogwarts, the Headmaster's portrait speaks instead. Without an ElevenLabs key the
TV's own voice reads the same words. Costs: a web answer is about a cent (OpenRouter); a spoken line uses one
ElevenLabs credit per character (lines said before are cached). New recorded lines: `home/tools/voice_lines.py`.

**Setup:** an [OpenRouter](https://openrouter.ai/keys) key (a few dollars of credit lasts months: a pick costs a small
fraction of a cent), a free [TMDB](https://www.themoviedb.org/settings/api) key and, for the Hand's voice, an
[ElevenLabs](https://elevenlabs.io/app/settings/api-keys) key (optional). Then:

```bash
./scripts/agent-keys.sh          # asks for the keys (hidden), checks them, sends them to the TV
```

The keys stay on the TV, in Pantheon's private storage; they're never in the repo. `./scripts/agent-keys.sh --clear`
removes them. Test from a computer: `adb shell am start -n dev.pantheon.adultspace/.MainActivity --es council '{"minutes":45,"like":"something cozy"}'`,
or as if said to the mic: `--es ask "any flight delays from Atlanta today"`; watch `adb logcat -s Pantheon/Agent`.

## Themes

Pick one with the **crown** in the top row (Up from the dock, then Left), or by voice: "switch to Game of
Thrones", "Hogwarts theme". The whole home changes with it. New installs start on Game of Thrones.

### Game of Thrones: Pantheon's own, built by Jose

| | |
|---|---|
| **Home** | the throne room, firelit, following the real weather and daylight outside (below) |
| **Mascot** | Drogon hops between the apps and **breathes fire on the one you pick**: it glows, chars, smoulders and cools back |
| **Greeting** | a royal-court line on a frosted parchment scroll under the clock, for the time of day ("The torches are lit, Your Grace."), a different one each day |
| **Headlines** | *The Ravens*: The Realm (national), Across the Narrow Sea (world), The Iron Bank (business) |
| **Library** | the eight seasons as rustic books, each with its own cover and a four-word caption |

Every picture, sprite, sound and line of this theme was made for Pantheon (see [NOTICE.md](NOTICE.md)).

### Hogwarts: by ikevinadams, extended by Pantheon

The castle by the lake, with Hedwig hopping between apps, the films as a library and the greenhouse, is
[hogwarts-tv-home](https://github.com/ikevinadams/hogwarts-tv-home) by **ikevinadams**. All credit for it goes to
them. Pantheon has modified it with several new features: the voice agent and mic button, the theme picker, the
place and weather under the clock, *The Daily Prophet*'s headlines, the Home takeover and self-repair, the unbloat,
Crave and Disney+ in the dock, and the one-command installer. Its pictures stay upstream and are fetched onto your
machine when you build (`scripts/fetch-fanart.sh`), into `home/themes/hogwarts/`.

### Marvel: coming next

Pantheon's own, like Game of Thrones. Only its colours exist so far.

### Live weather and sound (Game of Thrones)

The throne room follows the real world outside wherever the TV is. The location comes from the TV's internet address, so
it works anywhere (Canada, India and so on) with no setup:

- **Weather in the windows**: clear, cloudy, fog, drizzle, rain, snow, blizzard, and thunderstorms with lightning that
  flashes the room. Clouds drift past.
- **Light from the sun**: the room moves through night, dawn, golden hour and day by the sun's real height. Light shafts
  slant with its direction, and the moon shows its real phase.
- **Torches** burn live in all four sconces. A little fog drifts through the hall at night; by day, dust floats in
  the sunlight.
- **Sound**: crackling torches and a quiet original score, plus rain, wind and thunder to match the weather. Mute it with
  the speaker in the top row. The sound pauses when you open an app.

Weather data: [Open-Meteo](https://open-meteo.com) (no key needed), refreshed every 15 minutes. The sun and moon are
worked out on the TV.

**Your own music.** To play a track of your own each time you come home (once, then the built-in score loops; it
stays on your machine and is never committed), run:

```bash
./scripts/add-music.sh ~/Music/your-track.mp3
```

Then reinstall with `./install.sh`. To go back to the built-in score, run `./scripts/add-music.sh --remove`.

**Testing a weather**: `adb shell am start -n dev.pantheon.adultspace/.MainActivity --es wx thunder --es wx_at 2026-11-14T21:00`
(clear | partly | overcast | fog | drizzle | rain | heavyrain | snow | blizzard | thunder). Use `--es wx live` to return to
the real weather.

A theme is a folder, `home/themes/<id>/` (its scene, pictures, sprites, sounds and tools), plus a `ThemeData` class in
`android/.../theme/` for its colours and pictures. See `home/themes/got/`.

From a computer: `adb shell am start -n dev.pantheon.adultspace/.MainActivity --es theme got` (hogwarts | got | marvel).

## Credits

- **Pantheon**, and its Game of Thrones theme, designed and built by **Jose** ([jose16790](https://github.com/jose16790)).
- **Hogwarts** theme and the home-screen base: [hogwarts-tv-home](https://github.com/ikevinadams/hogwarts-tv-home) by
  **ikevinadams** (MIT), modified by Pantheon. The original project's README and guide are kept in `docs/hogwarts-tv-home/`.
- **The agentic remote's design**: [couch-tv](https://github.com/Seagey/couch-tv) by **Seagey** (MIT).

Full notices, including the weather and news sources and the font, are in [NOTICE.md](NOTICE.md).

## Licence

[MIT](LICENSE) for the code and for the Game of Thrones theme's own artwork and sound. The castle pictures, owl
sprites, film covers and app logos are not included and not licensed here.
