#!/usr/bin/env python3
"""生成液态玻璃底图与启动图标（离线预渲染，AppWidget 无法做实时模糊）。"""
import os, random, math
from PIL import Image, ImageDraw, ImageFilter

BASE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(BASE, "res")
os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
os.makedirs(os.path.join(RES, "mipmap-xxxhdpi"), exist_ok=True)

W, H = 720, 400
RADIUS = 56

def glass():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    base = Image.new("RGBA", (W, H), (22, 24, 32, 208))
    img = Image.alpha_composite(img, base)

    blobs = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(blobs)
    d.ellipse((-160, -140, 300, 260), fill=(96, 132, 255, 120))
    d.ellipse((380, -180, 860, 220), fill=(0, 214, 224, 92))
    d.ellipse((140, 210, 760, 560), fill=(186, 92, 255, 78))
    d.ellipse((-120, 180, 240, 520), fill=(255, 120, 200, 58))
    blobs = blobs.filter(ImageFilter.GaussianBlur(72))
    img = Image.alpha_composite(img, blobs)

    # 圆角
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, W - 1, H - 1), radius=RADIUS, fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (W, H), 0), mask))

    # 边缘高光
    edge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ed = ImageDraw.Draw(edge)
    ed.rounded_rectangle((2, 2, W - 3, H - 3), radius=RADIUS - 2,
                         outline=(255, 255, 255, 135), width=3)
    edge = edge.filter(ImageFilter.GaussianBlur(1.4))
    img = Image.alpha_composite(img, edge)

    # 顶部高光渐变
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i in range(70):
        a = int(70 * (1 - i / 70))
        gd.rounded_rectangle((0, i, W, 90), radius=RADIUS, fill=(255, 255, 255, a))
    glow = glow.filter(ImageFilter.GaussianBlur(22))
    # 只保留圆角内
    glow.putalpha(Image.composite(glow.getchannel("A"), Image.new("L", (W, H), 0), mask))
    img = Image.alpha_composite(img, glow)

    # 噪点
    noise = Image.effect_noise((W, H), 14).convert("L")
    nr = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    nr.putalpha(noise.point(lambda v: int(v * 0.05)))
    nr.putalpha(Image.composite(nr.getchannel("A"), Image.new("L", (W, H), 0), mask))
    img = Image.alpha_composite(img, nr)

    out = os.path.join(RES, "drawable-nodpi", "widget_glass.png")
    img.save(out, "PNG")
    print("wrote", out, img.size)

def icon():
    s = 192
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    grad = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(grad)
    for y in range(s):
        t = y / s
        r = int(70 + 60 * t); g = int(110 + 70 * t); b = int(230 - 30 * t)
        d.line([(0, y), (s, y)], fill=(r, g, b, 255))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=44, fill=255)
    img = Image.composite(grad, img, mask)
    d = ImageDraw.Draw(img)
    # 三条歌词横线 + 高亮点
    for i, (y, w) in enumerate([(76, 96), (96, 120), (116, 80)]):
        x = (s - w) // 2
        d.rounded_rectangle((x, y, x + w, y + 9), radius=4, fill=(255, 255, 255, 235))
    d.ellipse((s // 2 - 7, 138, s // 2 + 7, 152), fill=(255, 255, 255, 255))
    out = os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher.png")
    img.save(out, "PNG")
    print("wrote", out)

glass()
icon()
