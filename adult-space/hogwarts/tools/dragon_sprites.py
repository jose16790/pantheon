"""Pantheon (Game of Thrones theme): turns the generated dragon clips into sprite sheets the page draws on a canvas,
like owl_sprites.py does for the owl.
    reads   dragon/src/{idle,hopwide,breath}.mp4   generated on a flat green screen (see dragon/src/README.md);
            hopwide is a 16:9 take with room above for the wings (the square take clipped them)
    writes  dragon/{idle,hop,breath}.webp        packed frames, already at the size the TV draws them
            dragon/dragon.js                     window.DRAGON_SHEETS = {...}
Keying: green is removed by how much greener than red/blue a pixel is; a pixel must also be red-ish or dark (the
dragon is black and crimson) to stay, which drops the pale dust the video model likes to add, and only blobs joined
to the dragon itself are kept. Clips are framed differently, so each is scaled from its own first frame (the same
perched pose in all of them) until the dragon is DRAGON_H px tall.
Measured per clip: where the feet are (the perch point), and for the hop when it leaves and touches the ground,
and for the breath where the mouth is in every frame and when the jaws are thrust forward (when fire comes out).
Run with python (numpy + Pillow) and ffmpeg on PATH:  python tools/dragon_sprites.py"""
import json, os, subprocess
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.normpath(os.path.join(HERE, "..", "dragon"))
DRAGON_H = 140          # the perched dragon's height on screen, px (its sheets are drawn 1:1)
EVERY = 2               # keep every 2nd frame: 12 fps from the 24 fps clips (memory on the TV)
CLIPS = {"idle": "idle", "hop": "hopwide", "breath": "breath"}


def frames(clip):
    path = os.path.join(DIR, "src", clip + ".mp4")
    W, H = map(int, subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width,height",
                                    "-of", "csv=p=0", path], capture_output=True, text=True, check=True).stdout.strip().split(","))
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.uint8).reshape(-1, H, W, 3)[::EVERY]


def key(rgb):
    """RGBA with the green screen (and pale dust) made transparent, green spill removed from the edges."""
    f = rgb.astype(np.float32)
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    green = g - np.maximum(r, b)
    a = np.clip(1 - (green - 25) / 70, 0, 1)                      # not green
    a *= np.maximum(np.clip((r - g - 18) / 30, 0, 1),               # and red-ish ...
                    np.clip((125 - np.maximum(np.maximum(r, g), b)) / 45, 0, 1))   # ... or dark
    a[a < 0.06] = 0
    lab, n = ndimage.label(a > 0.5)                                 # keep the dragon, drop loose specks and dust
    if n > 1:
        sizes = ndimage.sum(np.ones_like(a), lab, range(1, n + 1))
        keep = np.isin(lab, 1 + np.nonzero(sizes >= 0.04 * sizes.max())[0])
        near = ndimage.binary_dilation(keep, iterations=3)              # soft edges next to what's kept stay
        a[~near] = 0
    g = np.minimum(g, np.maximum(r, b) + 6)                         # despill
    out = np.dstack([r, g, b, a * 255]).clip(0, 255).astype(np.uint8)
    return out


def jaw_gap(al, tip_x, tip_y, tall):
    """The open mouth. Its inside is solid (palate, tongue), so it's found from the head's profile: going down from
    the snout tip, the front edge (the frontmost opaque pixel of each row) runs back along the upper jaw, cuts in
    at the mouth (the notch), then juts out again at the lower jaw. Fire starts in the notch, level with it and
    midway out between the two jaw tips. None if there's no notch (jaws shut)."""
    y0, y1 = int(tip_y), int(tip_y + 0.24 * tall)
    x_min = int(tip_x - 0.2 * tall)
    rows = al[y0:y1, x_min:tip_x + 1] > 128
    front = np.array([(np.nonzero(r)[0].max() if r.any() else -1) for r in rows], float)
    if (front < 0).all():
        return None
    front[front < 0] = np.nan
    lo = int(np.nanargmax(np.nan_to_num(front[len(front) // 3:], nan=-1)) + len(front) // 3)   # the lower jaw's tip
    notch = int(np.nanargmin(front[:lo + 1]))                                                    # the deepest cut above it
    depth = min(np.nanmax(front[:notch + 1]), front[lo]) - front[notch]
    if notch == 0 or depth < 0.03 * tall:
        return None
    lower_tip = front[lo]
    return x_min + (front[notch] + lower_tip) / 2, y0 + notch


def bottom_front(alpha, x_from):
    """The lowest opaque row among columns right of x_from (the legs; the tail curls to the left)."""
    ys, xs = np.nonzero(alpha[:, x_from:] > 128)
    return ys.max() if len(ys) else None


clips = {n: np.stack([key(fr) for fr in frames(c)]) for n, c in CLIPS.items()}


def reference(cl):
    """Scale, perch point and the legs' side from a clip's first frame (crouched, wings folded)."""
    ys, xs = np.nonzero(cl[0, ..., 3] > 128)
    tall = ys.max() - ys.min()
    x_legs = int(xs.min() + 0.45 * (xs.max() - xs.min()))       # right of here: legs and head, not the tail
    ground = bottom_front(cl[0, ..., 3], x_legs)
    feet_x = xs[(ys > ground - 0.03 * tall) & (xs > x_legs)]
    return DRAGON_H / tall, (feet_x.mean(), ground), x_legs, ground, tall


def pack(sizes, width):
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    pos, x, y, row = [None] * len(sizes), 0, 0, 0
    for i in order:
        w, h = sizes[i]
        if x + w > width: x, y, row = 0, y + row, 0
        pos[i] = (x, y); x += w; row = max(row, h)
    return pos, y + row


meta = {}
fps = 24 / EVERY
for name, cl in clips.items():
    k, foot, x_legs, ground, tall = reference(cl)
    print(f"{name}: scale {k:.3f}, a {tall} px tall dragon -> {DRAGON_H} px, feet at {foot[0]:.0f},{foot[1]:.0f}")
    used = (cl[..., 3] > 8).any(0)                                  # everywhere any frame reaches
    uy, ux = np.nonzero(used)
    x0, y0, x1, y1 = ux.min(), uy.min(), ux.max() + 1, uy.max() + 1
    cw, ch = round((x1 - x0) * k), round((y1 - y0) * k)
    pieces, rects, mouth, lows, jaws_open = [], [], [], [], []
    for fr in cl:
        al = fr[..., 3]
        ys_, xs_ = np.nonzero(al > 128)
        if len(xs_):
            mx = xs_.max()                                           # the snout tip: the dragon faces right
            my = ys_[xs_ >= mx - 6].mean()
            gap = jaw_gap(al, mx, my, tall) if name == "breath" else None
            jaws_open.append(gap is not None)
            if gap: mx, my = gap                                     # the mouth, between the jaws' tips
            mouth.append([round((mx - x0) * k, 1), round((my - y0) * k, 1)])
        else:
            mouth.append(mouth[-1] if mouth else [0, 0]); jaws_open.append(False)
        lows.append(bottom_front(al, x_legs))
        cell = Image.fromarray(fr[y0:y1, x0:x1]).resize((cw, ch), Image.LANCZOS)
        box = cell.getchannel("A").point(lambda v: 255 if v > 2 else 0).getbbox() or (0, 0, 1, 1)
        box = (max(0, box[0] - 1), max(0, box[1] - 1), min(cw, box[2] + 1), min(ch, box[3] + 1))
        pieces.append(cell.crop(box)); rects.append(box)
    sizes = [p.size for p in pieces]
    width = min((wd for wd in range(512, 4097, 64) if pack(sizes, wd)[1] <= 4096), key=lambda wd: wd * pack(sizes, wd)[1])
    pos, height = pack(sizes, width)
    sheet = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    rows = []
    for p, (px, py), box in zip(pieces, pos, rects):
        sheet.paste(p, (px, py))
        rows.append([px, py, p.width, p.height, box[0], box[1]])
    sheet.save(os.path.join(DIR, name + ".webp"), quality=84, method=6)
    m = dict(src=f"dragon/{name}.webp", n=len(rows), fps=fps, cw=cw, ch=ch, h=DRAGON_H,
             footX=round((foot[0] - x0) * k, 1), footY=round((foot[1] - y0) * k, 1), frames=rows)
    if name == "breath":                                             # fire only while the jaws are thrust forward AND open
        mx0 = mouth[0][0]
        reach = np.array([mo[0] - mx0 for mo in mouth])
        ok = (reach > 0.45 * reach.max()) & np.array(jaws_open)
        runs = np.split(np.nonzero(ok)[0], np.nonzero(np.diff(np.nonzero(ok)[0]) > 1)[0] + 1)
        on = max(runs, key=len)                                      # the longest stretch of open, forward jaws
        m["mouth"] = mouth
        m["fire"] = [round(on.min() / fps, 2), round((on.max() + 1) / fps, 2)]
        print(f"breath: jaws forward {m['fire'][0]}-{m['fire'][1]} s")
    if name == "hop":                                                # airborne: the legs well above the perch
        lift = np.array([(ground - (lo if lo is not None else ground)) for lo in lows])
        air = np.nonzero(lift > 0.06 * tall)[0]
        m["up"], m["down"], m["end"] = round(air.min() / fps, 2), round((air.max() + 1) / fps, 2), round(len(rows) / fps, 2)
        top_clipped = int((cl[:, 0, :, 3] > 128).any(1).sum())
        print(f"hop: up {m['up']} s, down {m['down']} s, end {m['end']} s; {top_clipped} frames touch the top edge")
    meta[name] = m
    print(f"{name}: {len(rows)} frames, sheet {sheet.size}, {width * height * 4 / 1e6:.1f} MB decoded")

with open(os.path.join(DIR, "dragon.js"), "w") as f:
    f.write("// made by tools/dragon_sprites.py from dragon/src/*.mp4\nwindow.DRAGON_SHEETS = " + json.dumps(meta) + ";\n")
