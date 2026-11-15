# Pantheon

A custom, ad-free Android TV home screen with a voice agent behind the remote's microphone.

**Adult Space** is the first "space". It's the animated castle home from
[hogwarts-tv-home](https://github.com/ikevinadams/hogwarts-tv-home), with a hopping owl, a film library and a
greenhouse. Pantheon adds three things to it:

- **Voice agent:** press the mic, speak, and the text is sent to your own agent (a Raspberry Pi on your home network).
  The design follows the agentic remote in [couch-tv](https://github.com/Seagey/couch-tv).
- **Themes:** colours and backdrops come from a `ThemeData` interface, so the look can be swapped. Hogwarts is the
  full theme. Game of Thrones and Marvel are palette-only stubs for now.
- **Clean open-source split:** the code is MIT, and the fan art is fetched locally and never committed.

> Unofficial fan project. Not affiliated with Warner Bros., HBO, Marvel/Disney, Google or the app makers whose logos
> the dock shows. See [NOTICE.md](NOTICE.md).

## Quick start (for friends)

You need a Mac (with [Homebrew](https://brew.sh)) or Linux, and your TV on the same Wi-Fi.

```bash
git clone https://github.com/jose16790/pantheon.git && cd pantheon && ./install.sh
```

The installer sets up the tools, builds Pantheon, shows you how to turn on wireless debugging on the TV, pairs with
it (you type the 6-digit code from the TV), installs Pantheon, and then asks, one at a time, whether to make it your
home screen, give it the mic button, clean out the ads, and which theme to start with. Run it in a normal terminal
window, since it asks questions. To update later: `git pull && ./install.sh`.

## Layout

```
adult-space/
  hogwarts/                 the home-screen page (HTML/JS): layout, D-pad, the owl, the voice banner
    index.html              sections, the remote's keys (keydown handler), owl animation
    themes/theme.js         applies a theme (CSS variables + picture slots) and shows the voice banner
    tools/                  picture pipeline (tv_assets.py makes the TV-sized copies)
  hogwarts-tv/              the Android TV app (a full-screen WebView + a JS bridge called `TV`)
    app/src/main/java/dev/pantheon/adultspace/
      MainActivity.java     the WebView, the bridge, remote keys
      voice/VoiceAgent.java mic → speech-to-text (system voice screen, else in-app recognizer)
      voice/AgentClient.java  POST to the agent, response → Logcat
      theme/ThemeData.java  the theme interface; HogwartsTheme, GameOfThronesTheme, MarvelTheme
      theme/ThemeManager.java the active theme (saved), handed to the page as JSON
  build.sh                  builds the APK
scripts/fetch-fanart.sh     downloads the castle pictures from upstream for a personal build
```

## Build by hand

(`./install.sh` does all of this.) Requirements: JDK 17 and the Android SDK (platform 35). On macOS with Homebrew:

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
```

Then:

```bash
adult-space/build.sh            # fetches fan art on first run, builds dist/Pantheon-adult-space.apk
```

## Install on a Google TV (Wi-Fi ADB)

On the TV: *Settings → System → About →* click *Android TV OS build* 7 times. Then *Settings → System → Developer
options →* turn on *USB debugging*, then *Wireless debugging*, and note the IP and port it shows.

```bash
adb connect <TV-IP>:5555          # Android 14 Google TV: use "adb pair" first with the pairing code
adb install -r dist/Pantheon-adult-space.apk
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

Pantheon's top row (Up from the dock): **Wi-Fi**, **TV settings**, **Ask** (voice), **Customise**.

`unbloat.sh` switches off ambient mode (the idle screensaver with ads), ad services, feedback reporting, the
Google TV / Play Movies shop, Play Games, Gallery and TalkBack. It refuses anything the TV needs: the homes, the
setup app, Play Services, the Play Store, the remote, Bluetooth, voice, the keyboard, HDMI and Wi-Fi.

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

## Themes

Pick one with the **crown** in the top row (Up from the dock, then Left), or by voice: "switch to Game of
Thrones", "Hogwarts theme". The whole home changes with it.

| Theme | Home | Mascot | Library |
|---|---|---|---|
| **Hogwarts** | the castle by the lake | Hedwig the owl hops between apps | the eight films |
| **Game of Thrones** | a firelit throne room, torches burning live | a dragon hops between apps and **breathes fire on the one you pick**: it glows, chars, smoulders and cools back | the eight seasons, each with its own cover and a one-line caption |
| **Marvel** | colours only for now | | |

A theme is a `ThemeData` class (`theme/`) for its colours and pictures, plus, optionally, a scene in
`themes/<id>.js` for its mascot, live effects and books (see `themes/got.js`).

From a computer: `adb shell am start -n dev.pantheon.adultspace/.MainActivity --es theme got` (hogwarts | got | marvel).

## Credits

Built on [hogwarts-tv-home](https://github.com/ikevinadams/hogwarts-tv-home) by **ikevinadams** and
[couch-tv](https://github.com/Seagey/couch-tv) by **Seagey**, both MIT. Full notices are in [NOTICE.md](NOTICE.md).
The original castle project's README and guide are kept in `adult-space/`.

## Licence

[MIT](LICENSE) for the code. The castle pictures, owl sprites, film covers and app logos are not included and not
licensed here.
