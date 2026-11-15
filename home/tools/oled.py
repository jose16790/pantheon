"""Pantheon: true black for OLED screens. The night pictures' near-blacks (which an OLED panel shows as a faint grey
glow) are taken down to real black, and the shadows just above ease into it smoothly, so nothing bands; everything
brighter is left exactly as it was. Hue is kept: each pixel is scaled as a whole by its brightest channel.
    from oled import oled;  im = oled(im)        (a PIL RGB image; used by the themes' TV-asset tools)"""
import numpy as np
from PIL import Image

LO, HI, BEND = 4, 22, 1.5          # at or below LO (of 255): black; LO..HI: eased in; above HI: unchanged


def oled(im, lo=LO, hi=HI, bend=BEND):
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    L = a.max(-1)
    t = np.clip((L - lo) / (hi - lo), 0, 1)
    L2 = np.where(L >= hi, L, hi * t ** bend)
    k = np.where(L > 0, L2 / np.maximum(L, 1e-6), 0)
    return Image.fromarray((a * k[..., None]).clip(0, 255).round().astype(np.uint8))
