"""Předrozmazané vrstvy polární záře (v telce se jen pomalu posouvají – skoro žádný výkon).
Použití: python3 tools/aurora.py → app/src/main/res/drawable-nodpi/aurora_*.png"""
import os
import numpy as np
from PIL import Image, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable-nodpi")
W, H = 640, 360
rng = np.random.default_rng(7)


def ribbon(seed, c_low, c_high, y0, amp, thick, rays=True):
    r = np.random.default_rng(seed)
    x = np.linspace(0, 1, W, dtype=np.float32)
    # středová křivka pásu
    curve = y0 + amp * (np.sin(x * 2.2 * np.pi + r.uniform(0, 6)) * .6 + np.sin(x * 4.7 * np.pi + r.uniform(0, 6)) * .25 + np.sin(x * 9.1 * np.pi + r.uniform(0, 6)) * .1)
    yy = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    d = yy - curve[None, :]
    # nahoře dlouhý závoj, dole ostřejší hrana – jako skutečná záře
    body = np.where(d < 0, np.exp(-(d / (thick * 2.6)) ** 2), np.exp(-(d / (thick * .55)) ** 2))
    if rays:
        ray = sum(np.sin(x * f * np.pi * 2 + r.uniform(0, 6)) for f in (7, 13, 23, 41)) / 4
        ray = (.72 + .28 * ray)[None, :]
        body = body * ray
    inten = (.55 + .45 * np.sin(x * 1.3 * np.pi + r.uniform(0, 6)) ** 2)[None, :]
    a = np.clip(body * inten, 0, 1)
    t = np.clip(-d / (thick * 3) + .15, 0, 1)[..., None]      # barva: dole c_low, nahoře c_high
    col = np.array(c_low, np.float32) * (1 - t) + np.array(c_high, np.float32) * t
    img = np.dstack([col, a[..., None] * 255]).astype(np.uint8)
    im = Image.fromarray(img, "RGBA").filter(ImageFilter.GaussianBlur(9))
    return im


ribbon(1, (45, 212, 191), (56, 189, 248), .50, .10, .13).save(os.path.join(OUT, "aurora_a.png"), optimize=True)
ribbon(5, (129, 140, 248), (192, 132, 252), .42, .13, .16).save(os.path.join(OUT, "aurora_b.png"), optimize=True)
print("ok")
