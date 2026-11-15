"""Pantheon (Game of Thrones theme): TV-sized copies of the theme's backdrops, like tv_assets.py makes for the castle.
    themes/got/img/throne.jpg, themes/got/img/shelf.jpg  ->  android/app/src/main/assets/www/themes/got/img/
1936 px wide, with the page's static shading baked in (#hshade for the home, #lvig for the library: the TV hides
those layers to save drawing). Also copies the dragon's sprite sheets. Run by build.sh --regen-art, or by hand:
    python themes/got/tools/got_assets.py        (numpy + Pillow)"""
import os, shutil
import numpy as np
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, "..", "..", ".."))        # home/: the page (paths below are relative to it)
OUT = os.path.normpath(os.path.join(SRC, "..", "android", "app", "src", "main", "assets", "www"))   # the TV app's copy
WIDE = 1936


def ramp(t, stops):
    return np.interp(t, [p for p, _ in stops], [a for _, a in stops])


def radial(X, Y, cx, cy, rx, ry, stops):
    return ramp(np.hypot((X - cx) / rx, (Y - cy) / ry), stops)


def shaded(im, alphas):   # the same as tv_assets.py: black layers over the picture, as the browser stacks them
    a = np.asarray(im).astype(np.float32)
    h, w = a.shape[:2]
    X = ((np.arange(w) + 0.5) / w * 1935 - 7.5) / 1920
    Y = (np.arange(h) + 0.5) / h
    X, Y = np.meshgrid(X, Y)
    keep = np.ones_like(X)
    for f in alphas:
        keep *= 1 - f(X, Y)
    return Image.fromarray((a * keep[..., None]).clip(0, 255).astype(np.uint8))


BAKE = {   # the gradients of #hshade and #lvig in index.html (as in tv_assets.py)
    "themes/got/img/throne.jpg": [lambda X, Y: ramp(Y, [(0, .30), (.16, 0), (.52, 0), (.66, .30), (.80, .62), (1, .80)]),
                           lambda X, Y: radial(X, Y, .55, .45, .80, .70, [(0, 0), (.60, 0), (1, .45), (9, .45)])],
    "themes/got/img/shelf.jpg": [lambda X, Y: radial(X, Y, .51, .50, .80, .75, [(0, 0), (.58, 0), (1, .50), (9, .50)])],
}
NIGHT = ("themes/got/img/throne.jpg", "themes/got/img/shelf.jpg")   # the room at night and the library: true black
sys.path.insert(0, os.path.join(SRC, "tools")); from oled import oled
for v in ("overcast", "day", "golden"):   # the room by day and at golden hour (the weather wizard crossfades them)
    BAKE[f"themes/got/img/throne_{v}.jpg"] = BAKE["themes/got/img/throne.jpg"]
os.makedirs(os.path.join(OUT, "themes", "got", "img"), exist_ok=True)
for rel, alphas in BAKE.items():
    im = Image.open(os.path.join(SRC, rel)).convert("RGB")
    im = im.resize((WIDE, round(WIDE * im.height / im.width)), Image.LANCZOS)
    out = shaded(im, alphas)
    if rel in NIGHT: out = oled(out)                                   # true black on OLED screens (tools/oled.py)
    out.save(os.path.join(OUT, rel), quality=90)
    print(rel, im.size)
os.makedirs(os.path.join(OUT, "themes", "got", "dragon"), exist_ok=True)
for f in ("idle.webp", "hop.webp", "breath.webp", "dragon.js"):
    shutil.copy2(os.path.join(SRC, "themes", "got", "dragon", f), os.path.join(OUT, "themes", "got", "dragon", f))
for f in ("glass.png", "clouds_puffy.webp", "clouds_wispy.webp", "torch.webp", "torch.js"):
    shutil.copy2(os.path.join(SRC, "themes", "got", "img", f), os.path.join(OUT, "themes", "got", "img", f))
os.makedirs(os.path.join(OUT, "themes", "got", "sounds"), exist_ok=True)
for f in os.listdir(os.path.join(SRC, "themes", "got", "sounds")):
    shutil.copy2(os.path.join(SRC, "themes", "got", "sounds", f), os.path.join(OUT, "themes", "got", "sounds", f))
