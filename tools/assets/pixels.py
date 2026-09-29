"""Procedural 16x16 pixel-art painters for Galaxy MC's blocks and items.

Everything is deterministic: each texture is seeded from its name, so re-running the generator
reproduces the same art. Textures are numpy arrays of shape (H, W, 4), uint8 RGBA.
"""
import hashlib

import numpy as np
from PIL import Image, ImageDraw

N = 16


# ---------------------------------------------------------------------------------------------- basics

def rng(name):
    seed = int.from_bytes(hashlib.sha256(name.encode()).digest()[:4], "little")
    return np.random.RandomState(seed)


def hexc(c):
    """'#rrggbb' or 0xRRGGBB -> float array [r, g, b] in 0..255."""
    if isinstance(c, str):
        c = int(c.lstrip("#"), 16)
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], dtype=float)


def mix(a, b, t):
    return a + (b - a) * t


def value_noise(r, cells, size=N):
    """Tileable value noise: a random grid of `cells` x `cells`, bicubic-smoothed up to `size`."""
    grid = r.rand(cells, cells)
    xs = np.arange(size) * cells / size
    x0 = np.floor(xs).astype(int)
    f = xs - x0
    f = f * f * (3 - 2 * f)
    x1 = (x0 + 1) % cells
    x0 %= cells
    top = grid[x0][:, x0] * (1 - f)[None, :] + grid[x0][:, x1] * f[None, :]
    bot = grid[x1][:, x0] * (1 - f)[None, :] + grid[x1][:, x1] * f[None, :]
    return top * (1 - f)[:, None] + bot * f[:, None]


def fbm(r, octaves=((2, 0.35), (4, 0.3), (8, 0.2), (16, 0.15)), size=N):
    v = np.zeros((size, size))
    for cells, w in octaves:
        v += value_noise(r, cells, size) * w
    v -= v.min()
    m = v.max()
    return v / m if m > 0 else v


def ramp(v, colors):
    """Map values 0..1 through a list of colours (evenly spaced stops)."""
    cols = [hexc(c) for c in colors]
    n = len(cols) - 1
    t = np.clip(v, 0, 1) * n
    i = np.minimum(np.floor(t).astype(int), n - 1)
    f = (t - i)[..., None]
    a = np.stack([cols[k] for k in range(n + 1)])
    out = a[i] * (1 - f) + a[i + 1] * f
    return out


def rgba(rgb, alpha=255):
    h, w = rgb.shape[:2]
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = np.clip(np.round(rgb), 0, 255)
    out[..., 3] = alpha if np.isscalar(alpha) else np.clip(alpha, 0, 255)
    return out


def posterize(v, levels):
    return np.round(v * (levels - 1)) / (levels - 1)


def save(img, path):
    Image.fromarray(img, "RGBA").save(path)


def gray(img, lo=150, hi=250):
    """Luminance version remapped into [lo, hi] - the base for textures that are tinted in game."""
    out = img.copy()
    lum = img[..., 0] * 0.3 + img[..., 1] * 0.59 + img[..., 2] * 0.11
    mn, mx = lum[img[..., 3] > 0].min(), lum[img[..., 3] > 0].max()
    t = (lum - mn) / max(1.0, mx - mn)
    g = lo + t * (hi - lo)
    out[..., 0] = out[..., 1] = out[..., 2] = np.clip(g, 0, 255)
    return out


def shift(img, dy=0, dx=0):
    return np.roll(np.roll(img, dy, axis=0), dx, axis=1)


# ---------------------------------------------------------------------------------------------- terrain

def stone(name, colors, cracks=2, speckle=0.06, levels=6):
    r = rng(name)
    v = posterize(fbm(r), levels)
    rgb = ramp(v, colors)
    dark = hexc(colors[0]) * 0.82
    for _ in range(cracks):
        y, x = r.randint(0, N, 2)
        for _ in range(r.randint(3, 7)):
            rgb[y % N, x % N] = dark
            if r.rand() < 0.5:
                x += r.choice([-1, 1])
            else:
                y += r.choice([-1, 1])
    m = r.rand(N, N) < speckle
    rgb[m] = mix(rgb[m], hexc(colors[-1]), 0.6)
    return rgba(rgb)


def strata(name, colors, bands=5):
    r = rng(name)
    v = fbm(r, ((4, 0.4), (8, 0.35), (16, 0.25)))
    rows = (np.arange(N)[:, None] * bands / N + v * 1.4) % 1.0
    rgb = ramp(posterize(rows, len(colors) + 1), colors)
    rgb *= (0.9 + 0.2 * v)[..., None]
    return rgba(rgb)


def sand(name, colors, pebbles=4, levels=5):
    r = rng(name)
    v = posterize(fbm(r, ((4, 0.2), (8, 0.3), (16, 0.5))), levels)
    rgb = ramp(v, colors)
    for _ in range(pebbles):
        y, x = r.randint(0, N, 2)
        rgb[y, x] = hexc(colors[0])
        rgb[y, (x + 1) % N] = mix(hexc(colors[0]), hexc(colors[-1]), 0.3)
    return rgba(rgb)


def regolith(name, colors, craters=2):
    img = sand(name, colors, pebbles=3)
    r = rng(name + "#c")
    rgb = img[..., :3].astype(float)
    dark = hexc(colors[0]) * 0.85
    light = hexc(colors[-1])
    for _ in range(craters):
        cy, cx = r.randint(0, N, 2)
        rad = r.choice([1.5, 2.0, 2.5])
        for y in range(N):
            for x in range(N):
                dy = min(abs(y - cy), N - abs(y - cy))
                dx = min(abs(x - cx), N - abs(x - cx))
                d = (dx * dx + dy * dy) ** 0.5
                if d < rad - 0.5:
                    rgb[y, x] = mix(rgb[y, x], dark, 0.45)
                elif d < rad + 0.5:
                    lit = ((y - cy) + (x - cx)) > 0
                    rgb[y, x] = mix(rgb[y, x], light if lit else dark, 0.5)
    return rgba(rgb)


def ice(name, colors, streaks=5):
    r = rng(name)
    v = fbm(r, ((2, 0.5), (4, 0.3), (8, 0.2)))
    rgb = ramp(posterize(v, 5), colors)
    light = hexc("#ffffff")
    for _ in range(streaks):
        y, x = r.randint(0, N, 2)
        ln = r.randint(3, 7)
        for k in range(ln):
            yy, xx = (y + k) % N, (x + k) % N
            rgb[yy, xx] = mix(rgb[yy, xx], light, 0.55)
    return rgba(rgb)


def bricks(name, colors, mortar, rows=4):
    r = rng(name)
    base = fbm(r, ((8, 0.5), (16, 0.5)))
    rgb = ramp(posterize(base, 4) * 0.6 + 0.2, colors)
    mc = hexc(mortar)
    h = N // rows
    for row in range(rows):
        y0 = row * h
        off = (h * 2) if row % 2 else 0
        rgb[y0] = mc
        shade = 0.9 + r.rand(3) * 0.2
        for b in range(2):
            x0 = (off + b * 8) % N
            rgb[y0 + 1:y0 + h, x0] = mc
            span = [(x0 + k) % N for k in range(1, 8)]
            rgb[y0 + 1:y0 + h][:, span] *= shade[b]
        rgb[y0 + 1, :] = mix(rgb[y0 + 1, :], hexc(colors[-1]), 0.25)
    return rgba(rgb)


def polished(name, colors):
    r = rng(name)
    v = fbm(r, ((2, 0.6), (4, 0.4))) * 0.4 + 0.3
    rgb = ramp(v, colors)
    rgb[0, :] = rgb[:, 0] = hexc(colors[-1])
    rgb[N - 1, :] = rgb[:, N - 1] = hexc(colors[0])
    rgb[1:N - 1, 1] = mix(rgb[1:N - 1, 1], hexc(colors[-1]), 0.4)
    return rgba(rgb)


def ore(base, name, colors, clusters=5, size=(3, 5), glint=True):
    """Stamp mineral clusters on a base texture; colours are [dark, mid, light]."""
    img = base.copy()
    r = rng(name + "#ore")
    dark, mid, light = (hexc(c) for c in colors)
    taken = np.zeros((N, N), bool)
    for _ in range(clusters):
        for _attempt in range(12):
            cy, cx = r.randint(1, N - 3, 2)
            if not taken[max(0, cy - 2):cy + 4, max(0, cx - 2):cx + 4].any():
                break
        n = r.randint(size[0], size[1] + 1)
        pts = {(cy, cx)}
        while len(pts) < n + 1:
            y, x = list(pts)[r.randint(len(pts))]
            d = [(0, 1), (1, 0), (0, -1), (-1, 0), (1, 1)][r.randint(5)]
            pts.add((min(N - 1, max(0, y + d[0])), min(N - 1, max(0, x + d[1]))))
        for (y, x) in pts:
            img[y, x, :3] = mid
            taken[y, x] = True
        for (y, x) in pts:
            if (y + 1, x) not in pts and y + 1 < N:
                img[y + 1, x, :3] = dark
                taken[y + 1, x] = True
            if (y - 1, x) not in pts and (y, x - 1) not in pts:
                img[y, x, :3] = light
        if glint:
            y, x = min(pts)
            img[y, x, :3] = mix(light, hexc("#ffffff"), 0.6)
    return img


def overlay_only(base, oreimg):
    """Pixels that differ from the base become an alpha overlay (for two-layer tinted ores)."""
    diff = np.any(base[..., :3] != oreimg[..., :3], axis=-1)
    out = oreimg.copy()
    out[..., 3] = np.where(diff, 255, 0)
    return out


def grass_top(name, colors):
    r = rng(name)
    v = posterize(fbm(r, ((4, 0.3), (8, 0.3), (16, 0.4))), 5)
    return rgba(ramp(v, colors))


def grass_overlay(name, colors, depth=(3, 6)):
    """Side overlay: drippy grass along the top edge, transparent below."""
    r = rng(name + "#ov")
    top = grass_top(name, colors)
    out = top.copy()
    for x in range(N):
        d = r.randint(depth[0], depth[1] + 1)
        out[d:, x, 3] = 0
        out[d - 1, x, :3] = (out[d - 1, x, :3] * 0.8).astype(np.uint8)
    return out


def leaves(name, colors, holes=0.22):
    r = rng(name)
    v = fbm(r, ((4, 0.3), (8, 0.3), (16, 0.4)))
    rgb = ramp(posterize(v, 5), colors)
    a = np.where(r.rand(N, N) < holes, 0, 255)
    # Clumps: darker pixel below each lit one reads as leaf shape.
    lit = r.rand(N, N) < 0.18
    rgb[lit] = mix(rgb[lit], hexc(colors[-1]), 0.5)
    return rgba(rgb, a)


def log_side(name, colors):
    r = rng(name)
    v = fbm(r, ((2, 0.2), (16, 0.8)))
    stripes = (np.sin(np.arange(N) * 2.1 + r.rand() * 6)[None, :] * 0.5 + 0.5) * 0.5
    t = posterize(np.clip(v * 0.6 + stripes * np.ones((N, 1)), 0, 1), 5)
    rgb = ramp(t, colors)
    for _ in range(3):
        x = r.randint(N)
        y = r.randint(N)
        rgb[y:y + r.randint(2, 5), x] = hexc(colors[0])
    return rgba(rgb)


def log_top(name, bark, rings):
    img = np.zeros((N, N, 4), np.uint8)
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            d = max(abs(y - c), abs(x - c))
            if d > 6.5:
                img[y, x, :3] = hexc(bark[1])
            else:
                ring = int(d) % 2
                img[y, x, :3] = hexc(rings[ring])
            img[y, x, 3] = 255
    img[int(c), int(c), :3] = hexc(rings[0]) * 0.8
    return img


def planks(name, colors):
    r = rng(name)
    grain = fbm(r, ((2, 0.3), (16, 0.7)))
    rgb = ramp(posterize(grain * 0.6 + 0.2, 4), colors)
    for y in range(0, N, 4):
        rgb[y + 3, :] = hexc(colors[0])
        cut = (r.randint(N) + (8 if (y // 4) % 2 else 0)) % N
        rgb[y:y + 3, cut] = hexc(colors[0]) * 1.05
    return rgba(rgb)


def cloud(name, colors):
    r = rng(name)
    v = fbm(r, ((2, 0.45), (4, 0.35), (8, 0.2)))
    band = (np.sin(np.arange(N) * 0.8 + v * 5)[..., None] if False else np.sin(np.arange(N)[:, None] * 0.7 + v * 5)) * 0.5 + 0.5
    return rgba(ramp(posterize(v * 0.6 + band * 0.4, 5), colors))


def cheese(name):
    r = rng(name)
    rgb = ramp(fbm(r, ((4, 0.5), (8, 0.5))) * 0.5 + 0.4, ["#c89a2a", "#e8c050", "#f8e080"])
    for _ in range(6):
        cy, cx = r.randint(0, N, 2)
        rad = r.choice([1.0, 1.5, 2.0])
        for y in range(N):
            for x in range(N):
                if (y - cy) ** 2 + (x - cx) ** 2 <= rad * rad:
                    rgb[y % N, x % N] = hexc("#a87818")
        rgb[(cy + int(rad)) % N, cx % N] = hexc("#fff0a0")
    return rgba(rgb)


def plasma_frames(name, frames=8):
    r = rng(name)
    base = [fbm(r, ((2, 0.4), (4, 0.35), (8, 0.25))) for _ in range(2)]
    out = []
    for f in range(frames):
        t = f / frames
        v = base[0] * (0.5 + 0.5 * np.cos(t * 2 * np.pi)) + base[1] * (0.5 - 0.5 * np.cos(t * 2 * np.pi))
        v = shift(v, f // 2, f)
        out.append(rgba(ramp(v, ["#c83000", "#ff7a00", "#ffc020", "#fff4a0", "#ffffff"])))
    return np.concatenate(out, axis=0)


def slag(name):
    r = rng(name)
    v = fbm(r)
    rgb = ramp(posterize(v, 5), ["#141010", "#241c18", "#342820"])
    cr = fbm(rng(name + "#cr"), ((4, 0.6), (8, 0.4)))
    m = np.abs(cr - 0.5) < 0.05
    rgb[m] = hexc("#ff6a10")
    m2 = (np.abs(cr - 0.5) < 0.09) & ~m
    rgb[m2] = hexc("#8a2a08")
    return rgba(rgb)


def crystal_block(name, colors):
    r = rng(name)
    img = np.zeros((N, N, 3))
    img[:] = hexc(colors[1])
    facets = r.randint(5, 8)
    pts = r.randint(0, N, (facets, 2))
    shades = np.linspace(0, 1, facets)
    r.shuffle(shades)
    yy, xx = np.mgrid[0:N, 0:N]
    best = np.full((N, N), 1e9)
    idx = np.zeros((N, N), int)
    for k, (py, px) in enumerate(pts):
        for oy in (-N, 0, N):
            for ox in (-N, 0, N):
                d = (yy - py - oy) ** 2 + (xx - px - ox) ** 2
                upd = d < best
                best[upd] = d[upd]
                idx[upd] = k
    rgb = ramp(shades[idx], colors)
    edge = (idx != np.roll(idx, 1, 0)) | (idx != np.roll(idx, 1, 1))
    rgb[edge] = mix(rgb[edge], hexc(colors[-1]), 0.6)
    return rgba(rgb)


# ---------------------------------------------------------------------------------------------- machines

def panel(name, colors, rivets=True, seams=True):
    r = rng(name)
    v = fbm(r, ((2, 0.5), (4, 0.3), (16, 0.2))) * 0.35 + 0.45
    rgb = ramp(v, colors)
    if seams:
        rgb[0, :] = rgb[:, 0] = hexc(colors[-1])
        rgb[N - 1, :] = rgb[:, N - 1] = hexc(colors[0])
        rgb[7, 1:N - 1] = hexc(colors[0])
        rgb[8, 1:N - 1] = hexc(colors[-1])
    if rivets:
        for (y, x) in ((2, 2), (2, 13), (5, 2), (5, 13), (10, 2), (10, 13), (13, 2), (13, 13)):
            rgb[y, x] = hexc(colors[0])
            rgb[y - 1, x - 1] = hexc(colors[-1])
    return rgba(rgb)


def hazard(name, a="#f08a10", b="#202020", base_colors=("#b8bcc0", "#d8dce0", "#f0f2f4")):
    img = panel(name, base_colors, rivets=False)
    for y in range(4, 12):
        for x in range(N):
            img[y, x, :3] = hexc(a) if ((x + y) // 3) % 2 == 0 else hexc(b)
    img[3, :, :3] = hexc(base_colors[0])
    img[12, :, :3] = hexc(base_colors[0])
    return img


def grate(name):
    img = panel(name, ["#4a4e54", "#5c6066", "#747880"], rivets=False, seams=False)
    for y in range(N):
        for x in range(N):
            if (y % 4 in (1, 2)) and (x % 4 in (1, 2)):
                img[y, x, :3] = hexc("#1c1e22")
            elif y % 4 == 3 or x % 4 == 3:
                img[y, x, :3] = hexc("#3a3e44")
    return img


def ship_light(name):
    img = rgba(np.ones((N, N, 3)) * hexc("#fffbe8"))
    rgb = img[..., :3].astype(float)
    frame = hexc("#8c9096")
    rgb[0:2, :] = rgb[N - 2:, :] = frame
    rgb[:, 0:2] = rgb[:, N - 2:] = frame
    rgb[2, 2:N - 2] = hexc("#ffffff")
    rgb[N - 3, 2:N - 2] = hexc("#f0e8c8")
    return rgba(rgb)


def glass(name, frame="#8c9aa8", pane="#a8d0e8", alpha=70):
    img = np.zeros((N, N, 4), np.uint8)
    img[..., :3] = hexc(pane)
    img[..., 3] = alpha
    fr = hexc(frame)
    for k in range(N):
        for (y, x) in ((0, k), (N - 1, k), (k, 0), (k, N - 1)):
            img[y, x, :3] = fr
            img[y, x, 3] = 255
    for k in range(3, 9):
        img[k, 12 - k, :3] = hexc("#ffffff")
        img[k, 12 - k, 3] = 150
        img[k + 1, 12 - k, :3] = hexc("#ffffff")
        img[k + 1, 12 - k, 3] = 110
    img[1, 1:N - 1, :3] = hexc("#c8d4e0")
    return img


def vents(name, colors, glow):
    img = panel(name, colors, rivets=False)
    for y in range(3, 13, 3):
        img[y, 3:13, :3] = hexc("#101418")
        img[y + 1, 3:13, :3] = hexc(glow)
    return img


def nozzle(name):
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 3] = 255
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            d = ((y - c) ** 2 + (x - c) ** 2) ** 0.5
            if d < 2.5:
                col = "#ffd060"
            elif d < 4.2:
                col = "#ff7a20"
            elif d < 5.5:
                col = "#2a2c30"
            elif d < 7.0:
                col = "#6a6e74"
            else:
                col = "#484c52"
            img[y, x, :3] = hexc(col)
    return img


def screen(name, stars=True):
    r = rng(name)
    img = panel(name, ["#303848", "#3c4658", "#566278"], rivets=False)
    rgb = img[..., :3].astype(float)
    rgb[2:11, 2:14] = hexc("#061228")
    if stars:
        for _ in range(9):
            y, x = r.randint(3, 10), r.randint(3, 13)
            rgb[y, x] = hexc(["#9ad8ff", "#ffffff", "#ffd27a"][r.randint(3)])
        rgb[6, 6:10] = hexc("#40c0ff")
        rgb[5:8, 8] = hexc("#40c0ff")
    for i, col in enumerate(("#ff5040", "#ffd040", "#50e060", "#40a0ff")):
        rgb[13, 3 + i * 3:5 + i * 3] = hexc(col)
    return rgba(rgb)


def barrel(name, body=("#8a1a14", "#b8281e", "#d8483a")):
    img = rgba(ramp(np.tile(np.abs(np.linspace(-1, 1, N)), (N, 1)) * -1 + 1, list(body)))
    rgb = img[..., :3].astype(float)
    rgb[1, :] = rgb[N - 2, :] = hexc("#3a3a3e")
    for y in range(6, 10):
        for x in range(N):
            rgb[y, x] = hexc("#f0c020") if ((x + y) // 2) % 2 == 0 else hexc("#202020")
    return rgba(rgb)


def barrel_top(name):
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 3] = 255
    img[..., :3] = hexc("#4a4c52")
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            d = ((y - c) ** 2 + (x - c) ** 2) ** 0.5
            if d < 2:
                img[y, x, :3] = hexc("#f0c020")
            elif d < 3:
                img[y, x, :3] = hexc("#202020")
            elif d > 6.8:
                img[y, x, :3] = hexc("#b8281e")
    return img


def metal_block(name, colors):
    r = rng(name)
    v = fbm(r, ((2, 0.4), (4, 0.3), (16, 0.3))) * 0.4 + 0.35
    rgb = ramp(v, colors)
    rgb[0, :] = rgb[:, 0] = hexc(colors[-1])
    rgb[N - 1, :] = rgb[:, N - 1] = hexc(colors[0])
    rgb[1:N - 1, N - 2] = mix(rgb[1:N - 1, N - 2], hexc(colors[0]), 0.4)
    for k in range(2, 6):
        rgb[k, 7 - k + 2] = hexc(colors[-1])
    return rgba(rgb)


def table_top(name):
    img = planks(name, ["#5a3a20", "#7a5230", "#946a40"])
    rgb = img[..., :3].astype(float)
    # Wool half.
    rgb[2:14, 2:8] = ramp(fbm(rng(name + "w"), ((8, 0.5), (16, 0.5))) * 0.5 + 0.4, ["#c83030", "#e84848", "#f07070"])[2:14, 2:8]
    # Ice half.
    rgb[2:14, 8:14] = ice(name + "i", ["#6aa0d8", "#8ac0f0", "#c0e4ff"])[2:14, 8:14, :3]
    for y in range(3, 13, 2):
        rgb[y, 7] = hexc("#f0f0f0")
    return rgba(rgb)


def forge_top(name, bg=("#20182c", "#2c2240", "#3c3058"), core=("#ffe0ff", "#d060ff"), ring="#8030c0"):
    img = panel(name, list(bg), rivets=False)
    rgb = img[..., :3].astype(float)
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            d = ((y - c) ** 2 + (x - c) ** 2) ** 0.5
            if d < 2.2:
                rgb[y, x] = hexc(core[0])
            elif d < 3.4:
                rgb[y, x] = hexc(core[1])
            elif 5.0 < d < 5.9:
                rgb[y, x] = hexc(ring)
    for (y, x) in ((1, 7), (1, 8), (14, 7), (14, 8), (7, 1), (8, 1), (7, 14), (8, 14)):
        rgb[y, x] = hexc(core[1])
    return rgba(rgb)


def fan_top(name, bg, blade, glow):
    """Round intake grille with a four-blade fan and a glowing hub."""
    img = panel(name, list(bg), rivets=True)
    rgb = img[..., :3].astype(float)
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            dy, dx = y - c, x - c
            d = (dy * dy + dx * dx) ** 0.5
            if d < 6.2:
                ang = np.arctan2(dy, dx)
                on_blade = (np.cos(4 * ang + d * 0.5) > 0.35) and d > 1.8
                rgb[y, x] = hexc(blade) if on_blade else hexc("#0c1418")
            if d < 1.8:
                rgb[y, x] = hexc(glow)
            elif 6.2 <= d < 7.0:
                rgb[y, x] = hexc(bg[0])
    return rgba(rgb)


def door_half(name, top):
    img = panel(name, ["#8c9096", "#a8acb2", "#c8ccd2"], rivets=False, seams=False)
    rgb = img[..., :3].astype(float)
    rgb[:, 0] = rgb[:, N - 1] = hexc("#6a6e74")
    rgb[:, 7:9] = hexc("#5a5e64")
    if top:
        rgb[0, :] = hexc("#6a6e74")
        for x in (2, 10):
            rgb[3:10, x:x + 4] = hexc("#a8d0e8")
            rgb[3, x:x + 4] = hexc("#e0f0ff")
    else:
        rgb[N - 1, :] = hexc("#6a6e74")
        for y in range(10, 14):
            for x in range(1, N - 1):
                if x in (7, 8):
                    continue
                rgb[y, x] = hexc("#f0c020") if ((x + y) // 2) % 2 == 0 else hexc("#202020")
    return rgba(rgb)


# ---------------------------------------------------------------------------------------------- plants

def _canvas():
    return np.zeros((N, N, 4), np.uint8)


def _px(img, y, x, c, a=255):
    if 0 <= y < N and 0 <= x < N:
        img[y, x, :3] = hexc(c)
        img[y, x, 3] = a


def blades(name, colors, count=7):
    r = rng(name)
    img = _canvas()
    for i in range(count):
        x = 1 + int(i * (N - 2) / count) + r.randint(0, 2)
        h = r.randint(6, 14)
        lean = r.choice([-1, 0, 1])
        for k in range(h):
            y = N - 1 - k
            xx = x + (lean * k) // 5
            t = k / h
            c = colors[min(len(colors) - 1, int(t * len(colors)))]
            _px(img, y, xx, c)
    return img


def mushroom(name, stem, cap, spots):
    r = rng(name)
    img = _canvas()
    for (cx, h, w) in ((5, 9, 3), (11, 6, 2)):
        for y in range(N - h, N):
            _px(img, y, cx, stem[0])
            _px(img, y, cx + 1, stem[1])
        top = N - h - 2
        for y in range(top, top + 3):
            for x in range(cx - w, cx + w + 2):
                c = cap[1] if y > top else cap[0]
                _px(img, y, x, c)
        _px(img, top, cx - w + 1 + r.randint(0, 2), spots)
        _px(img, top + 1, cx + w, spots)
    return img


def spines(name):
    img = _canvas()
    body = ["#2a6040", "#3a8050", "#58a868"]
    for y in range(3, N):
        for x in range(6, 10):
            img[y, x, :3] = hexc(body[min(2, abs(x - 7))])
            img[y, x, 3] = 255
    for (y, x0, d) in ((4, 5, -1), (7, 10, 1), (10, 5, -1), (13, 10, 1), (6, 5, -1), (11, 10, 1)):
        for k in range(3):
            _px(img, y - k // 2, x0 + d * k, "#e8e0c0")
    for x in range(6, 10):
        _px(img, 2, x, "#d050c0")
    _px(img, 1, 7, "#f080e0")
    _px(img, 1, 8, "#f080e0")
    return img


def bloom(name, petals, center, stem="#3a7050"):
    img = _canvas()
    for y in range(8, N):
        _px(img, y, 7, stem)
    _px(img, 11, 6, stem)
    _px(img, 12, 5, stem)
    _px(img, 10, 8, stem)
    _px(img, 9, 9, stem)
    cy, cx = 5, 7
    for (dy, dx) in ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (-1, 1), (1, -1), (1, 1), (-2, 1), (0, 3), (2, 1), (1, 2), (-1, 2)):
        _px(img, cy + dy, cx + dx, petals[(abs(dy) + abs(dx)) % len(petals)])
    _px(img, cy, cx, center)
    _px(img, cy, cx + 1, center)
    return img


def shard(name):
    img = _canvas()
    for (x0, h, w) in ((7, 13, 2), (3, 8, 2), (11, 9, 2), (5, 5, 1), (13, 5, 1)):
        for k in range(h):
            y = N - 1 - k
            ww = w if k < h - 2 else max(0, w - 1)
            for x in range(x0, x0 + ww + (0 if k >= h - 1 else 1)):
                c = 250 if x == x0 else (200 if x == x0 + ww else 225)
                if 0 <= x < N:
                    img[y, x, :3] = c
                    img[y, x, 3] = 255
    return img


# ---------------------------------------------------------------------------------------------- items

def _mask(draw_fn, scale=4):
    big = Image.new("L", (N * scale, N * scale), 0)
    draw_fn(ImageDraw.Draw(big), scale)
    small = np.array(big.resize((N, N), Image.BOX)) > 110
    return small


def sprite(mask, colors, name, light_dir=(1, 1), noise=0.12, outline=None):
    """Shade a silhouette: diagonal light, a little noise, dark outline and a lit top-left rim."""
    r = rng(name)
    yy, xx = np.mgrid[0:N, 0:N]
    t = 1 - (yy * light_dir[0] + xx * light_dir[1]) / (2 * N)
    t = np.clip(t + (r.rand(N, N) - 0.5) * noise, 0, 1)
    rgb = ramp(posterize(t, 5), colors[1:])
    img = rgba(rgb, np.where(mask, 255, 0))
    out = hexc(outline or colors[0])
    pad = np.pad(mask, 1)
    for y in range(N):
        for x in range(N):
            if not mask[y, x]:
                continue
            nb = [pad[y, x + 1], pad[y + 2, x + 1], pad[y + 1, x], pad[y + 1, x + 2]]
            if not all(nb):
                img[y, x, :3] = out
            elif not pad[y, x + 1] or not pad[y + 1, x]:
                img[y, x, :3] = hexc(colors[-1])
    return img


def lump(name, colors):
    r = rng(name)
    pts = []
    for k in range(9):
        a = k / 9 * 2 * np.pi
        rad = 5.2 + r.rand() * 1.8
        pts.append((8 + np.cos(a) * rad, 8.5 + np.sin(a) * rad * 0.85))

    def d(dr, s):
        dr.polygon([(x * s, y * s) for x, y in pts], fill=255)

    img = sprite(_mask(d), colors, name)
    for _ in range(4):
        y, x = r.randint(5, 12), r.randint(4, 12)
        if img[y, x, 3]:
            img[y, x, :3] = hexc(colors[-1])
    return img


def ingot(name, colors):
    def d(dr, s):
        dr.polygon([(2 * s, 10 * s), (6 * s, 5 * s), (14 * s, 5 * s), (14 * s, 7 * s), (10 * s, 12 * s), (2 * s, 12 * s)], fill=255)

    img = sprite(_mask(d), colors, name)
    for x in range(6, 13):
        if img[6, x, 3]:
            img[6, x, :3] = hexc(colors[-1])
    for x in range(3, 10):
        if img[10, x, 3]:
            img[10, x, :3] = hexc(colors[2])
    return img


def gem(name, colors, shape="diamond"):
    def d(dr, s):
        if shape == "diamond":
            dr.polygon([(8 * s, 1 * s), (14 * s, 6 * s), (8 * s, 15 * s), (2 * s, 6 * s)], fill=255)
        elif shape == "shard":
            dr.polygon([(10 * s, 1 * s), (13 * s, 5 * s), (7 * s, 15 * s), (4 * s, 11 * s)], fill=255)
        elif shape == "cluster":
            dr.polygon([(7 * s, 1 * s), (10 * s, 5 * s), (8 * s, 14 * s), (5 * s, 6 * s)], fill=255)
            dr.polygon([(3 * s, 6 * s), (6 * s, 9 * s), (5 * s, 15 * s), (1 * s, 11 * s)], fill=255)
            dr.polygon([(12 * s, 5 * s), (15 * s, 10 * s), (11 * s, 15 * s), (9 * s, 10 * s)], fill=255)
        elif shape == "hex":
            dr.regular_polygon((8 * s, 8 * s, 6.5 * s), 6, fill=255)
        elif shape == "round":
            dr.ellipse((2 * s, 2 * s, 14 * s, 14 * s), fill=255)

    img = sprite(_mask(d), colors, name, noise=0.05)
    if shape in ("diamond", "hex", "round"):
        _px(img, 4, 6, "#ffffff")
        _px(img, 5, 5, "#ffffff", 200)
    return img


def powder(name, colors):
    def d(dr, s):
        dr.ellipse((2 * s, 7 * s, 14 * s, 15 * s), fill=255)
        dr.ellipse((5 * s, 4 * s, 11 * s, 11 * s), fill=255)

    img = sprite(_mask(d), colors, name, noise=0.3)
    r = rng(name + "g")
    for _ in range(5):
        y, x = r.randint(5, 14), r.randint(3, 13)
        if img[y, x, 3]:
            img[y, x, :3] = hexc(colors[-1])
    return img


def wedge(name):
    def d(dr, s):
        dr.polygon([(1 * s, 11 * s), (13 * s, 4 * s), (15 * s, 9 * s), (15 * s, 13 * s), (1 * s, 14 * s)], fill=255)

    img = sprite(_mask(d), ["#7a5410", "#c89a2a", "#e8c050", "#f8e080"], name)
    for (y, x) in ((11, 5), (12, 10), (9, 12), (12, 13)):
        _px(img, y, x, "#a87818")
    return img


def hide(name, colors):
    def d(dr, s):
        dr.polygon([(3 * s, 2 * s), (13 * s, 2 * s), (15 * s, 6 * s), (12 * s, 9 * s), (14 * s, 14 * s), (2 * s, 14 * s), (4 * s, 9 * s), (1 * s, 6 * s)], fill=255)

    img = sprite(_mask(d), colors, name, noise=0.25)
    r = rng(name + "s")
    for _ in range(6):
        y, x = r.randint(4, 13), r.randint(4, 12)
        if img[y, x, 3]:
            img[y, x, :3] = hexc(colors[1])
    return img


def blob(name, colors, shine=True):
    def d(dr, s):
        dr.ellipse((2 * s, 4 * s, 14 * s, 15 * s), fill=255)
        dr.ellipse((5 * s, 1 * s, 11 * s, 8 * s), fill=255)

    img = sprite(_mask(d), colors, name, noise=0.05)
    if shine:
        _px(img, 6, 5, "#ffffff")
        _px(img, 7, 5, "#ffffff", 180)
        _px(img, 6, 6, "#ffffff", 180)
    return img


def spores(name, colors):
    img = _canvas()
    r = rng(name)
    for _ in range(7):
        cy, cx = r.randint(3, 13), r.randint(3, 13)
        _px(img, cy, cx, colors[-1])
        for (dy, dx) in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            if not img[cy + dy, cx + dx, 3]:
                _px(img, cy + dy, cx + dx, colors[1], 200)
    return img


def spool(name, colors):
    def d(dr, s):
        dr.rectangle((4 * s, 2 * s, 12 * s, 14 * s), fill=255)
        dr.rectangle((3 * s, 1 * s, 13 * s, 3 * s), fill=255)
        dr.rectangle((3 * s, 13 * s, 13 * s, 15 * s), fill=255)

    img = sprite(_mask(d), colors, name)
    for y in range(4, 13, 2):
        for x in range(5, 12):
            if img[y, x, 3]:
                img[y, x, :3] = hexc(colors[1])
    for y in (1, 2, 13, 14):
        for x in range(3, 13):
            if img[y, x, 3]:
                img[y, x, :3] = hexc("#8a6a40" if y in (1, 14) else "#6a4a28")
    return img


def fang(name, colors):
    def d(dr, s):
        dr.polygon([(4 * s, 1 * s), (11 * s, 2 * s), (12 * s, 5 * s), (9 * s, 11 * s), (6 * s, 15 * s), (6 * s, 8 * s)], fill=255)

    return sprite(_mask(d), colors, name, noise=0.05)


def scale_item(name, colors):
    """A single broad scale: rounded top, pointed tip, a central ridge and growth lines."""
    def d(dr, s):
        dr.ellipse((2 * s, 1 * s, 14 * s, 11 * s), fill=255)
        dr.polygon([(2 * s, 6 * s), (14 * s, 6 * s), (8 * s, 15.5 * s)], fill=255)

    img = sprite(_mask(d), colors, name, noise=0.05)
    for y in range(2, 14):
        if img[y, 8, 3]:
            img[y, 8, :3] = hexc(colors[-1])
            if img[y, 9, 3]:
                img[y, 9, :3] = hexc(colors[1])
    for y in (5, 8, 11):
        for x in range(3, 14):
            if img[y, x, 3] and x not in (8, 9) and img[y - 1, x, 3] and img[y + 1, x, 3]:
                img[y, x, :3] = hexc(colors[2])
    return img


def meat(name, colors, bone="#e8e0d0"):
    def d(dr, s):
        dr.ellipse((2 * s, 3 * s, 13 * s, 13 * s), fill=255)

    img = sprite(_mask(d), colors, name, noise=0.2)
    for k in range(4):
        _px(img, 11 + k // 2, 11 + k, bone)
    _px(img, 14, 14, bone)
    _px(img, 13, 15, bone)
    _px(img, 15, 13, bone)
    return img


def canister(name):
    def d(dr, s):
        dr.rounded_rectangle((4 * s, 3 * s, 12 * s, 15 * s), radius=2 * s, fill=255)
        dr.rectangle((6 * s, 1 * s, 10 * s, 3 * s), fill=255)

    img = sprite(_mask(d), ["#401010", "#8a1a14", "#b8281e", "#d8483a", "#f07060"], name)
    for x in range(5, 12):
        for y in (7, 8, 9):
            if img[y, x, 3]:
                img[y, x, :3] = hexc("#f0c020") if (x + y) % 3 else hexc("#202020")
    for x in range(6, 10):
        _px(img, 1, x, "#9a9ea4")
    return img


def blueprint(name, paper, lines, accent):
    def d(dr, s):
        dr.rectangle((2 * s, 3 * s, 14 * s, 13 * s), fill=255)
        dr.ellipse((0 * s, 2 * s, 4 * s, 14 * s), fill=255)

    img = sprite(_mask(d), [paper[0], paper[0], paper[1], paper[2], paper[2]], name, noise=0.05)
    for y in range(4, 12):
        if img[y, 1, 3]:
            img[y, 1, :3] = hexc(paper[0])
            img[y, 2, :3] = hexc(paper[2])
    # A little ship silhouette.
    for (y, x) in ((5, 9), (6, 8), (6, 9), (6, 10), (7, 8), (7, 9), (7, 10), (8, 8), (8, 9), (8, 10), (9, 7), (9, 11), (10, 7), (10, 11)):
        _px(img, y, x, lines)
    _px(img, 11, 9, accent)
    _px(img, 4, 5, lines)
    _px(img, 4, 13, lines)
    for x in range(5, 14, 2):
        _px(img, 12, x, lines)
    return img


def thermometer(name):
    img = _canvas()
    for y in range(1, 11):
        _px(img, y, 7, "#c8d8e8")
        _px(img, y, 9, "#8898a8")
        _px(img, y, 8, "#e03030" if y > 4 else "#e8f0f8")
    for (y, x) in ((0, 8), (11, 7), (11, 9)):
        _px(img, y, x, "#8898a8")
    for y in range(11, 15):
        for x in range(6, 11):
            if (y - 12.5) ** 2 + (x - 8) ** 2 <= 5:
                _px(img, y, x, "#c02020")
    _px(img, 12, 7, "#ff8080")
    for y in (3, 5, 7, 9):
        _px(img, y, 10, "#606870")
    return img


SUIT = ["#50545c", "#b8bcc4", "#d8dce2", "#eef0f4", "#ffffff"]


def suit_piece(name, piece):
    def d(dr, s):
        if piece == "helmet":
            dr.ellipse((2 * s, 1 * s, 14 * s, 14 * s), fill=255)
        elif piece == "chestplate":
            dr.polygon([(1 * s, 2 * s), (5 * s, 1 * s), (11 * s, 1 * s), (15 * s, 2 * s), (15 * s, 7 * s), (12 * s, 7 * s),
                        (12 * s, 15 * s), (4 * s, 15 * s), (4 * s, 7 * s), (1 * s, 7 * s)], fill=255)
        elif piece == "leggings":
            dr.polygon([(3 * s, 1 * s), (13 * s, 1 * s), (13 * s, 15 * s), (9 * s, 15 * s), (8 * s, 6 * s), (7 * s, 15 * s), (3 * s, 15 * s)], fill=255)
        else:
            dr.polygon([(1 * s, 3 * s), (7 * s, 3 * s), (7 * s, 11 * s), (8 * s, 15 * s), (0 * s, 15 * s), (1 * s, 11 * s)], fill=255)
            dr.polygon([(9 * s, 3 * s), (15 * s, 3 * s), (15 * s, 11 * s), (16 * s, 15 * s), (8 * s, 15 * s), (9 * s, 11 * s)], fill=255)

    img = sprite(_mask(d), SUIT, name, noise=0.05)
    if piece == "helmet":
        for y in range(5, 11):
            for x in range(4, 12):
                if (y - 7.5) ** 2 / 9 + (x - 7.5) ** 2 / 16 <= 1:
                    img[y, x, :3] = hexc("#e8a020" if y > 5 else "#ffd070")
        _px(img, 6, 5, "#fff4c0")
    elif piece == "chestplate":
        for x in range(5, 12):
            _px(img, 5, x, "#f08a10")
        _px(img, 8, 7, "#40c0ff")
        _px(img, 8, 8, "#40c0ff")
        _px(img, 9, 7, "#1a6090")
    elif piece == "leggings":
        for y in range(2, 14):
            if img[y, 4, 3]:
                img[y, 4, :3] = hexc("#f08a10")
            if img[y, 11, 3]:
                img[y, 11, :3] = hexc("#f08a10")
    else:
        for y in (13, 14):
            for x in range(N):
                if img[y, x, 3]:
                    img[y, x, :3] = hexc("#404448" if y == 13 else "#2a2c30")
        for x in list(range(2, 7)) + list(range(10, 15)):
            if img[5, x, 3]:
                img[5, x, :3] = hexc("#f08a10")
    return img


def core(name):
    img = gem(name, ["#401060", "#8030c0", "#c060ff", "#f0b0ff", "#ffffff"], "round")
    c = 7.5
    for y in range(N):
        for x in range(N):
            if img[y, x, 3] and ((y - c) ** 2 + (x - c) ** 2) < 6:
                img[y, x, :3] = hexc("#fff0ff")
    return img


def mineral_layers(name):
    """Exotic mineral: tinted crystal cluster (layer0) plus untinted glints (layer1)."""
    base = gray(gem(name, ["#303030", "#707070", "#a0a0a0", "#d0d0d0", "#f0f0f0"], "cluster"), 120, 255)
    glints = _canvas()
    for (y, x) in ((3, 7), (4, 7), (8, 2), (7, 12), (2, 8)):
        if base[y, x, 3]:
            _px(glints, y, x, "#ffffff", 230)
    return base, glints


def relic_layers(name):
    """Relic: tinted gem (layer0) in an untinted gold setting (layer1)."""
    def d(dr, s):
        dr.ellipse((1 * s, 1 * s, 15 * s, 15 * s), fill=255)

    ring = sprite(_mask(d), ["#5a3a08", "#a07018", "#d8a830", "#f0d060", "#fff0a0"], name + "ring", noise=0.05)

    def g(dr, s):
        dr.regular_polygon((8 * s, 8 * s, 4.6 * s), 6, rotation=30, fill=255)

    gm = _mask(g)
    ring[gm] = 0
    gem_img = gray(sprite(gm, ["#202020", "#606060", "#909090", "#c8c8c8", "#ffffff"], name + "gem", noise=0.05), 110, 255)
    _px(ring, 5, 6, "#ffffff", 220)
    for (y, x) in ((0, 8), (8, 0), (8, 15), (15, 8)):
        _px(ring, y, x, "#fff0a0")
    return gem_img, ring


# ---------------------------------------------------------------------------------------------- armour

def suit_equipment(leggings=False):
    """64x32 humanoid armour layer (standard player UV layout)."""
    w, h = 64, 32
    img = np.zeros((h, w, 4), np.uint8)
    r = rng("space_suit" + ("_legs" if leggings else ""))

    def fill(x0, y0, ww, hh, colors, vis=True):
        v = fbm(r, ((4, 0.5), (8, 0.5)), size=64)[:hh, :ww] * 0.35 + 0.5
        img[y0:y0 + hh, x0:x0 + ww, :3] = np.clip(ramp(v, colors), 0, 255)
        img[y0:y0 + hh, x0:x0 + ww, 3] = 255 if vis else 0

    white = ["#b8bcc4", "#d8dce2", "#eef0f4"]

    def box(u, v, bw, bh, bd, colors):
        # Standard box UV unwrap: top/bottom row then the four sides.
        fill(u + bd, v, bw, bd, colors)
        fill(u + bd + bw, v, bw, bd, colors)
        fill(u, v + bd, bd, bh, colors)
        fill(u + bd, v + bd, bw, bh, colors)
        fill(u + bd + bw, v + bd, bd, bh, colors)
        fill(u + 2 * bd + bw, v + bd, bw, bh, colors)

    if not leggings:
        box(0, 0, 8, 8, 8, white)          # helmet
        img[10:15, 9:15, :3] = hexc("#e8a020")   # visor (front face at x 8..16, y 8..16)
        img[10, 9:15, :3] = hexc("#ffd070")
        img[8, 8:16, :3] = hexc("#f08a10")
        box(16, 16, 8, 12, 4, white)       # chest
        img[21:23, 20:28, :3] = hexc("#f08a10")
        img[24:26, 22:24, :3] = hexc("#40c0ff")
        box(40, 16, 4, 12, 4, white)       # arms
        img[26:28, 40:56, :3] = hexc("#50545c")
        # Boots share the leg box; only its lower third and sole are painted, the rest stays clear.
        fill(8, 16, 4, 4, ["#303438", "#50545c"])      # sole
        fill(0, 26, 16, 6, white)
        img[30:32, 0:16, :3] = hexc("#404448")
        img[26, 0:16, :3] = hexc("#f08a10")
    else:
        box(16, 16, 8, 12, 4, white)       # belt/waist
        img[26:28, 16:40, :3] = hexc("#50545c")
        box(0, 16, 4, 12, 4, white)        # legs
        img[20:32, 4:5, :3] = hexc("#f08a10")
        img[20:32, 11:12, :3] = hexc("#f08a10")
    return img


# ---------------------------------------------------------------------------------------------- icon

def icon():
    size = 128
    r = rng("icon")
    yy, xx = np.mgrid[0:size, 0:size]
    sky = ramp(np.clip(yy / size * 0.8 + fbm(r, ((4, 0.6), (8, 0.4)), size=size) * 0.4, 0, 1),
               ["#050818", "#0c1030", "#281850", "#402060"])
    img = rgba(sky)
    for _ in range(90):
        y, x = r.randint(0, size, 2)
        b = r.randint(140, 255)
        img[y, x, :3] = (b, b, min(255, b + 20))
    cx, cy, rad = 58, 72, 34
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    band = np.sin((yy - cy) * 0.35 + fbm(r, ((4, 0.5), (8, 0.5)), size=size) * 4) * 0.5 + 0.5
    planet = ramp(band * 0.7 + 0.15, ["#6a2a18", "#b8582c", "#e8a060", "#f8d8a0"])
    lit = np.clip(1.15 - ((xx - cx + 14) ** 2 + (yy - cy + 14) ** 2) ** 0.5 / (rad * 1.9), 0.25, 1.0)
    m = d < rad
    img[m, :3] = np.clip(planet[m] * lit[m][:, None], 0, 255)
    # Ring: an ellipse drawn in two halves so the planet hides the back.
    ring = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(ring)
    dr.ellipse((cx - 58, cy - 14, cx + 58, cy + 14), outline=(230, 210, 170, 255), width=4)
    dr.ellipse((cx - 50, cy - 10, cx + 50, cy + 10), outline=(170, 140, 110, 200), width=2)
    ring_a = np.array(ring)
    front = yy >= cy
    sel = (ring_a[..., 3] > 0) & (front | ~m)
    img[sel] = ring_a[sel]
    # Rocket climbing away, with a flame trail.
    rk = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(rk)
    ox, oy = 100, 30
    dr.line((ox - 30, oy + 40, ox - 4, oy + 8), fill=(255, 180, 60, 150), width=5)
    dr.line((ox - 22, oy + 32, ox - 4, oy + 8), fill=(255, 240, 180, 220), width=2)
    body = [(ox, oy - 14), (ox + 7, oy - 4), (ox + 4, oy + 10), (ox - 6, oy + 12), (ox - 7, oy + 2)]
    dr.polygon(body, fill=(236, 238, 244, 255), outline=(80, 84, 92, 255))
    dr.polygon([(ox - 6, oy + 12), (ox - 13, oy + 16), (ox - 7, oy + 4)], fill=(240, 138, 16, 255))
    dr.polygon([(ox + 4, oy + 10), (ox + 3, oy + 18), (ox + 7, oy + 2)], fill=(240, 138, 16, 255))
    dr.ellipse((ox - 1, oy - 4, ox + 4, oy + 1), fill=(64, 192, 255, 255))
    rk_a = np.array(rk)
    sel = rk_a[..., 3] > 0
    a = rk_a[sel][:, 3:4] / 255.0
    img[sel, :3] = np.clip(img[sel, :3] * (1 - a) + rk_a[sel][:, :3] * a, 0, 255)
    return img
