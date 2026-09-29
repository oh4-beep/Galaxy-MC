"""Body plans: procedural creature geometry in Minecraft entity-model space.

Model space is the one vanilla entity models use: units are 1/16 block, y grows downwards and the
ground is at y = 24; -z is the creature's front. Every plan is a function of a parameter dict so that
two species of one family differ in proportions, and element adornments (spikes, horns, crests, sacs,
crystal shards, tendrils) are bolted on afterwards.

A part is a dict:
    name, parent, pivot (x, y, z), rot (radians), cubes [(origin, size, inflate)],
    anim {role, phase, amp, speed}, face (which cube face carries the eyes, for the painter)
"""
import math


def part(name, pivot, cubes, parent="root", rot=(0.0, 0.0, 0.0), role=None, phase=0.0, amp=1.0, speed=1.0, **extra):
    p = {"name": name, "parent": parent, "pivot": list(pivot), "rot": list(rot), "cubes": [], "anim": None}
    for c in cubes:
        origin, size = c[0], c[1]
        inflate = c[2] if len(c) > 2 else 0.0
        p["cubes"].append({"origin": [float(v) for v in origin], "size": [max(1, int(round(v))) for v in size],
                           "inflate": float(inflate)})
    if role:
        p["anim"] = {"role": role, "phase": phase, "amp": amp, "speed": speed}
    p.update(extra)
    return p


def box(cx, cy, cz, w, h, d, inflate=0.0):
    """Cube centred on (cx, cy, cz) relative to its part's pivot."""
    w, h, d = max(1, round(w)), max(1, round(h)), max(1, round(d))
    return ((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), inflate)


# ---------------------------------------------------------------------------------------------- plans

def quadruped(p):
    """Four (or six) legged walker: grazers, stalkers, hounds, behemoths, stags, tortoises."""
    L, W, H = p["body_len"], p["body_w"], p["body_h"]
    leg, lw = p["leg_len"], p["leg_w"]
    pairs = p.get("leg_pairs", 2)
    body_y = 24 - leg - H / 2
    parts = [part("body", (0, body_y, 0), [box(0, 0, 0, W, H, L)], role="body", amp=0.3)]
    hs = p["head"]
    neck = p.get("neck", 0)
    head_y = body_y - H / 2 + hs / 2 - neck * 0.6 + p.get("head_drop", 0)
    head_z = -L / 2 - hs / 2 + 1 - neck * 0.5
    if neck:
        parts.append(part("neck", (0, -H / 4, -L / 2 + 1), [box(0, -neck / 2, -neck / 2, hs * 0.55, neck, hs * 0.55)],
                          parent="body", rot=(0.6, 0, 0)))
    snout = p.get("snout", 0)
    head_cubes = [box(0, 0, 0, hs * p.get("head_w", 1.0), hs, hs)]
    if snout:
        head_cubes.append(box(0, hs * 0.15, -hs / 2 - snout / 2, hs * 0.6, hs * 0.55, snout))
    parts.append(part("head", (0, head_y, head_z), head_cubes, role="head", face=0))
    if p.get("jaw"):
        parts.append(part("jaw", (0, hs * 0.35, -hs / 2 + 1), [box(0, hs * 0.1, -snout / 2 - 1, hs * 0.55, hs * 0.2, max(2, snout + hs * 0.5))],
                          parent="head", role="jaw"))
    xs = W / 2 - lw / 2
    for i in range(pairs):
        z = -L / 2 + lw / 2 + 1 + i * (L - lw - 2) / max(1, pairs - 1)
        for side in (-1, 1):
            phase = (i % 2) * math.pi + (0 if side < 0 else math.pi)
            parts.append(part(f"leg_{i}_{'l' if side < 0 else 'r'}", (side * xs, 24 - leg, z),
                              [box(0, leg / 2, 0, lw, leg, lw)], role="leg", phase=phase, amp=p.get("stride", 1.0)))
    tail = p.get("tail", 0)
    if tail:
        parts.append(part("tail", (0, -H / 4, L / 2), [box(0, 0, tail / 2, max(1, W * 0.2), max(1, H * 0.2), tail)],
                          parent="body", rot=(-0.4, 0, 0), role="tail", amp=0.4))
    if p.get("shell"):
        sh = p["shell"]
        parts.append(part("shell", (0, -H / 2, 0), [box(0, -sh / 2, 0, W + 4, sh, L + 2)], parent="body"))
    return parts


def biped(p):
    """Upright two-legged: greys, golems, brutes, raptors (leaning), runners."""
    H, W, D = p["body_h"], p["body_w"], p["body_d"]
    leg, lw = p["leg_len"], p["leg_w"]
    lean = p.get("lean", 0.0)
    body_y = 24 - leg - H / 2
    parts = [part("body", (0, 24 - leg, 0), [box(0, -H / 2, 0, W, H, D)], rot=(lean, 0, 0), role="body", amp=0.2)]
    hs = p["head"]
    parts.append(part("head", (0, -H, -D * 0.2 - lean * H * 0.6), [box(0, -hs / 2, -hs * 0.1, hs * p.get("head_w", 1.0), hs, hs)],
                      parent="body", rot=(-lean, 0, 0), role="head", face=0))
    snout = p.get("snout", 0)
    if snout:
        parts.append(part("snout", (0, -hs * 0.35, -hs * 0.6), [box(0, 0, -snout / 2, hs * 0.55, hs * 0.45, snout)], parent="head"))
    if p.get("jaw"):
        parts.append(part("jaw", (0, -hs * 0.1, -hs * 0.5), [box(0, 0, -max(2, snout) / 2, hs * 0.5, 2, max(2, snout))],
                          parent="head", role="jaw"))
    arm, aw = p.get("arm_len", 0), p.get("arm_w", 2)
    if arm:
        for side in (-1, 1):
            parts.append(part(f"arm_{'l' if side < 0 else 'r'}", (side * (W / 2 + aw / 2), -H + 1.5, 0),
                              [box(0, arm / 2 - 1, 0, aw, arm, aw)], parent="body", rot=(-lean, 0, side * 0.08),
                              role="arm", phase=0 if side < 0 else math.pi, amp=0.8))
    for side in (-1, 1):
        parts.append(part(f"leg_{'l' if side < 0 else 'r'}", (side * (W / 2 - lw / 2), 24 - leg, 0),
                          [box(0, leg / 2, 0, lw, leg, lw)], role="leg", phase=0 if side < 0 else math.pi, amp=1.0))
    tail = p.get("tail", 0)
    if tail:
        parts.append(part("tail", (0, -1, D / 2), [box(0, 0, tail / 2, 3, 3, tail)], parent="body", rot=(0.3, 0, 0), role="tail", amp=0.4))
    return parts


def insect(p):
    """Hexapods: beetles, mantises, drones, scorpions (with stinger), centipedes use worm."""
    L, W, H = p["body_len"], p["body_w"], p["body_h"]
    leg, lw = p["leg_len"], 1
    body_y = 24 - leg * 0.6 - H / 2
    parts = [part("thorax", (0, body_y, 0), [box(0, 0, 0, W, H, L * 0.45)])]
    parts.append(part("abdomen", (0, 0, L * 0.22), [box(0, -H * 0.1, L * 0.3, W * 1.15, H * 1.1, L * 0.6)], parent="thorax",
                      role="tail", amp=0.08))
    hs = p["head"]
    parts.append(part("head", (0, 0, -L * 0.22), [box(0, 0, -hs / 2, hs * 1.1, hs * 0.9, hs)], parent="thorax", role="head", face=0))
    if p.get("mandibles"):
        for side in (-1, 1):
            parts.append(part(f"mandible_{side}", (side * hs * 0.3, hs * 0.2, -hs), [box(0, 0, -1.5, 1, 1, 3)], parent="head",
                              rot=(0, side * 0.4, 0), role="jaw", amp=0.5))
    if p.get("blades"):
        for side in (-1, 1):
            parts.append(part(f"blade_{side}", (side * W / 2, -H * 0.2, -L * 0.2), [box(0, 4, -1, 1, 8, 2), box(0, 8, -3, 1, 1, 5)],
                              parent="thorax", rot=(-0.9, 0, 0), role="arm", phase=0 if side < 0 else math.pi, amp=0.6))
    for i in range(3):
        z = -L * 0.2 + i * L * 0.2
        for side in (-1, 1):
            phase = (i % 2) * math.pi + (0 if side < 0 else math.pi)
            parts.append(part(f"leg_{i}_{side}", (side * W / 2, body_y + H * 0.2, z),
                              [box(side * leg / 2, 0, 0, leg, lw, lw)], rot=(0, 0, side * 0.6), role="leg_side", phase=phase, amp=0.5))
    if p.get("stinger"):
        parts.append(part("stinger", (0, -H * 0.3, L * 0.55), [box(0, -3, 1, 2, 6, 2), box(0, -7, -1, 1, 3, 3)], parent="abdomen",
                          rot=(0.9, 0, 0), role="tail", amp=0.3))
    if p.get("antennae"):
        for side in (-1, 1):
            parts.append(part(f"antenna_{side}", (side * hs * 0.3, -hs * 0.4, -hs), [box(0, -3, -1, 1, 6, 1)], parent="head",
                              rot=(-0.5, 0, side * 0.3), role="antenna", phase=side))
    return parts


def arachnid(p):
    """Eight-legged: weavers and similar."""
    L, W, H = p["body_len"], p["body_w"], p["body_h"]
    leg = p["leg_len"]
    body_y = 24 - leg * 0.55 - H / 2
    parts = [part("cephalo", (0, body_y, -L * 0.2), [box(0, 0, 0, W * 0.8, H * 0.8, L * 0.4)])]
    parts.append(part("abdomen", (0, -1, L * 0.2), [box(0, 0, L * 0.3, W * 1.3, H * 1.2, L * 0.6)], parent="cephalo", role="tail", amp=0.06))
    hs = p["head"]
    parts.append(part("head", (0, 0, -L * 0.2), [box(0, 0, -hs / 2, hs, hs * 0.8, hs)], parent="cephalo", role="head", face=0))
    for i in range(4):
        z = -L * 0.3 + i * L * 0.12
        for side in (-1, 1):
            phase = (i % 2) * math.pi + (0 if side < 0 else math.pi)
            parts.append(part(f"leg_{i}_{side}", (side * W * 0.4, body_y, z - L * 0.2),
                              [box(side * leg / 2, 0, 0, leg, 2, 2)], rot=(0, side * (0.7 - i * 0.45), side * 0.75),
                              role="leg_side", phase=phase, amp=0.4))
    return parts


def crab(p):
    L, W, H = p["body_len"], p["body_w"], p["body_h"]
    leg = p["leg_len"]
    body_y = 24 - leg * 0.6 - H / 2
    parts = [part("body", (0, body_y, 0), [box(0, 0, 0, W, H, L)], role="body", amp=0.2)]
    for side in (-1, 1):
        parts.append(part(f"eyestalk_{side}", (side * W * 0.2, -H / 2, -L / 2 + 1), [box(0, -2, 0, 1, 4, 1), box(0, -4, 0, 2, 2, 2)],
                          parent="body", role="antenna", phase=side, face=1))
        cl = p["claw"]
        parts.append(part(f"claw_{side}", (side * W / 2, 0, -L / 2), [box(side * cl * 0.3, 0, -cl * 0.5, cl * 0.6, cl * 0.6, cl)],
                          parent="body", rot=(0, -side * 0.5, 0), role="arm", phase=0 if side < 0 else math.pi, amp=0.3))
        for i in range(3):
            parts.append(part(f"leg_{i}_{side}", (side * W / 2, body_y + H * 0.2, -L * 0.25 + i * L * 0.25),
                              [box(side * leg / 2, 0, 0, leg, 2, 2)], rot=(0, 0, side * 0.7), role="leg_side",
                              phase=(i % 2) * math.pi + side, amp=0.4))
    parts.append(part("head", (0, -H / 2 + 1, -L / 2), [box(0, 0, -1, W * 0.4, 2, 2)], parent="body", role="head"))
    return parts


def blob(p):
    """Oozes: a gelatinous cube with a visible core; bounces."""
    s = p["size"]
    parts = [part("body", (0, 24 - s / 2, 0), [box(0, 0, 0, s, s, s)], role="bounce", amp=1.0)]
    parts.append(part("core", (0, 0, 0), [box(0, 0, 0, s * 0.45, s * 0.45, s * 0.45)], parent="body", role="spin", speed=0.4))
    parts.append(part("head", (0, -s * 0.15, -s / 2 + 0.5), [box(0, 0, 0, s * 0.6, s * 0.25, 1)], parent="body", face=0))
    return parts


def flyer(p):
    """Winged: gliders (bat-like), wyverns, moths."""
    L, W, H = p["body_len"], p["body_w"], p["body_h"]
    span = p["span"]
    parts = [part("body", (0, 12, 0), [box(0, 0, 0, W, H, L)], role="hover", amp=1.0)]
    hs = p["head"]
    parts.append(part("head", (0, -H * 0.2, -L / 2), [box(0, 0, -hs / 2, hs, hs, hs)], parent="body", role="head", face=0))
    snout = p.get("snout", 0)
    if snout:
        parts.append(part("snout", (0, hs * 0.1, -hs), [box(0, 0, -snout / 2, hs * 0.5, hs * 0.4, snout)], parent="head"))
    chord = p.get("chord", L * 0.6)
    for side in (-1, 1):
        # `span` is the full wingspan: each side is an inner wing and a tip, half the span in total.
        inner, outer = span * 0.28, span * 0.22
        parts.append(part(f"wing_{side}", (side * W / 2, -H * 0.3, -L * 0.1), [box(side * inner / 2, 0, chord * 0.2, inner, 1, chord)],
                          parent="body", role="wing", phase=0, amp=side))
        parts.append(part(f"wingtip_{side}", (side * inner, 0, 0), [box(side * outer / 2, 0, chord * 0.35, outer, 1, chord * 0.7)],
                          parent=f"wing_{side}", role="wing", phase=0.6, amp=side * 0.7))
    tail = p.get("tail", 0)
    if tail:
        parts.append(part("tail", (0, 0, L / 2), [box(0, 0, tail / 2, 2, 2, tail)], parent="body", role="tail", amp=0.3))
    if p.get("legs"):
        for side in (-1, 1):
            parts.append(part(f"leg_{side}", (side * W * 0.3, H / 2, L * 0.2), [box(0, 2, 0, 1, 4, 1)], parent="body", rot=(0.8, 0, 0)))
    return parts


def ray(p):
    """Mantas: a flat diamond body with rippling fins."""
    L, span = p["body_len"], p["span"]
    parts = [part("body", (0, 14, 0), [box(0, 0, 0, span * 0.35, 3, L)], role="hover", amp=0.6)]
    for side in (-1, 1):
        parts.append(part(f"wing_{side}", (side * span * 0.17, 0, 0), [box(side * span * 0.12, 0, 1, span * 0.24, 1, L * 0.8)],
                          parent="body", role="wing", amp=side * 0.6, speed=0.4))
        parts.append(part(f"wingtip_{side}", (side * span * 0.24, 0, 1), [box(side * span * 0.05, 0, 2, span * 0.1, 1, L * 0.4)],
                          parent=f"wing_{side}", role="wing", phase=0.8, amp=side * 0.5, speed=0.4))
    parts.append(part("head", (0, 0, -L / 2), [box(0, 0, -1, span * 0.2, 2, 3)], parent="body", face=0))
    parts.append(part("tail", (0, 0, L / 2), [box(0, 0, L * 0.4, 1, 1, L * 0.8)], parent="body", role="tail", amp=0.3))
    return parts


def jelly(p):
    s = p["size"]
    parts = [part("body", (0, 24 - p["tentacle"] - s, 0), [box(0, -s / 2, 0, s, s, s), box(0, s * 0.1, 0, s + 2, 2, s + 2)],
                  role="hover", amp=1.5)]
    n = p.get("tentacles", 8)
    for i in range(n):
        a = i * 2 * math.pi / n
        x, z = math.cos(a) * s * 0.35, math.sin(a) * s * 0.35
        parts.append(part(f"tentacle_{i}", (x, s * 0.2, z), [box(0, p["tentacle"] / 2, 0, 1, p["tentacle"], 1)], parent="body",
                          role="tentacle", phase=a, amp=0.35))
    parts.append(part("head", (0, -s * 0.2, -s / 2), [box(0, 0, 0, s * 0.5, s * 0.3, 1)], parent="body", face=0))
    return parts


def floater_eye(p):
    s = p["size"]
    parts = [part("head", (0, 12, 0), [box(0, 0, 0, s, s, s)], role="head", face=0)]
    parts[0]["anim"] = {"role": "head_hover", "phase": 0.0, "amp": 1.0, "speed": 1.0}
    for i in range(p.get("stalks", 4)):
        a = i * 2 * math.pi / p.get("stalks", 4)
        parts.append(part(f"stalk_{i}", (math.cos(a) * s * 0.4, s * 0.4, math.sin(a) * s * 0.4 + s * 0.3),
                          [box(0, 3, 0, 1, 6, 1)], parent="head", role="tentacle", phase=a, amp=0.4))
    return parts


def tripod(p):
    """Towering three-legged striders."""
    s, leg = p["body"], p["leg_len"]
    parts = [part("body", (0, 24 - leg - s / 2, 0), [box(0, 0, 0, s, s * 0.6, s)], role="body", amp=0.5)]
    parts.append(part("head", (0, -s * 0.2, -s / 2), [box(0, 0, -2, s * 0.4, s * 0.3, 4)], parent="body", role="head", face=0))
    for i in range(3):
        a = i * 2 * math.pi / 3 + math.pi / 2
        parts.append(part(f"leg_{i}", (math.cos(a) * s * 0.3, 24 - leg, math.sin(a) * s * 0.3),
                          [box(0, leg / 2, 0, 2, leg, 2)], rot=(math.sin(a) * 0.25, 0, -math.cos(a) * 0.25), role="leg",
                          phase=i * 2 * math.pi / 3, amp=0.5))
    return parts


def plant(p):
    """Rooted spitters: a stalk with a bulbous head and leaves."""
    h, hs = p["stalk"], p["head"]
    parts = [part("stalk", (0, 24, 0), [box(0, -h / 2, 0, 3, h, 3)], role="sway", amp=0.15)]
    parts.append(part("head", (0, -h, 0), [box(0, -hs / 2, 0, hs, hs, hs)], parent="stalk", role="head", face=0))
    parts.append(part("jaw", (0, -hs * 0.2, -hs / 2), [box(0, 0, -1.5, hs * 0.8, 2, 3)], parent="head", role="jaw"))
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        parts.append(part(f"leaf_{i}", (0, 23, 0), [box(math.cos(a) * 3.5, 0, math.sin(a) * 3.5, 6, 1, 3)], rot=(0, a, 0.2),
                          role="sway", phase=i, amp=0.2))
    return parts


def mushroom(p):
    h, cap = p["stem"], p["cap"]
    leg = p.get("leg_len", 4)
    parts = [part("stem", (0, 24 - leg, 0), [box(0, -h / 2, 0, 5, h, 5)], role="body", amp=0.3)]
    parts.append(part("head", (0, -h, 0), [box(0, -3, 0, cap, 6, cap), box(0, -7, 0, cap * 0.6, 3, cap * 0.6)], parent="stem",
                      role="sway", amp=0.1, face=0))
    for side in (-1, 1):
        parts.append(part(f"leg_{side}", (side * 1.5, 24 - leg, 0), [box(0, leg / 2, 0, 2, leg, 2)], role="leg",
                          phase=0 if side < 0 else math.pi))
        parts.append(part(f"arm_{side}", (side * 3, -h * 0.7, 0), [box(side * 1, 3, 0, 1, 6, 1)], parent="stem", role="arm",
                          phase=0 if side < 0 else math.pi, amp=0.5))
    return parts


def sprite(p):
    s = p["size"]
    parts = [part("head", (0, 14, 0), [box(0, 0, 0, s, s * 1.6, s)], face=0)]
    parts[0]["anim"] = {"role": "head_hover", "phase": 0.0, "amp": 1.0, "speed": 1.0}
    for i in range(3):
        a = i * 2 * math.pi / 3
        parts.append(part(f"shard_{i}", (0, 0, 0), [box(math.cos(a) * s * 1.2, 0, math.sin(a) * s * 1.2, 2, s, 2)], parent="head",
                          role="spin", speed=1.2 + i * 0.3))
    return parts


def mimic(p):
    s = p["size"]
    parts = [part("body", (0, 24 - s / 2, 0), [box(0, 0, 0, s, s * 0.8, s)])]
    parts.append(part("head", (0, -s * 0.1, -s / 2), [box(0, 0, -1, s * 0.7, s * 0.25, 2)], parent="body", role="jaw", amp=0.6, face=0))
    for i in range(4):
        side = -1 if i % 2 else 1
        parts.append(part(f"leg_{i}", (side * s * 0.35, 24 - 3, (-1 if i < 2 else 1) * s * 0.3), [box(0, 1.5, 0, 2, 3, 2)],
                          role="leg", phase=i * math.pi / 2))
    return parts


def hydra(p):
    L, W, H = p["body_len"], p["body_w"], p["body_h"]
    leg = p["leg_len"]
    body_y = 24 - leg - H / 2
    parts = [part("body", (0, body_y, 0), [box(0, 0, 0, W, H, L)], role="body", amp=0.2)]
    heads = p.get("heads", 3)
    for i in range(heads):
        off = (i - (heads - 1) / 2) * (W / heads)
        parts.append(part(f"neck_{i}", (off, -H / 2, -L / 2 + 2), [box(0, -5, 0, 3, 10, 3)], parent="body", rot=(0.35, (i - 1) * 0.3, 0),
                          role="tentacle", phase=i * 1.3, amp=0.25))
        parts.append(part(f"head_{i}" if i else "head", (0, -10, 0), [box(0, -2, -3, 5, 4, 7)], parent=f"neck_{i}", role="jaw" if i else "head",
                          face=0))
    for i, z in enumerate((-L * 0.3, L * 0.3)):
        for side in (-1, 1):
            parts.append(part(f"leg_{i}_{side}", (side * (W / 2 - 2), 24 - leg, z), [box(0, leg / 2, 0, 4, leg, 4)], role="leg",
                              phase=i * math.pi + (0 if side < 0 else math.pi)))
    parts.append(part("tail", (0, 0, L / 2), [box(0, 0, 5, 3, 3, 10)], parent="body", role="tail", amp=0.4))
    return parts


def horror(p):
    """Tentacled land horrors: a bulbous mantle walking on writhing arms."""
    s = p["size"]
    t = p["tentacle"]
    parts = [part("head", (0, 24 - t - s / 2, 0), [box(0, 0, 0, s, s, s)], role="body", amp=0.4, face=0)]
    n = p.get("tentacles", 6)
    for i in range(n):
        a = i * 2 * math.pi / n
        parts.append(part(f"tentacle_{i}", (math.cos(a) * s * 0.3, 24 - t, math.sin(a) * s * 0.3), [box(0, t / 2, 0, 2, t, 2)],
                          rot=(math.sin(a) * 0.3, 0, -math.cos(a) * 0.3), role="tentacle", phase=a, amp=0.3))
    return parts


def worm_head(p):
    s = p["seg"]
    parts = [part("head", (0, 24 - s / 2, 0), [box(0, 0, 0, s, s, s * 1.1)], face=0)]
    for i in range(p.get("mandibles", 3)):
        a = i * 2 * math.pi / p.get("mandibles", 3) - math.pi / 2
        parts.append(part(f"mandible_{i}", (math.cos(a) * s * 0.3, math.sin(a) * s * 0.3, -s * 0.55),
                          [box(0, 0, -s * 0.2, s * 0.3, s * 0.3, s * 0.45)], parent="head", rot=(math.sin(a) * 0.4, -math.cos(a) * 0.4, 0),
                          role="jaw", amp=0.6))
    return parts


def worm_segment(p):
    s = p["seg"]
    parts = [part("body", (0, 24 - s / 2, 0), [box(0, 0, 0, s * 0.95, s * 0.95, s * 1.05)])]
    if p.get("legs"):
        for side in (-1, 1):
            parts.append(part(f"leg_{side}", (side * s * 0.45, s * 0.3, 0), [box(side * 2, 0, 0, 4, 1, 1)], parent="body",
                              rot=(0, 0, side * 0.5), role="leg_side", phase=0 if side < 0 else math.pi, amp=0.5))
    if p.get("ridge"):
        parts.append(part("ridge", (0, -s * 0.5, 0), [box(0, -1, 0, 1, 2, s * 0.6)], parent="body"))
    return parts


PLANS = {
    "quadruped": quadruped, "biped": biped, "insect": insect, "arachnid": arachnid, "crab": crab, "blob": blob,
    "flyer": flyer, "ray": ray, "jelly": jelly, "eye": floater_eye, "tripod": tripod, "plant": plant, "mushroom": mushroom,
    "sprite": sprite, "mimic": mimic, "hydra": hydra, "horror": horror, "worm": worm_head,
}


# ---------------------------------------------------------------------------------------------- adornments

def find(parts, name):
    for p in parts:
        if p["name"] == name:
            return p
    return None


def top_part(parts):
    for name in ("body", "thorax", "cephalo", "stem", "head"):
        p = find(parts, name)
        if p is not None:
            return p
    return parts[0]


def adorn(parts, element, rng):
    """Element-specific extras so a Frost Stalker is visibly not an Ember Stalker."""
    body = top_part(parts)
    c = body["cubes"][0]
    w, h, d = c["size"]
    ox, oy, oz = c["origin"]
    head = find(parts, "head")
    extra = []
    if element in ("crystal", "frost"):
        n = 3 + rng.randrange(3)
        for i in range(n):
            z = oz + (i + 0.5) * d / n
            ht = 2 + rng.randrange(4) if element == "crystal" else 2
            extra.append(part(f"spike_{i}", (0, oy, z), [box(0, -ht / 2, 0, 1 if element == "frost" else 2, ht, 2)], parent=body["name"],
                              rot=(rng.uniform(-0.3, 0.3), 0, rng.uniform(-0.25, 0.25))))
    if element in ("ember", "magma", "storm") and head is not None:
        hc = head["cubes"][0]
        hw, hh, hd = hc["size"]
        hx, hy, hz = hc["origin"]
        for side in (-1, 1):
            extra.append(part(f"horn_{side}", (side * hw * 0.35, hy, hz + hd * 0.5), [box(0, -2, 0, 1, 4, 1)], parent="head",
                              rot=(-0.5, 0, side * 0.35)))
    if element == "void" and head is not None:
        for side in (-1, 1):
            extra.append(part(f"tendril_{side}", (side * 1.5, 0, -1), [box(0, 3, 0, 1, 6, 1)], parent="head",
                              role="tentacle", phase=side, amp=0.5))
    if element == "toxic":
        for i in range(2):
            extra.append(part(f"sac_{i}", (0, oy + h * 0.2, oz + d * (0.3 + 0.4 * i)), [box((i * 2 - 1) * w / 2, 0, 0, 3, 3, 3)],
                              parent=body["name"], role="bounce", amp=0.3))
    if element == "storm":
        extra.append(part("crest", (0, oy, oz + d / 2), [box(0, -2, 0, 1, 3, d * 0.7)], parent=body["name"]))
    return parts + extra
