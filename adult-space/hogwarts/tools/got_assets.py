"""Pantheon (Game of Thrones theme): TV-sized copies of the theme's backdrops, like tv_assets.py makes for the castle.
    img/got/throne.jpg, img/got/shelf.jpg  ->  hogwarts-tv/app/src/main/assets/www/img/got/
1936 px wide, with the page's static shading baked in (#hshade for the home, #lvig for the library: the TV hides
those layers to save drawing). Also copies the dragon's sprite sheets. Run by build.sh --regen-art, or by hand:
    python tools/got_assets.py        (numpy + Pillow)"""
import os, shutil
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, ".."))
OUT = os.path.normpath(os.path.join(SRC, "..", "hogwarts-tv", "app", "src", "main", "assets", "www"))
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
    "img/got/throne.jpg": [lambda X, Y: ramp(Y, [(0, .30), (.16, 0), (.52, 0), (.66, .30), (.80, .62), (1, .80)]),
                           lambda X, Y: radial(X, Y, .55, .45, .80, .70, [(0, 0), (.60, 0), (1, .45), (9, .45)])],
    "img/got/shelf.jpg": [lambda X, Y: radial(X, Y, .51, .50, .80, .75, [(0, 0), (.58, 0), (1, .50), (9, .50)])],
}
os.makedirs(os.path.join(OUT, "img", "got"), exist_ok=True)
for rel, alphas in BAKE.items():
    im = Image.open(os.path.join(SRC, rel)).convert("RGB")
    im = im.resize((WIDE, round(WIDE * im.height / im.width)), Image.LANCZOS)
    shaded(im, alphas).save(os.path.join(OUT, rel), quality=88)
    print(rel, im.size)
os.makedirs(os.path.join(OUT, "dragon"), exist_ok=True)
for f in ("idle.webp", "hop.webp", "breath.webp", "dragon.js"):
    shutil.copy2(os.path.join(SRC, "dragon", f), os.path.join(OUT, "dragon", f))
