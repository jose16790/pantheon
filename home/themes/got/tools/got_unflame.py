"""Pantheon (Game of Thrones theme): takes the painted flames out of the throne room's pictures, so the live torch
flames (themes/got/img/torch.webp) aren't drawn over a second, frozen flame.
    reads   themes/got/img/src/throne{,_overcast,_day,_golden}.jpg      the pictures as generated
    writes  themes/got/img/throne{,_overcast,_day,_golden}.jpg          the same, with each torch's flame filled in from the stone
            around it (a patch of the same pillar from above it, Poisson-blended); the warm light the torches throw on the walls stays.
The flame in each sconce is the bright, warm blob just above its iron basket (SCONCES in themes/got/scene.js, in the
photo's 1935 x 1080 frame). Needs numpy, Pillow and opencv-python-headless."""
import os
import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, "..", "..", ".."))        # home/: the page (paths below are relative to it)
SCONCES = [(1760, 334, 112), (166, 338, 112), (390, 461, 80), (1535, 466, 80)]   # x, y of the flame's base, height

for name in ("throne", "throne_overcast", "throne_day", "throne_golden"):
    im = cv2.imread(os.path.join(SRC, "themes/got/img/src", name + ".jpg"))
    H, W = im.shape[:2]; k = W / 1935
    hsv = cv2.cvtColor(im, cv2.COLOR_BGR2HSV)
    b, g, r = [im[..., i].astype(int) for i in range(3)]
    mask = np.zeros((H, W), np.uint8); out = im.copy()
    for x, y, h in SCONCES:
        x0, x1 = int((x - .32 * h) * k), int((x + .32 * h) * k)
        y0, y1 = int((y - 1.1 * h) * k), int((y + .02 * h) * k)           # from above the flame's tip to the basket's rim
        box = np.zeros((H, W), bool); box[y0:y1, x0:x1] = True
        # the flame only: much brighter than the lit stone around it (which keeps its glow), strongly warm or white-hot
        local = cv2.GaussianBlur(hsv[..., 2].astype(np.float32), (0, 0), 25)
        flame = box & (hsv[..., 2] > 200) & ((r > b + 70) | (hsv[..., 2] > 240)) & (hsv[..., 2] > local + 35)
        m = flame.astype(np.uint8) * 255
        m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
        m = cv2.dilate(m, np.ones((5, 5), np.uint8), iterations=2)
        mask |= m
        # fill it with real stone: the same patch of pillar from just above the flame, blended in with Poisson cloning
        # (which matches it to the light around the hole, so the torch's glow on the wall stays)
        ys, xs = np.nonzero(m)
        bx0, by0, bx1, by1 = xs.min() - 8, ys.min() - 8, xs.max() + 9, ys.max() + 9
        bh = by1 - by0
        sy = max(0, by0 - bh - 4)
        src = out[sy:sy + bh, bx0:bx1].copy()
        sub = m[by0:by1, bx0:bx1].copy(); sub[:3] = 0; sub[-3:] = 0; sub[:, :3] = 0; sub[:, -3:] = 0
        out = cv2.seamlessClone(src, out, sub, ((bx0 + bx1) // 2, (by0 + by1) // 2), cv2.NORMAL_CLONE)
    cv2.imwrite(os.path.join(SRC, "themes/got/img", name + ".jpg"), out, [cv2.IMWRITE_JPEG_QUALITY, 92])
    print(name, f"{(mask > 0).sum() / 1e3:.1f}k px filled")
