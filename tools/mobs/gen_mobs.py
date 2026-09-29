#!/usr/bin/env python3
"""Generates every creature asset Galaxy MC needs from the bestiary in species.py.

For each species:
  * geometry from its body plan (proportions jittered per species, element adornments added)
  * a box-UV layout packed into the smallest power-of-two texture that fits
  * three textures painted straight onto that layout: the hide (gradients, family pattern, eyes and
    mouth on the head's front face), a white "markings" mask that the client tints per planet strain,
    and a full-bright eyes layer
  * data/galaxy_mc/species.json (stats, behaviour, habitat) read by the mod at start-up
  * a model JSON read by the client model loader, loot table, spawn egg (item model, definition and a
    painted egg texture) and lang entries

    python3 tools/mobs/gen_mobs.py
"""
import json
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bodyplans  # noqa: E402
from species import expand  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "galaxy_mc")
DATA = os.path.join(RES, "data", "galaxy_mc")

PATTERNS = {
    "stalker": "stripes", "grazer": "spots", "hound": "stripes", "ramhorn": "plates", "behemoth": "plates", "titan": "plates",
    "hopper": "spots", "beetle": "shell", "mantis": "stripes", "drone": "rings", "weaver": "chevrons", "scorpion": "rings",
    "grey": "none", "golem": "cracks", "raptor": "stripes", "strider": "spots", "brute": "fur", "crab": "shell", "ooze": "bubbles",
    "glider": "veins", "skyray": "spots", "wyvern": "scales", "jelly": "bubbles", "moth": "eyespots", "eye": "veins",
    "sandworm": "rings", "burrower": "rings", "serpent": "chevrons", "centipede": "rings", "eel": "stripes", "spitter": "veins",
    "myconid": "spots", "shardling": "facets", "mimic": "cracks", "tortoise": "shell", "stag": "spots", "runner": "stripes",
    "spikeback": "plates", "hydra": "scales", "horror": "veins",
}


def hexs(c):
    return "#%06x" % c


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


# ---------------------------------------------------------------------------------------------- geometry

def jitter(params, rng):
    """Per-species proportion wobble so two members of a family never share a silhouette."""
    out = {}
    for k, v in params.items():
        if isinstance(v, bool) or not isinstance(v, (int, float)) or k in ("leg_pairs", "heads", "tentacles", "stalks", "mandibles"):
            out[k] = v
        elif k in ("lean",):
            out[k] = v * rng.uniform(0.9, 1.1)
        else:
            out[k] = max(1, round(v * rng.uniform(0.85, 1.18)))
    return out


def geometry(s, rng):
    params = jitter(s["params"], rng)
    if s["plan"] == "worm":
        head = bodyplans.worm_head(params)
        seg = bodyplans.worm_segment(dict(params, legs=s["worm"].get("legs"), ridge=s["worm"].get("ridge")))
        return bodyplans.adorn(head, s["element"], rng), seg, params
    parts = bodyplans.PLANS[s["plan"]](params)
    return bodyplans.adorn(parts, s["element"], rng), None, params


def uv_size(size):
    w, h, d = size
    return 2 * (w + d), h + d


def pack(parts):
    """Shelf-packs every cube's box-UV rectangle; returns (tex_w, tex_h)."""
    cubes = [c for p in parts for c in p["cubes"]]
    order = sorted(cubes, key=lambda c: -uv_size(c["size"])[1])
    for tw in (64, 128, 256, 512):
        x = y = row = 0
        ok = True
        for c in order:
            w, h = uv_size(c["size"])
            if w > tw:
                ok = False
                break
            if x + w > tw:
                x, y, row = 0, y + row, 0
            c["uv"] = [x, y]
            x += w
            row = max(row, h)
        if ok:
            th = y + row
            size = 32
            while size < th:
                size *= 2
            return tw, max(size, 32)
    raise ValueError("model too large to pack")


def bounds(parts):
    """Approximate model-space bounds (ignores rotation) as (minx, miny, minz, maxx, maxy, maxz)."""
    by_name = {p["name"]: p for p in parts}

    def abs_pivot(p):
        x, y, z = p["pivot"]
        parent = p["parent"]
        while parent != "root" and parent in by_name:
            q = by_name[parent]
            x, y, z = x + q["pivot"][0], y + q["pivot"][1], z + q["pivot"][2]
            parent = q["parent"]
        return x, y, z

    lo = [1e9, 1e9, 1e9]
    hi = [-1e9, -1e9, -1e9]
    for p in parts:
        px, py, pz = abs_pivot(p)
        for c in p["cubes"]:
            o, sz = c["origin"], c["size"]
            for i, base in enumerate((px, py, pz)):
                lo[i] = min(lo[i], base + o[i])
                hi[i] = max(hi[i], base + o[i] + sz[i])
    return lo, hi


# ---------------------------------------------------------------------------------------------- painting

def faces(c):
    """Box-UV face rectangles for a cube: name -> (x, y, w, h)."""
    u, v = c["uv"]
    w, h, d = c["size"]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


class Painter:
    def __init__(self, tw, th, s, rng):
        self.hide = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        self.marks = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        self.eyes = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        self.s = s
        self.rng = rng
        p, sec, acc, eye = s["colours"]
        # Each species nudges its family colours so variants of one element still differ.
        hue_shift = rng.uniform(0.9, 1.1)
        self.primary = shade(rgb(p), hue_shift)
        self.secondary = shade(rgb(sec), rng.uniform(0.92, 1.08))
        self.accent = rgb(acc)
        self.eye = rgb(eye)
        self.pattern = PATTERNS.get(s["family"], "spots")

    def px(self, img, x, y, c, a=255):
        if 0 <= x < img.width and 0 <= y < img.height:
            img.putpixel((x, y), (c[0], c[1], c[2], a))

    def cube(self, c, part_name, role, is_eye_cube):
        big = max(c["size"]) >= 5 and role not in ("leg", "leg_side", "tentacle", "antenna")
        for fname, (x, y, w, h) in faces(c).items():
            for j in range(h):
                for i in range(w):
                    t = j / max(1, h - 1)
                    if fname == "top":
                        base = shade(self.primary, 0.85)
                    elif fname == "bottom":
                        base = self.secondary
                    else:
                        base = mix(self.primary, self.secondary, t * 0.7)
                    n = self.rng.uniform(0.9, 1.08)
                    col = shade(base, n)
                    mark = big and self.marked(fname, i, j, w, h, x + i, y + j)
                    if mark:
                        col = mix(col, self.accent, 0.75)
                        self.px(self.marks, x + i, y + j, (255, 255, 255), 255)
                    self.px(self.hide, x + i, y + j, col)
            if fname == "front" and is_eye_cube:
                self.face(x, y, w, h)

    def marked(self, fname, i, j, w, h, gx, gy):
        p = self.pattern
        r = self.rng
        if p == "stripes":
            return fname in ("east", "west", "top") and (i + (j // 3)) % 4 == 0
        if p == "spots":
            return fname in ("east", "west", "top", "back") and r.random() < 0.09
        if p == "plates":
            return fname in ("top", "east", "west") and (i % 4 == 0 or j % 4 == 0)
        if p == "shell":
            return fname == "top" and (abs(i - w / 2) < 0.6 or (i + j) % 5 == 0)
        if p == "rings":
            return fname != "bottom" and j % 3 == 0
        if p == "chevrons":
            return fname in ("top", "back") and (i + abs(j - h / 2)) % 4 < 1
        if p == "cracks":
            return r.random() < 0.05 or (fname != "bottom" and (i * 7 + j * 3) % 11 == 0)
        if p == "fur":
            return fname != "bottom" and r.random() < 0.18
        if p == "bubbles":
            return r.random() < 0.07
        if p == "veins":
            return fname != "bottom" and ((i * 3 + j * 5) % 9 == 0 or r.random() < 0.03)
        if p == "scales":
            return fname != "bottom" and (i + (j % 2)) % 2 == 0 and j % 2 == 0
        if p == "eyespots":
            return fname == "top" and ((i - w * 0.3) ** 2 + (j - h * 0.5) ** 2 < 2.5 or (i - w * 0.7) ** 2 + (j - h * 0.5) ** 2 < 2.5)
        if p == "facets":
            return (i + j) % 3 == 0
        return False

    def face(self, x, y, w, h):
        """Eyes and a mouth on a head's front face; eyes also go to the glow layer."""
        s = self.s
        dark = (10, 8, 12)
        if s["plan"] == "eye":
            cx, cy = x + w / 2 - 0.5, y + h / 2 - 0.5
            rad = min(w, h) * 0.42
            for j in range(h):
                for i in range(w):
                    d = math.hypot(x + i - cx, y + j - cy)
                    if d <= rad:
                        c = self.eye if d > rad * 0.35 else dark
                        self.px(self.hide, x + i, y + j, c)
                        if d > rad * 0.35:
                            self.px(self.eyes, x + i, y + j, self.eye)
            return
        count = 2
        if s["element"] == "void" or s["family"] in ("weaver",):
            count = 4
        if s["family"] in ("grey",):
            count = 2
        row = y + max(0, int(h * 0.3))
        big = s["family"] == "grey"
        if w <= 2:
            positions = [x]
        else:
            positions = [x + int(w * (k + 1) / (count + 1)) for k in range(count)] if count > 2 else [x + max(0, int(w * 0.25)), x + min(w - 1, int(w * 0.72))]
        for ex in positions:
            size = 2 if big and h >= 5 else 1
            for dy in range(size):
                for dx in range(size):
                    colour = dark if big else self.eye
                    self.px(self.hide, ex + dx - (size - 1), row + dy, colour)
                    if not big:
                        self.px(self.eyes, ex + dx - (size - 1), row + dy, self.eye)
            if count > 2:
                self.px(self.hide, ex, row + 2, self.eye)
                self.px(self.eyes, ex, row + 2, self.eye)
        if h >= 4 and w >= 3:
            my = y + h - 2
            for i in range(max(1, w // 4), w - max(1, w // 4)):
                self.px(self.hide, x + i, my, shade(self.accent, 0.5))


def paint(parts, tw, th, s, rng):
    painter = Painter(tw, th, s, rng)
    for p in parts:
        role = p["anim"]["role"] if p.get("anim") else None
        face_index = p.get("face")
        for k, c in enumerate(p["cubes"]):
            painter.cube(c, p["name"], role, face_index is not None and k == face_index)
    return painter


def egg(s, path):
    """A 16x16 spawn egg in the species' colours with spots."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p, sec, acc, eye = (rgb(c) for c in s["colours"])
    rng = random.Random(s["id"])
    for y in range(16):
        for x in range(16):
            dx = (x - 7.5) / 5.6
            dy = (y - 8.8) / 7.0 if y > 8 else (y - 8.8) / 7.6
            d = dx * dx + dy * dy
            if d <= 1.0:
                c = shade(p, 1.1 - 0.3 * d + (0.12 if x < 7 and y < 7 else 0))
                if rng.random() < 0.16:
                    c = sec
                if d > 0.82:
                    c = shade(c, 0.6)
                img.putpixel((x, y), c + (255,))
    img.save(path)


# ---------------------------------------------------------------------------------------------- output

def model_json(parts, tw, th):
    out = []
    for p in parts:
        out.append({
            "name": p["name"], "parent": p["parent"], "pivot": [round(v, 3) for v in p["pivot"]],
            "rot": [round(v, 4) for v in p["rot"]],
            "cubes": [{"uv": c["uv"], "origin": [round(v, 3) for v in c["origin"]], "size": c["size"], "inflate": c["inflate"]}
                      for c in p["cubes"]],
            "anim": p["anim"],
        })
    return {"texture": [tw, th], "parts": out}


def loot(s):
    pools = []
    drops = s.get("drops", [])
    giant = s.get("giant") or s.get("scale", 1) >= 2.5
    for i, item in enumerate(drops):
        rare = item in ("titan_core", "worldeater_fang", "leviathan_scale")
        if rare and not giant:
            continue
        lo, hi = (1, 3) if giant else (0, 2 if i == 0 else 1)
        if giant and not rare:
            lo, hi = 2, 6
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"galaxy_mc:{item}",
                                                "functions": [{"function": "minecraft:set_count",
                                                               "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}]}]})
    if "xeno_meat" in drops:
        pools[drops.index("xeno_meat")]["entries"][0]["functions"].append(
            {"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties", "entity": "this",
                                                                     "predicate": {"flags": {"is_on_fire": True}}}]})
        pools[drops.index("xeno_meat")]["entries"][0]["name"] = "galaxy_mc:xeno_meat"
    return {"type": "minecraft:entity", "pools": pools, "random_sequence": f"galaxy_mc:entities/{s['id']}"}


def species_entry(s, width, height, tex, seg_tex):
    giant = bool(s.get("giant")) or s.get("scale", 1.0) >= 2.5
    scale = float(s.get("scale", 1.0))
    fire = s["element"] in ("ember", "magma", "ash") or s["id"] == "lava_eel"
    entry = {
        "id": s["id"], "name": s["name"], "family": s["family"], "element": s["element"], "effect": s["effect"],
        "plan": s["plan"], "kind": s["kind"], "temper": s.get("temper", "neutral"), "attack": s.get("attack", "melee"),
        "spitRange": s.get("spit_range", 0), "leap": bool(s.get("leap")), "charge": bool(s.get("charge")),
        "climb": bool(s.get("climb")), "swim": bool(s.get("swim")), "hops": bool(s.get("hops")), "ambush": bool(s.get("ambush")),
        "giant": giant, "scale": scale, "width": round(width, 3), "height": round(height, 3),
        "health": s["health"], "damage": s["damage"], "speed": s["speed"], "flySpeed": s.get("fly", 0.0), "armor": s["armor"],
        "knockbackResistance": 1.0 if giant else round(min(0.8, s["armor"] / 20.0), 2),
        "followRange": 64 if giant else 32, "stepHeight": round(1.0 + (scale - 1) * 0.5, 2) if giant else 1.0,
        "group": list(s.get("group", (1, 2))), "weight": s.get("weight", 5), "minTier": s.get("min_tier", 1),
        "climates": s["climates"], "types": s.get("types", []), "sol": s.get("sol", []), "voice": s.get("voice", "silverfish"),
        "colours": {"primary": hexs(s["colours"][0]), "secondary": hexs(s["colours"][1]), "accent": hexs(s["colours"][2]),
                    "eyes": hexs(s["colours"][3])},
        "fireImmune": fire, "texture": tex,
    }
    if s["kind"] == "worm":
        w = dict(s["worm"])
        w["segmentTexture"] = seg_tex
        entry["worm"] = w
    return entry


def write_json(path, obj, compact=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        if compact:
            json.dump(obj, f, separators=(",", ":"))
        else:
            json.dump(obj, f, indent=2)
            f.write("\n")


def merge_lang(entries):
    path = os.path.join(ASSETS, "lang", "en_us.json")
    lang = {}
    if os.path.exists(path):
        with open(path) as f:
            lang = json.load(f)
    lang.update(entries)
    write_json(path, dict(sorted(lang.items())))


def main():
    species = expand()
    table = []
    lang = {}
    for s in species:
        rng = random.Random("galaxy_mc/" + s["id"])
        parts, seg_parts, params = geometry(s, rng)
        tw, th = pack(parts)
        lo, hi = bounds(parts)
        # Minecraft hitboxes are square, so long bodies use their girth rather than their length.
        width = max(0.35, min(max(hi[0] - lo[0], (hi[2] - lo[2]) * 0.55) / 16.0, 2.6))
        height = max(0.3, (24 - lo[1]) / 16.0)
        if s["kind"] == "worm":
            width = height = params["seg"] / 16.0
        tex = f"textures/entity/creature/{s['id']}"
        painter = paint(parts, tw, th, s, rng)
        base = os.path.join(ASSETS, "textures", "entity", "creature")
        os.makedirs(base, exist_ok=True)
        painter.hide.save(os.path.join(base, f"{s['id']}.png"))
        painter.marks.save(os.path.join(base, f"{s['id']}_markings.png"))
        painter.eyes.save(os.path.join(base, f"{s['id']}_eyes.png"))
        write_json(os.path.join(ASSETS, "creature_models", f"{s['id']}.json"), model_json(parts, tw, th), compact=True)
        seg_tex = None
        if seg_parts is not None:
            sw, sh = pack(seg_parts)
            sp = paint(seg_parts, sw, sh, s, random.Random("galaxy_mc/seg/" + s["id"]))
            sp.hide.save(os.path.join(base, f"{s['id']}_segment.png"))
            sp.marks.save(os.path.join(base, f"{s['id']}_segment_markings.png"))
            seg_tex = f"textures/entity/creature/{s['id']}_segment"
            write_json(os.path.join(ASSETS, "creature_models", f"{s['id']}_segment.json"), model_json(seg_parts, sw, sh), compact=True)
        table.append(species_entry(s, width, height, tex, seg_tex))
        # Loot, spawn egg, lang.
        write_json(os.path.join(DATA, "loot_table", "entities", f"{s['id']}.json"), loot(s))
        egg_dir = os.path.join(ASSETS, "textures", "item", "spawn_egg")
        os.makedirs(egg_dir, exist_ok=True)
        egg(s, os.path.join(egg_dir, f"{s['id']}.png"))
        write_json(os.path.join(ASSETS, "models", "item", f"{s['id']}_spawn_egg.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"galaxy_mc:item/spawn_egg/{s['id']}"}})
        write_json(os.path.join(ASSETS, "items", f"{s['id']}_spawn_egg.json"),
                   {"model": {"type": "minecraft:model", "model": f"galaxy_mc:item/{s['id']}_spawn_egg"}})
        lang[f"entity.galaxy_mc.{s['id']}"] = s["name"]
        lang[f"item.galaxy_mc.{s['id']}_spawn_egg"] = f"{s['name']} Spawn Egg"
    write_json(os.path.join(DATA, "species.json"), {"species": table}, compact=False)
    lang["entity.galaxy_mc.worm_segment"] = "Worm Segment"
    merge_lang(lang)
    giants = sum(1 for t in table if t["giant"])
    print(f"{len(table)} species ({giants} giants, {sum(1 for t in table if t['kind'] == 'worm')} segmented)")


if __name__ == "__main__":
    main()
