"""Pantheon (Game of Thrones theme): the torch flame and its crackle, for the throne room's four sconces.
    reads   dragon/src/torch.mp4          one torch flame on black, with its sound (generated)
    writes  img/got/torch.webp            a seamless loop of the flame, see-through (black made clear)
            img/got/torch.js              window.TORCH_SHEET = {src, n, fps, w, h, frames}
            sounds/got/fire.mp3           the crackle, louder, looped without a seam
The clip also drew a glowing stub under the flame; only the flame above it is kept (the photo's iron baskets are the
base). The loop's end is cross-faded into its start, so it never visibly restarts. Run with numpy + scipy + Pillow + ffmpeg."""
import json, os, subprocess
import numpy as np
from scipy import ndimage
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, ".."))
CLIP = os.path.join(SRC, "dragon", "src", "torch.mp4")
FADE, START = 0, 30                     # no blend (blended fire ghosts): a cut between near-identical flames is invisible
HEAT_AT = [0, .3, .6, .85, 1]
HEAT = [(150, 30, 0), (235, 85, 8), (255, 150, 30), (255, 205, 95), (255, 240, 190)]
H_OUT = 150                             # the flame's height in the sheet

W, H = map(int, subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width,height", "-of", "csv=p=0", CLIP],
                               capture_output=True, text=True, check=True).stdout.strip().split(","))
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", CLIP, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
clip = np.frombuffer(raw, np.uint8).reshape(-1, H, W, 3).astype(np.float32)
# fire moves too fast to cross-fade over long without ghosting, so the loop ends where the flame looks most like it
# did at the start (searched over 1.5-3 s), and only the last few frames blend
small = clip[:, ::8, ::8].max(-1)
cands = range(START + 36, min(len(clip) - FADE, START + 72))
LOOP = min(cands, key=lambda j: np.abs(small[j] - small[START]).mean()) - START
seg = clip[START:START + LOOP + FADE]
loop = seg[:LOOP].copy()
for i in range(FADE):
    a = (i + 1) / (FADE + 1); loop[i] = seg[i] * a + seg[LOOP + i] * (1 - a)

# the stub: the steady, dull block at the bottom of the flame (its rows barely change between frames)
lum = loop.max(-1)                                       # brightness per pixel per frame
rows_on = (lum.max(0) > 40).any(1)
ys = np.nonzero(rows_on)[0]; top, bot = ys.min(), ys.max()
var = lum.std(0).mean(1)                                 # how much each row flickers
bright = lum.mean(0).mean(1)
stub = [y for y in range(bot, top, -1) if bright[y] > 20 and var[y] < 0.35 * var[top:bot].max()]
cut = (min(stub) if stub else bot) - 2                   # just above the stub
cols = np.nonzero((lum[:, top:cut].max(0) > 40).any(0))[0]
x0, x1 = max(0, cols.min() - 6), min(W, cols.max() + 7)
y0 = max(0, top - 6)
k = H_OUT / (cut - y0); w = round((x1 - x0) * k)
fade_base = np.clip((cut - np.arange(y0, cut)) / (0.06 * (cut - y0)), 0, 1)[:, None]   # the base fades out softly

cells = []
for fr in loop:
    rgb = fr[y0:cut, x0:x1]
    a = np.clip((rgb.max(-1) - 30) / 200, 0, 1) ** 0.85 * fade_base
    # colour by heat: the clip's flame is near-white, so each pixel's brightness picks its colour on a fire ramp
    # (deep orange at the thin edges, yellow, and pale yellow only in the hottest core)
    # The clip's core is overexposed (flat white), so the heat also comes from how deep inside the flame a pixel is
    # and how low: a real torch burns yellow-white low in its core, orange at its edges and red-orange at its tips.
    lum = np.clip((rgb.max(-1) - 30) / 225, 0, 1)
    depth = ndimage.distance_transform_edt(lum > 0.15)
    depth = np.clip(depth / max(1.0, 0.16 * rgb.shape[1]), 0, 1)
    low = np.linspace(0.55, 1.0, rgb.shape[0])[:, None]
    heat = np.clip(lum * (0.3 + 0.7 * depth) * low, 0, 1)
    col = np.stack([np.interp(heat, HEAT_AT, [c[i] for c in HEAT]) for i in range(3)], -1)
    cells.append(Image.fromarray(np.dstack([col.clip(0, 255), a * 255]).astype(np.uint8)).resize((w, H_OUT), Image.LANCZOS))
per_row = max(1, 4096 // w)
sheet = Image.new("RGBA", (per_row * w, -(-LOOP // per_row) * H_OUT), (0, 0, 0, 0))
frames = []
for i, c in enumerate(cells):
    x, y = (i % per_row) * w, (i // per_row) * H_OUT
    sheet.paste(c, (x, y)); frames.append([x, y])
os.makedirs(os.path.join(SRC, "img", "got"), exist_ok=True)
sheet.save(os.path.join(SRC, "img/got/torch.webp"), quality=86, method=6)
with open(os.path.join(SRC, "img/got/torch.js"), "w") as f:
    f.write("// made by tools/got_torch.py from dragon/src/torch.mp4\nwindow.TORCH_SHEET = "
            + json.dumps(dict(src="img/got/torch.webp", n=LOOP, fps=24, w=w, h=H_OUT, frames=frames)) + ";\n")
print(f"torch: {LOOP} frames of {w}x{H_OUT}, sheet {sheet.size}, stub cut at row {cut} of {H}")

# the crackle: louder (the clip's is very quiet), then its end cross-faded into its start
os.makedirs(os.path.join(SRC, "sounds", "got"), exist_ok=True)
pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", CLIP, "-vn", "-ac", "2", "-ar", "44100", "-af",
                      "highpass=f=60,acompressor=threshold=-30dB:ratio=3:attack=5:release=80,loudnorm=I=-20:TP=-2:LRA=11",
                      "-f", "f32le", "-"], capture_output=True, check=True).stdout
snd = np.frombuffer(pcm, np.float32).reshape(-1, 2)
sr, xf = 44100, int(0.8 * 44100)
body, tail = snd[:-xf].copy(), snd[-xf:]
ramp = np.linspace(0, 1, xf)[:, None]
body[:xf] = body[:xf] * ramp + tail * (1 - ramp)         # play body on repeat: no seam
subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(sr), "-ac", "2", "-i", "-", "-b:a", "128k",
                os.path.join(SRC, "sounds/got/fire.mp3")], input=body.astype(np.float32).tobytes(), check=True)
print(f"fire.mp3: {len(body) / sr:.2f} s loop")
