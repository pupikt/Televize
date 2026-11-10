"""Ikona a banner appky Televize: noční modrá s polární září, skleněná televize."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

def aurora(w, h):
    img = Image.new("RGB", (w, h), (10, 13, 24))
    for (cx, cy, r, col) in [(.2, .1, .75, (56, 189, 248)), (.85, .3, .7, (129, 140, 248)), (.55, 1.05, .6, (244, 114, 182))]:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        R = r * max(w, h)
        d.ellipse([cx * w - R / 2, cy * h - R / 2, cx * w + R / 2, cy * h + R / 2], fill=col + (120,))
        layer = layer.filter(ImageFilter.GaussianBlur(R / 4))
        img.paste(layer, (0, 0), layer)
    return img.convert("RGBA")

def tv(img, box, s):
    x0, y0, x1, y1 = box
    glass = Image.new("RGBA", img.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glass)
    r = int(s * .09)
    g.rounded_rectangle(box, r, fill=(255, 255, 255, 46), outline=(255, 255, 255, 190), width=max(2, int(s * .016)))
    # obrazovka – zář uvnitř
    ins = int(s * .045)
    g.rounded_rectangle([x0 + ins, y0 + ins, x1 - ins, y1 - ins], int(r * .6), fill=(129, 140, 248, 70))
    # odlesk
    g.polygon([(x0 + ins, y0 + ins), (x0 + (x1 - x0) * .55, y0 + ins), (x0 + ins, y0 + (y1 - y0) * .62)], fill=(255, 255, 255, 40))
    # nožička
    cx = (x0 + x1) / 2
    g.rounded_rectangle([cx - s * .16, y1 + s * .05, cx + s * .16, y1 + s * .085], int(s * .02), fill=(255, 255, 255, 170))
    img.alpha_composite(glass)

def moon(img, c, r):
    m = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(m)
    d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=(241, 245, 255, 235))
    d.ellipse([c[0] - r * .25, c[1] - r * 1.25, c[0] + r * 1.55, c[1] + r * .55], fill=(0, 0, 0, 0))
    img.alpha_composite(m)

out = sys.argv[1]
font = sys.argv[2]
# ikona 192
s = 192
ic = aurora(s, s)
tv(ic, (s * .17, s * .24, s * .83, s * .7), s)
moon(ic, (s * .62, s * .44), s * .1)
ic.convert("RGB").save(f"{out}/icon.png")
# banner 320×180
W, H = 320, 180
b = aurora(W, H)
tv(b, (24, 44, 128, 112), 150)
moon(b, (93, 74), 12)
d = ImageDraw.Draw(b)
f = ImageFont.truetype(font, 34)
f.set_variation_by_axes([800])
d.text((148, 64), "Televize", font=f, fill=(244, 246, 255))
b.convert("RGB").save(f"{out}/banner.png")
