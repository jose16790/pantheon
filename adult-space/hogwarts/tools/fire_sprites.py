"""Pantheon (Game of Thrones theme): the dragon's fire breath, from real-looking fire footage.
    reads   dragon/src/fire.mp4      a flame jet streaming left to right on black (generated)
    writes  dragon/fire.webp         a seamless loop of the jet at full length, see-through
            dragon/fire.js           window.FIRE_SHEET = {src, n, fps, w, h, srcY, frames}
The steady part of the clip (after the jet has built up) is looped, its end cross-faded into its start so the loop
has no jump. Black becomes transparent: alpha is the pixel's brightness and the colour is un-premultiplied, so the
fire composites over the scene like a light (the TV can't afford blend modes). Run with numpy + Pillow + ffmpeg."""
import json, os, subprocess
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.normpath(os.path.join(HERE, "..", "dragon"))
W_OUT = 280            # the jet's length in the sheet (the page scales it to the gap between the jaws and the icon)
LOOP, FADE = 36, 10    # frames in the loop (24 fps: 1.5 s), and how many of them cross-fade end into start
START = 50             # where the steady jet begins in the clip (it builds up for ~2 s first)

path = os.path.join(DIR, "src", "fire.mp4")
W, H = map(int, subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width,height",
                                "-of", "csv=p=0", path], capture_output=True, text=True, check=True).stdout.strip().split(","))
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
clip = np.frombuffer(raw, np.uint8).reshape(-1, H, W, 3).astype(np.float32)
seg = clip[START:START + LOOP + FADE]
assert len(seg) == LOOP + FADE, f"clip too short: {len(clip)} frames"
loop = seg[:LOOP].copy()
for i in range(FADE):                                   # the end fades into the start: a loop with no seam
    a = (i + 1) / (FADE + 1)
    loop[i] = seg[i] * a + seg[LOOP + i] * (1 - a)

bright = loop.max(-1).max(0)                            # where fire ever is, to crop the empty rows
rows = np.nonzero(bright.max(1) > 18)[0]
y0, y1 = max(0, rows.min() - 4), min(H, rows.max() + 5)
left = loop[:, :, :12].max(-1).mean(0)[:, 0]            # the jet's source: the brightest rows at the left edge
src_y = float((np.arange(H) * left ** 4).sum() / (left ** 4).sum())
k = W_OUT / W
w, h = W_OUT, round((y1 - y0) * k)

frames, cells = [], []
for fr in loop:
    rgb = fr[y0:y1]
    a = np.clip((rgb.max(-1) - 6) / 235, 0, 1) ** 0.8    # brightness -> opacity (a little fuller than linear)
    col = np.where(a[..., None] > 0.004, rgb / np.maximum(a[..., None], 0.004), 0)
    rgba = np.dstack([col.clip(0, 255), a * 255]).astype(np.uint8)
    cells.append(Image.fromarray(rgba).resize((w, h), Image.LANCZOS))
cols = max(1, 4096 // w)
sheet = Image.new("RGBA", (cols * w, -(-LOOP // cols) * h), (0, 0, 0, 0))
for i, c in enumerate(cells):
    x, y = (i % cols) * w, (i // cols) * h
    sheet.paste(c, (x, y)); frames.append([x, y])
sheet.save(os.path.join(DIR, "fire.webp"), quality=86, method=6)
meta = dict(src="dragon/fire.webp", n=LOOP, fps=24, w=w, h=h, srcY=round((src_y - y0) * k, 1), frames=frames)
with open(os.path.join(DIR, "fire.js"), "w") as f:
    f.write("// made by tools/fire_sprites.py from dragon/src/fire.mp4\nwindow.FIRE_SHEET = " + json.dumps(meta) + ";\n")
print(f"fire: {LOOP} frames of {w}x{h}, sheet {sheet.size} ({sheet.size[0] * sheet.size[1] * 4 / 1e6:.1f} MB decoded), source at y={meta['srcY']}")
