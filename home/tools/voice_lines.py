"""Pantheon: the themes' recorded voice lines, the instant ones the agent plays while it works ("At once, Your Grace.").
    writes  home/themes/<theme>/voice/<kind>_<n>.mp3 and voice.json  {"voice": id, "lines": {kind: [{text, file}]}}
Made once with ElevenLabs v3, which performs the cues in square brackets ([chuckles], [sighs]). Needs your key:
    ELEVENLABS_API_KEY=... python3 home/tools/voice_lines.py [got|hogwarts]
The voices come from ElevenLabs' public library; scripts/agent-keys.sh adds them to your account."""
import json, os, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
HOME = os.path.normpath(os.path.join(HERE, ".."))
KEY = os.environ.get("ELEVENLABS_API_KEY", "")

THEMES = {
    "got": {   # the Hand of the King: Peter Baker, deep, clear, mature
        "voice": "Ix8C14HEHgIQkJswik2o",
        "lines": {
            "ack": ["[clears throat] At once, Your Grace.", "As you command.", "It shall be done, Your Grace.",
                    "[chuckles] Consider it done.", "Right away, Your Grace."],
            "think": ["A moment, Your Grace. [sighs] I shall consult the maesters.", "Let me send a raven, Your Grace.",
                      "[clears throat] One moment. The Citadel keeps records of such things.",
                      "Allow me a moment, Your Grace. The ravens are flying."],
            "council": ["I shall summon the Small Council, Your Grace.",
                        "[chuckles] The Council convenes. Let us see what the realm offers tonight.",
                        "Very good. The Council will have its counsel shortly."],
            "sorry": ["[sighs] Forgive me, Your Grace. The ravens did not return.",
                      "I could not find that, Your Grace. [chuckles] Even the maesters are stumped.",
                      "[sighs] The ravens are lost in the storm, Your Grace. Ask me again."],
        },
    },
    "hogwarts": {   # the Headmaster's portrait: a gruff old British wizard
        "voice": "htZQqY7WtacRNV7s62Iy",
        "lines": {
            "ack": ["Certainly, my dear.", "[chuckles] Consider it done.", "Right away.", "As you wish, my dear."],
            "think": ["Let me consult the library, my dear.", "[chuckles] One moment. The owls are on their way.",
                      "Hmm. [clears throat] Let me think."],
            "council": ["Let us ask the Sorting Hat, shall we?", "[chuckles] The Hat is pondering, my dear."],
            "sorry": ["[sighs] Oh dear. The owls have not returned.", "I'm afraid I couldn't find that, my dear."],
        },
    },
}


def speak(voice, text, out):
    req = urllib.request.Request(f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=mp3_44100_96",
                                 data=json.dumps({"text": text, "model_id": "eleven_v3", "voice_settings": {"stability": 0.5}}).encode(),
                                 headers={"xi-api-key": KEY, "Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r, open(out, "wb") as f:
        f.write(r.read())


if not KEY:
    sys.exit("Set ELEVENLABS_API_KEY.")
for theme in (sys.argv[1:] or THEMES):
    t = THEMES[theme]
    d = os.path.join(HOME, "themes", theme, "voice"); os.makedirs(d, exist_ok=True)
    index = {"voice": t["voice"], "model": "eleven_v3", "lines": {}}
    for kind, lines in t["lines"].items():
        index["lines"][kind] = []
        for n, text in enumerate(lines, 1):
            f = f"{kind}_{n}.mp3"
            speak(t["voice"], text, os.path.join(d, f))
            index["lines"][kind].append({"text": text, "file": f})
            print(theme, f, text)
    with open(os.path.join(d, "voice.json"), "w") as f:
        json.dump(index, f, indent=1, ensure_ascii=False)
