"""Dlaždice oblíbených aplikací z originálních log (Tom je poslal 10. 11. 2026).
Použití: python3 tools/tiles.py  → app/src/main/res/drawable-nodpi/tile_*.png (640×360)"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "logos")
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
W, H = 640, 360


def gradient(c1, c2, angle_diag=True):
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    t = (x / W * 0.55 + y / H * 0.45) if angle_diag else (y / H)
    t = t[..., None]
    a = np.array(c1, np.float32); b = np.array(c2, np.float32)
    img = a + (b - a) * t
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def glow(img, cx, cy, r, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r * 0.7, cx + r, cy + r * 0.7], fill=tuple(color) + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(r * 0.45))
    img.alpha_composite(layer)


def place(img, logo, width_frac, dy=0, shadow=True):
    logo = logo.crop(logo.getbbox())
    w = int(W * width_frac)
    h = int(logo.height * w / logo.width)
    logo = logo.resize((w, h), Image.LANCZOS)
    x, y = (W - w) // 2, (H - h) // 2 + dy
    if shadow:
        sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
        a = logo.split()[3].point(lambda v: int(v * 0.55))
        sh.paste((0, 0, 0, 255), (x, y + 6), a)
        img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))
    img.alpha_composite(logo, (x, y))


def finish(img, name):
    # jemná vinětace, ať sklo kolem vynikne
    v = Image.new("L", (W, H), 0)
    ImageDraw.Draw(v).rectangle([0, 0, W, H], fill=0)
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    d = ((x / W - .5) ** 2 + (y / H - .5) ** 2) * 2.2
    dark = Image.fromarray(np.clip(d * 90, 0, 90).astype(np.uint8), "L")
    black = Image.new("RGBA", (W, H), (0, 0, 0, 255)); black.putalpha(dark)
    img.alpha_composite(black)
    img.convert("RGB").save(os.path.join(OUT, f"tile_{name}.png"), optimize=True)


# ---------- Netflix ----------
t = gradient((30, 6, 10), (4, 4, 6))
glow(t, W * .5, H * 1.05, 330, (229, 9, 20), 150)
glow(t, W * .15, -20, 220, (229, 9, 20), 60)
place(t, Image.open(os.path.join(SRC, "netflix.png")).convert("RGBA"), 0.66)
finish(t, "netflix")

# ---------- YouTube: červená ikona + bílý nápis ----------
src = np.asarray(Image.open(os.path.join(SRC, "youtube.webp")).convert("RGB")).astype(np.float32)
r, g, b = src[..., 0], src[..., 1], src[..., 2]
red_a = np.clip((r - g) / 255.0, 0, 1)                    # ikona
lum = (r + g + b) / 3
txt_a = np.clip((242 - lum) / (242 - 33), 0, 1) * (red_a < 0.02)   # nápis (tmavý → bílý)
h, w = red_a.shape
logo = np.zeros((h, w, 4), np.float32)
logo[..., 0] = 255 * (txt_a > 0) + 255 * (red_a > 0) * (txt_a == 0)
logo[..., 1] = 255 * (txt_a > 0) + 0
logo[..., 2] = 255 * (txt_a > 0) + 51 * (red_a > 0) * (txt_a == 0)
logo[..., 3] = np.maximum(red_a, txt_a) * 255
yt = Image.fromarray(logo.astype(np.uint8), "RGBA")
under = Image.new("RGBA", yt.size, (0, 0, 0, 0))
ImageDraw.Draw(under).polygon([(518, 462), (518, 584), (624, 523)], fill=(255, 255, 255, 255))  # bílý trojúhelník pod červenou
under.alpha_composite(yt)
t = gradient((34, 34, 40), (10, 10, 13))
glow(t, W * .28, H * .5, 260, (255, 0, 51), 85)
glow(t, W * .9, H * 1.1, 260, (255, 60, 90), 40)
place(t, under, 0.70)
finish(t, "youtube")

# ---------- Disney+: bílé logo, oblouk do tyrkysova ----------
src = np.asarray(Image.open(os.path.join(SRC, "disney.webp")).convert("RGB")).astype(np.float32)
r, g, b = src[..., 0], src[..., 1], src[..., 2]
h, w = r.shape
yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
arc = (yy < 640) | ((yy < 752) & (xx > 1500) & (xx < 1700))   # oblouk nad nápisem
chroma = np.max(src, axis=2) - np.min(src, axis=2)             # šachovnice je šedá → chroma 0
a_word = np.where(b - r > 12, np.clip((205 - r) / 190.0, 0, 1), 0)
a_arc = np.clip(chroma / 150.0, 0, 1)
a = np.where(arc, a_arc, a_word)
a = np.where(a < 0.08, 0, a)
col = np.zeros((h, w, 4), np.float32)
tx = np.clip((xx - 500) / 1150, 0, 1)
col[..., 0] = np.where(arc, 190 - 150 * tx, 255)
col[..., 1] = np.where(arc, 250 - 60 * tx, 255)
col[..., 2] = 255
col[..., 3] = a * 255
dis = Image.fromarray(col.astype(np.uint8), "RGBA")
t = gradient((10, 18, 74), (6, 62, 92))
glow(t, W * .78, H * .15, 300, (40, 200, 220), 95)
glow(t, W * .1, H * 1.0, 280, (60, 70, 220), 110)
place(t, dis, 0.60, dy=-4)
finish(t, "disney")

# ---------- Oneplay: výřez z originálního obrázku ----------
op = Image.open(os.path.join(SRC, "oneplay.png")).convert("RGBA")
cw, ch = 992, 558
x0, y0 = (1200 - cw) // 2, 54
t = op.crop((x0, y0, x0 + cw, y0 + ch)).resize((W, H), Image.LANCZOS)
finish(t, "oneplay")
print("ok")
