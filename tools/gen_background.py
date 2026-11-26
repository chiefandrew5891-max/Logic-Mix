"""One-off generator for the static premium backgrounds (res/drawable-nodpi/premium_background_{dark,light}.webp).

Renders silk ribbons (bundles of hairline Bezier curves) once, offline; the app only draws the finished bitmap.
Usage: python3 tools/gen_background.py <out_dir>
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H, SS = 800, 2200, 2  # SS = supersampling for anti-aliased hairlines


def bez(p, n=500):
    t = np.linspace(0, 1, n)[:, None]
    p = np.asarray(p, float)
    return ((1 - t) ** 3) * p[0] + 3 * ((1 - t) ** 2) * t * p[1] + 3 * (1 - t) * t * t * p[2] + t ** 3 * p[3]


def gradient(stops):
    ys = np.linspace(0, 1, H)
    pos = [s[0] for s in stops]
    out = np.zeros((H, 3))
    for c in range(3):
        out[:, c] = np.interp(ys, pos, [s[1][c] for s in stops])
    return np.repeat(out[:, None, :], W, axis=1)


def radial(cx, cy, r, color, alpha):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx * W) ** 2 + (yy - cy * H) ** 2) / (r * W)
    a = np.clip(1 - d, 0, 1) ** 2 * alpha
    return a[..., None] * np.array(color)[None, None, :]


def hexc(h):
    return [int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)]


def ribbon_layer(rng, y0, gap, color, lines, strength, tilt):
    xs = np.array([-0.25, 0.3, 0.7, 1.25]) * W
    a = [(xs[i], H * (y0 + tilt * 3.2 * (i - 1.5) / 3 + rng.uniform(-0.07, 0.07))) for i in range(4)]
    b = [(xs[i], H * (y0 + gap + tilt * 1.9 * (i - 1.5) / 3 + rng.uniform(-0.12, 0.12))) for i in range(4)]
    m = Image.new("L", (W * SS, H * SS), 0)
    d = ImageDraw.Draw(m)
    for i in range(lines + 1):
        u = i / lines
        s = u ** 1.35
        pts = [(a[k][0] + (b[k][0] - a[k][0]) * s, a[k][1] + (b[k][1] - a[k][1]) * s) for k in range(4)]
        v = bez(pts) * SS
        level = int(255 * strength * (0.25 + 0.75 * u * u))
        d.line([tuple(p) for p in v], fill=level, width=SS)
    edge = bez(b) * SS
    em = Image.new("L", (W * SS, H * SS), 0)
    ImageDraw.Draw(em).line([tuple(p) for p in edge], fill=255, width=SS * 2)
    fill = Image.new("L", (W * SS, H * SS), 0)
    poly = [tuple(p) for p in bez(a) * SS] + [tuple(p) for p in bez(b)[::-1] * SS]
    ImageDraw.Draw(fill).polygon(poly, fill=int(255 * strength * 0.35))
    f = lambda im: np.asarray(im.resize((W, H), Image.LANCZOS), float)[..., None] / 255
    hair, edge_m, fill_m = f(m), f(em), f(fill.filter(ImageFilter.GaussianBlur(26 * SS)))
    glow = f(em.filter(ImageFilter.GaussianBlur(7 * SS)))
    return hair, edge_m, fill_m, glow


def render(dark, seed):
    rng = np.random.default_rng(seed)
    if dark:
        img = gradient([(0, hexc("#050B1F")), (0.5, hexc("#0A1C48")), (1, hexc("#04091A"))])
        img += radial(0.5, 0.22, 0.9, hexc("#16367E"), 0.55) + radial(0.9, 0.62, 0.8, hexc("#3A1C7A"), 0.5) + radial(0.1, 0.86, 0.8, hexc("#0B4A6E"), 0.55)
        bands = [("#2F6BFF", 0.08, 0.10, 44, 0.55, 0.06), ("#8E4DFF", 0.19, 0.14, 46, 0.6, -0.08), ("#35D0FF", 0.33, 0.09, 40, 0.6, 0.07),
                 ("#2A4FD0", 0.46, 0.16, 44, 0.5, -0.05), ("#E23CD0", 0.58, 0.10, 34, 0.45, 0.08), ("#2F6BFF", 0.69, 0.14, 46, 0.6, -0.07),
                 ("#35D0FF", 0.80, 0.10, 40, 0.55, 0.06), ("#8E4DFF", 0.90, 0.12, 40, 0.5, -0.05)]
    else:
        img = gradient([(0, hexc("#B7A2EB")), (0.42, hexc("#EDF0FF")), (1, hexc("#63BCDB"))])
        img += radial(0.5, 0.3, 0.9, (1, 1, 1), 0.12) + radial(0.1, 0.9, 0.9, hexc("#3FA9D0"), 0.3)
        bands = [("#8D6BE0", 0.07, 0.16, 36, 0.35, 0.05), ("#FFFFFF", 0.20, 0.10, 28, 0.8, -0.06), ("#4F86EE", 0.34, 0.18, 36, 0.35, 0.06),
                 ("#FFFFFF", 0.47, 0.09, 26, 0.75, -0.05), ("#1FB0C8", 0.60, 0.16, 36, 0.4, 0.05), ("#8D6BE0", 0.72, 0.14, 30, 0.3, -0.05),
                 ("#FFFFFF", 0.82, 0.08, 24, 0.7, 0.05), ("#1FB0C8", 0.91, 0.12, 32, 0.4, -0.04)]
    for hx, y0, gap, lines, strength, tilt in bands:
        col = np.array(hexc(hx))
        core = col * 0.4 + 0.6
        hair, edge, fill, glow = ribbon_layer(rng, y0, gap, col, lines, strength, tilt)
        if dark:
            img += fill * col * 0.5 + hair * col * 0.9 + glow * col * 0.35 + edge * core * 0.8
        else:
            for layer, c, k in ((fill, col, 0.6), (hair, col, 1.0), (glow, col, 0.35), (edge, core, 0.7)):
                al = np.clip(layer * k, 0, 1)
                img = img * (1 - al) + c * al
    img = np.clip(img + rng.normal(0, 0.004, img.shape), 0, 1)  # dither against banding
    return Image.fromarray((img * 255).astype(np.uint8))


if __name__ == "__main__":
    out = sys.argv[1]
    render(True, 7).save(f"{out}/premium_background_dark.webp", quality=82, method=6)
    render(False, 11).save(f"{out}/premium_background_light.webp", quality=82, method=6)
