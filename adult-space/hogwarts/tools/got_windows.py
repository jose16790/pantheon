"""Pantheon (Game of Thrones theme): where the glass is in the throne room's windows, so the page can show a live
sky (moving clouds, the moon, rain, snow, lightning) through it while the stone tracery and the throne stay in front.
    reads   img/got/throne.jpg
    writes  img/got/glass.png      the windows' bounding box, white where it is glass (soft-edged), clear elsewhere
            prints the box, for GLASS in themes/got.js
How: the bright, blue-ish pixels high in the picture are glass; they are grown into solid window openings (filling
the dark clouds and the lead lines), then inside the openings anything much darker than its surroundings (the lead
cames and the tracery) or warm (the throne's spikes in front of the middle window) is taken back out."""
import os, sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, ".."))
im = Image.open(os.path.join(SRC, "img/got/throne.jpg")).convert("RGB")
W, H = im.size
a = np.asarray(im).astype(np.float32); r, g, b = a[..., 0], a[..., 1], a[..., 2]
lum = 0.3 * r + 0.59 * g + 0.11 * b

seed = ((b > r + 10) & (lum > 50)) | ((lum > 190) & (b > r - 15))   # sky, clouds, the moon (not the orange torches)
seed[int(H * 0.64):] = False
lab, n = ndimage.label(ndimage.binary_closing(seed, iterations=6))
sizes = ndimage.sum(seed, lab, range(1, n + 1))
openings = np.isin(lab, 1 + np.nonzero(sizes > 0.03 * sizes.max())[0])   # the windows (and their pieces), not stray glints
openings = ndimage.binary_fill_holes(ndimage.binary_closing(openings, iterations=12))
# each window is as wide as its upper part (above the throne): fog lit beside the throne isn't glass
lab, n = ndimage.label(openings)
for i in range(1, n + 1):
    ys, xs = np.nonzero(lab == i)
    top = xs[ys < np.percentile(ys, 55)]
    lo, hi = np.percentile(top, 0.5), np.percentile(top, 99.5)
    comp = lab == i
    comp[:, :int(lo) - 2] = False; comp[:, int(hi) + 3:] = False
    openings[(lab == i) & ~comp] = False

local = ndimage.uniform_filter(lum, 15)                          # the stone between panes is much darker than the glass around it
stone = lum < 0.62 * local
warm = (r > b + 6) & (lum < 120)                                 # the throne's iron, lit by the torches
glass = openings & ~stone & ~warm & (lum > 28)
glass = ndimage.binary_opening(glass, iterations=1)

alpha = Image.fromarray((glass * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
ys, xs = np.nonzero(glass)
x0, y0, x1, y1 = max(0, xs.min() - 4), max(0, ys.min() - 4), min(W, xs.max() + 5), min(H, ys.max() + 5)
out = Image.new("LA", (x1 - x0, y1 - y0), (255, 0)); out.putalpha(alpha.crop((x0, y0, x1, y1)))
out.convert("RGBA").save(os.path.join(SRC, "img/got/glass.png"), optimize=True)
k = 1935 / W                                                     # the page's photo frame (photoBox 1935 x 1080)
print(f"GLASS = {{ x: {x0 * k:.1f}, y: {y0 * k:.1f}, w: {(x1 - x0) * k:.1f}, h: {(y1 - y0) * k:.1f} }}   "
      f"(source {x0},{y0} {x1 - x0}x{y1 - y0}, {glass.mean() * 100:.1f}% of the picture)")
if len(sys.argv) > 1:
    prev = im.crop((x0, y0, x1, y1)).convert("RGBA")
    tint = Image.new("RGBA", prev.size, (0, 255, 0, 0)); tint.putalpha(alpha.crop((x0, y0, x1, y1)).point(lambda v: v * 0.6))
    prev.alpha_composite(tint); prev.convert("RGB").save(sys.argv[1])
