"""Pantheon: true black for OLED screens. The night pictures' near-blacks (which an OLED panel shows as a faint grey
glow) are taken down to real black, and the shadows just above ease into it smoothly, so nothing bands; everything
brighter is left exactly as it was. Hue is kept: each pixel is scaled as a whole by its brightest channel.
    from oled import oled;  im = oled(im)        (a PIL RGB image; used by the themes' TV-asset tools for every backdrop)"""
import numpy as np
from PIL import Image

LO, EASE, BEND = 4, 18, 1.5        # at or below the black point (at least LO of 255): black; the next EASE levels eased in
SHARE = 3                           # the black point is also at least the picture's darkest 3% (a bright daytime room's
                                    # deepest shadows go truly black too, not only a night scene's)


def oled(im, share=SHARE, bend=BEND):
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    L = a.max(-1)
    lo = max(LO, float(np.percentile(L, share))); hi = lo + EASE
    t = np.clip((L - lo) / (hi - lo), 0, 1)
    L2 = np.where(L >= hi, L, hi * t ** bend)
    k = np.where(L > 0, L2 / np.maximum(L, 1e-6), 0)
    return Image.fromarray((a * k[..., None]).clip(0, 255).round().astype(np.uint8))
