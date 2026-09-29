"""Generates Galaxy MC's block/item textures, models, blockstates, item definitions, loot tables,
recipes, tags, enchantment, damage types, equipment asset and English names.

Run from the repository root:  python3 tools/assets/gen_assets.py
Entity/spawn-egg assets come from tools/mobs/gen_mobs.py and are left alone (their lang keys are kept).
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import pixels as px  # noqa: E402

NS = "galaxy_mc"
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", NS)
D = os.path.join(ROOT, "data", NS)
DM = os.path.join(ROOT, "data", "minecraft")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tex(path, img):
    full = os.path.join(A, "textures", path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    px.save(img, full)


def ref(kind, name):
    return f"{NS}:{kind}/{name}"


def argb(rgb):
    """Opaque ARGB as a signed 32-bit int (item tint JSON)."""
    v = 0xFF000000 | rgb
    return v - (1 << 32)


# ============================================================================================== palettes

MOON_ROCK = ["#5a5a5e", "#707074", "#86868a", "#9a9aa0"]
MARS_ROCK = ["#6a2a18", "#8a3a22", "#a04a2c", "#b45a38"]
VENUS_BASALT = ["#3a2e22", "#4a3c2c", "#5c4a36", "#6a5840"]
MERCURY_ROCK = ["#48423e", "#5a534e", "#6c6560", "#7e7670"]
JOVIAN = ["#a89880", "#bcac94", "#cec0a8", "#dcd0bc"]
EUROPA_ICE = ["#6a9ac8", "#8ab8e0", "#b0d4f0", "#d8ecff"]
IO_ROCK = ["#8a5a10", "#c89a20", "#e0c030", "#f0e060"]
TITAN_ROCK = ["#4a3424", "#5e4430", "#72543c", "#846448"]
RING_ROCK = ["#6a6864", "#828078", "#9a968e", "#b0aca4"]
PLUTO_ROCK = ["#7a7470", "#908a84", "#a49e98", "#b8b2ac"]
ALIEN_STONE = ["#606060", "#808080", "#9c9c9c", "#b8b8b8"]
SOIL = ["#4a3424", "#5c4230", "#6e5038", "#806044"]

# Vanilla-like ore colours for the frontier's tinted-stone ores: [dark, mid, light].
VANILLA_ORES = {
    "coal": ["#101010", "#2c2c2c", "#4c4c4c"],
    "iron": ["#9a6a4a", "#d8a888", "#f0d4bc"],
    "copper": ["#7a3a1a", "#e07a4a", "#5cc0a0"],
    "gold": ["#a07010", "#f8d020", "#fff890"],
    "redstone": ["#700000", "#e01010", "#ff7070"],
    "lapis": ["#10307a", "#2050d0", "#70a0ff"],
    "diamond": ["#108080", "#40e0d0", "#d0fff8"],
    "emerald": ["#006020", "#10c040", "#90ffb0"],
}

# In-inventory colours for blocks that are tinted per planet in the world.
TINT_STONE = 0xC8C0D8
TINT_DEEP = 0x9C96AA


# ============================================================================================== blocks
# kind: cube | tcube (tinted) | bottom_top | column | log | grass | turf | tore (tinted ore) |
#       exotic | leaves | cross | tcross | shard | door | orient

BLOCKS = {}


def block(name, kind, **kw):
    BLOCKS[name] = dict(kind=kind, **kw)


def paint_blocks():
    t = {}

    def put(name, img):
        t[name] = img
        tex("block/" + name, img)

    # ---------------------------------------------------------------- Moon
    put("moon_regolith", px.regolith("moon_regolith", ["#6a6a6a", "#8a8a8a", "#a4a4a4", "#bcbcbc"], craters=3))
    put("moon_rock", px.stone("moon_rock", MOON_ROCK))
    put("moon_deep_rock", px.stone("moon_deep_rock", ["#2e2e34", "#3c3c44", "#4a4a52", "#56565e"]))
    put("polished_moon_rock", px.polished("polished_moon_rock", MOON_ROCK))
    put("moon_rock_bricks", px.bricks("moon_rock_bricks", MOON_ROCK, "#48484c"))
    put("lunar_titanium_ore", px.ore(t["moon_rock"], "lunar_titanium_ore", ["#4a5a6a", "#a8c0d8", "#e8f4ff"]))
    put("moon_cheese_block", px.cheese("moon_cheese_block"))
    # ---------------------------------------------------------------- Mars
    put("mars_sand", px.sand("mars_sand", ["#8a3a1a", "#a84a24", "#c05a2c", "#d06a38"]))
    put("mars_rock", px.strata("mars_rock", MARS_ROCK))
    put("mars_deep_rock", px.stone("mars_deep_rock", ["#3a1a12", "#4a2218", "#5a2c20", "#6a3426"]))
    put("mars_rock_bricks", px.bricks("mars_rock_bricks", MARS_ROCK, "#4a1a10"))
    put("mars_cobalt_ore", px.ore(t["mars_rock"], "mars_cobalt_ore", ["#101a50", "#2a4ac0", "#7a9aff"]))
    put("mars_polar_ice", px.ice("mars_polar_ice", ["#c0d0da", "#d4e2ea", "#e6f0f6", "#f8fcff"]))
    # ---------------------------------------------------------------- Venus
    put("venus_basalt", px.strata("venus_basalt", VENUS_BASALT, bands=4))
    put("venus_regolith", px.sand("venus_regolith", ["#8a7440", "#a88c50", "#c0a460", "#d0b870"]))
    put("venus_sulfur_ore", px.ore(t["venus_basalt"], "venus_sulfur_ore", ["#8a8a10", "#e8e020", "#fff890"], clusters=5))
    put("sulfur_block", px.crystal_block("sulfur_block", ["#b8a810", "#d8c818", "#ece040", "#fff890"]))
    # ---------------------------------------------------------------- Mercury
    put("mercury_rock", px.stone("mercury_rock", MERCURY_ROCK))
    put("mercury_dust", px.regolith("mercury_dust", ["#4a4540", "#5e5852", "#6e6760", "#7e7670"], craters=2))
    put("mercury_iridium_ore", px.ore(t["mercury_rock"], "mercury_iridium_ore", ["#5a5a6a", "#c8c8e0", "#ffffff"]))
    # ---------------------------------------------------------------- Sun
    put("solar_slag", px.slag("solar_slag"))
    frames = px.plasma_frames("solar_plasma")
    tex("block/solar_plasma", frames)
    with open(os.path.join(A, "textures", "block", "solar_plasma.png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, f, indent=2)
    put("helionite_ore", px.ore(t["solar_slag"], "helionite_ore", ["#a06a10", "#ffd040", "#fffac8"], clusters=5))
    # ---------------------------------------------------------------- Jupiter
    put("jovian_cloud", px.cloud("jovian_cloud", ["#c8a888", "#e0c8a8", "#f0e0c8", "#fff4e8"]))
    put("jovian_stone", px.stone("jovian_stone", JOVIAN, cracks=1))
    put("storm_crystal_ore", px.ore(t["jovian_stone"], "storm_crystal_ore", ["#3a1a7a", "#a080ff", "#f0e8ff"]))
    # ---------------------------------------------------------------- Europa
    put("europa_ice", px.ice("europa_ice", EUROPA_ICE))
    put("europa_frost", px.sand("europa_frost", ["#c8d8e8", "#dce8f4", "#eef4fc", "#ffffff"], pebbles=0))
    put("cryonite_ore", px.ore(t["europa_ice"], "cryonite_ore", ["#0a5a90", "#40d0ff", "#d0faff"]))
    # ---------------------------------------------------------------- Io
    put("io_sulfur_rock", px.stone("io_sulfur_rock", IO_ROCK, speckle=0.1))
    put("io_ash", px.sand("io_ash", ["#2a2622", "#3a3530", "#4a4238", "#5a5248"]))
    put("pyrocite_ore", px.ore(t["io_sulfur_rock"], "pyrocite_ore", ["#700808", "#ff4020", "#ffd0a0"]))
    # ---------------------------------------------------------------- Titan
    put("titan_sediment", px.sand("titan_sediment", ["#5a3a1a", "#7a5028", "#8a5a2e", "#a06a3a"]))
    put("titan_rock", px.stone("titan_rock", TITAN_ROCK))
    put("methane_clathrate_ore", px.ore(t["titan_rock"], "methane_clathrate_ore", ["#5a8080", "#c0e8e0", "#ffffff"], clusters=5))
    # ---------------------------------------------------------------- Saturn's rings
    put("ring_ice", px.ice("ring_ice", ["#8aa0b0", "#a8bcc8", "#c8d8e0", "#e8f0f4"]))
    put("ring_rock", px.stone("ring_rock", RING_ROCK))
    # ---------------------------------------------------------------- Pluto
    put("nitrogen_ice", px.ice("nitrogen_ice", ["#c8c0b8", "#dcd4cc", "#ece6e0", "#fcf8f4"], streaks=3))
    put("pluto_rock", px.stone("pluto_rock", PLUTO_ROCK))
    put("tholin_dust", px.sand("tholin_dust", ["#4a1e14", "#6a2c1e", "#7a3a2a", "#8e4a38"]))
    put("plutonite_ore", px.ore(t["pluto_rock"], "plutonite_ore", ["#106010", "#50ff50", "#e0ffe0"]))

    # ---------------------------------------------------------------- Frontier (tinted in world)
    stone_g = px.gray(px.stone("alien_stone", ALIEN_STONE), 150, 240)
    put("alien_stone", stone_g)
    deep_g = px.gray(px.stone("alien_deep_stone", ALIEN_STONE, cracks=3), 140, 225)
    put("alien_deep_stone", deep_g)
    put("alien_stone_bricks", px.gray(px.bricks("alien_stone_bricks", ALIEN_STONE, "#505050"), 120, 240))
    put("alien_sand", px.gray(px.sand("alien_sand", ["#808080", "#a0a0a0", "#b8b8b8", "#d0d0d0"]), 175, 250))
    put("alien_regolith", px.gray(px.regolith("alien_regolith", ["#707070", "#909090", "#a8a8a8", "#c0c0c0"]), 150, 240))
    put("alien_soil", px.gray(px.sand("alien_soil", SOIL, pebbles=5), 130, 220))
    put("alien_grass_top", px.gray(px.grass_top("alien_grass", ["#3a6a2a", "#4a8a34", "#5aa040", "#6ab84c"]), 140, 240))
    put("alien_grass_side_overlay", px.gray(px.grass_overlay("alien_grass", ["#3a6a2a", "#4a8a34", "#5aa040", "#6ab84c"]), 140, 240))
    soil = px.sand("alien_grass_soil", SOIL, pebbles=5)
    put("alien_grass_side", soil)
    put("alien_grass_bottom", soil)
    for turf, cols in (("fungal_turf", ["#3a1a4a", "#5a2a70", "#7a3a90", "#a060c0"]),
                       ("toxic_turf", ["#4a8a10", "#6ab018", "#90d030", "#c0f050"])):
        top = px.grass_top(turf, cols)
        put(turf + "_top", top)
        side = soil.copy()
        ov = px.grass_overlay(turf, cols)
        m = ov[..., 3] > 0
        side[m] = ov[m]
        put(turf + "_side", side)
    put("ash_block", px.sand("ash_block", ["#3a3632", "#4a4540", "#5a5550", "#6a6560"]))
    scorched = px.stone("scorched_rock", ["#141010", "#221a16", "#30261e", "#3e3228"], speckle=0.0)
    for (y, x) in ((3, 4), (9, 11), (12, 3), (6, 13)):
        scorched[y, x, :3] = px.hexc("#ff6a10")
    put("scorched_rock", scorched)
    put("crystal_block", px.gray(px.crystal_block("crystal_block", ["#606060", "#909090", "#c0c0c0", "#f0f0f0"]), 150, 255))
    put("glowing_crystal_block", px.gray(px.crystal_block("glowing_crystal_block", ["#a0a0a0", "#c8c8c8", "#e8e8e8", "#ffffff"]), 175, 255))
    put("xeno_log", px.log_side("xeno_log", ["#2a1a3a", "#3e2a54", "#54386e", "#6a4888"]))
    put("xeno_log_top", px.log_top("xeno_log_top", ["#2a1a3a", "#3e2a54"], ["#8a6ab0", "#a080c8"]))
    put("xeno_planks", px.planks("xeno_planks", ["#3a2450", "#503468", "#664480", "#7a5494"]))
    put("xeno_leaves", px.gray(px.leaves("xeno_leaves", ["#404040", "#707070", "#a0a0a0", "#d0d0d0"]), 110, 250))
    put("xeno_grass", px.gray(px.blades("xeno_grass", ["#607060", "#90a090", "#c0d0c0", "#e0f0e0"]), 130, 250))
    put("glow_shroom", px.mushroom("glow_shroom", ["#c8d8d0", "#a8b8b0"], ["#60fff0", "#20b0b0"], "#e0ffff"))
    put("spine_plant", px.spines("spine_plant"))
    put("frost_bloom", px.bloom("frost_bloom", ["#c0e8ff", "#80c8ff", "#ffffff"], "#4080ff"))
    put("ember_bloom", px.bloom("ember_bloom", ["#ff6020", "#ffa030", "#ffe060"], "#ff2000", stem="#6a3020"))
    put("crystal_shard", px.shard("crystal_shard"))

    for ore_name, cols in VANILLA_ORES.items():
        o = px.ore(stone_g, "alien_" + ore_name + "_ore", cols, clusters=5 if ore_name != "coal" else 6)
        put("alien_%s_ore_overlay" % ore_name, px.overlay_only(stone_g, o))
    exo = px.ore(stone_g, "exotic_ore", ["#707070", "#c8c8c8", "#ffffff"], clusters=5, size=(3, 5))
    put("exotic_ore_overlay", px.overlay_only(stone_g, exo))
    dexo = px.ore(deep_g, "deep_exotic_ore", ["#707070", "#c8c8c8", "#ffffff"], clusters=6, size=(3, 5))
    put("deep_exotic_ore_overlay", px.overlay_only(deep_g, dexo))

    # ---------------------------------------------------------------- Ships & machines
    HULL = ["#b8bcc4", "#d8dce2", "#eef0f4"]
    DARK = ["#3a3e44", "#4c5058", "#62666e"]
    put("hull_plating", px.panel("hull_plating", HULL))
    put("hull_plating_dark", px.panel("hull_plating_dark", DARK))
    put("hull_stripe", px.hazard("hull_stripe"))
    put("ship_floor", px.grate("ship_floor"))
    put("ship_light", px.ship_light("ship_light"))
    put("reinforced_glass", px.glass("reinforced_glass"))
    put("thruster_side", px.vents("thruster_side", DARK, "#ff8a30"))
    put("thruster_bottom", px.nozzle("thruster_bottom"))
    put("thruster_top", px.panel("thruster_top", DARK))
    put("airlock_door_top", px.door_half("airlock_door_top", True))
    put("airlock_door_bottom", px.door_half("airlock_door_bottom", False))
    put("navigation_console_front", px.screen("navigation_console_front"))
    put("navigation_console_top", px.screen("navigation_console_top", stars=False))
    put("navigation_console_side", px.panel("navigation_console_side", ["#303848", "#3c4658", "#566278"]))
    put("life_support_side", px.vents("life_support_side", HULL, "#40f0e0"))
    put("life_support_top", px.fan_top("life_support_top", HULL, "#8a9aa4", "#40f0e0"))
    put("life_support_bottom", px.panel("life_support_bottom", HULL, seams=False))
    put("thermal_armor_table_top", px.table_top("thermal_armor_table_top"))
    side = px.planks("thermal_armor_table_side", ["#5a3a20", "#7a5230", "#946a40", "#a87a4c"])
    side[5:8, 1:15, :3] = px.hexc("#d84040")
    side[8:11, 1:15, :3] = px.hexc("#8ac0f0")
    put("thermal_armor_table_side", side)
    put("thermal_armor_table_bottom", px.planks("thermal_armor_table_bottom", ["#4a3018", "#5a3a20", "#7a5230", "#946a40"]))
    put("stellar_forge_top", px.forge_top("stellar_forge_top"))
    fside = px.vents("stellar_forge_side", ["#20182c", "#2c2240", "#3c3058"], "#c050ff")
    put("stellar_forge_side", fside)
    put("stellar_forge_bottom", px.panel("stellar_forge_bottom", ["#20182c", "#2c2240", "#3c3058"], seams=False))
    put("fuel_block_side", px.barrel("fuel_block_side"))
    put("fuel_block_top", px.barrel_top("fuel_block_top"))
    put("titanium_block", px.metal_block("titanium_block", ["#8a929a", "#a8b0b8", "#c8d0d8", "#e0e8f0"]))
    put("cobalt_block", px.metal_block("cobalt_block", ["#1a2a6a", "#2a44a0", "#3a5cd0", "#6a8aff"]))
    put("iridium_block", px.metal_block("iridium_block", ["#9a9aa8", "#babac8", "#d8d8e4", "#f4f4fc"]))
    return t


def define_blocks():
    for n in ("moon_regolith", "moon_rock", "moon_deep_rock", "polished_moon_rock", "moon_rock_bricks", "lunar_titanium_ore",
              "moon_cheese_block", "mars_sand", "mars_rock", "mars_deep_rock", "mars_rock_bricks", "mars_cobalt_ore",
              "mars_polar_ice", "venus_basalt", "venus_regolith", "venus_sulfur_ore", "sulfur_block", "mercury_rock",
              "mercury_dust", "mercury_iridium_ore", "solar_slag", "solar_plasma", "helionite_ore", "jovian_cloud",
              "jovian_stone", "storm_crystal_ore", "europa_ice", "europa_frost", "cryonite_ore", "io_sulfur_rock", "io_ash",
              "pyrocite_ore", "titan_sediment", "titan_rock", "methane_clathrate_ore", "ring_ice", "ring_rock", "nitrogen_ice",
              "pluto_rock", "tholin_dust", "plutonite_ore", "ash_block", "scorched_rock", "hull_plating", "hull_plating_dark",
              "hull_stripe", "ship_floor", "ship_light", "reinforced_glass", "titanium_block", "cobalt_block", "iridium_block"):
        block(n, "cube")
    block("alien_stone", "tcube", tint=TINT_STONE)
    block("alien_deep_stone", "tcube", tint=TINT_DEEP)
    block("alien_stone_bricks", "tcube", tint=TINT_STONE)
    block("alien_sand", "tcube", tint=0xD8C8A8)
    block("alien_regolith", "tcube", tint=0xA8A4A0)
    block("alien_soil", "tcube", tint=0x8A6A4A)
    block("crystal_block", "tcube", tint=0x7AD0E8)
    block("glowing_crystal_block", "tcube", tint=0x9AE8F8)
    block("alien_grass", "grass", tint=0x6AA84F)
    block("fungal_turf", "turf")
    block("toxic_turf", "turf")
    block("xeno_log", "log")
    block("xeno_planks", "cube")
    block("xeno_leaves", "leaves", tint=0x7A4AB0)
    block("xeno_grass", "tcross", tint=0x6AA84F)
    for n in ("glow_shroom", "spine_plant", "frost_bloom", "ember_bloom"):
        block(n, "cross")
    block("crystal_shard", "shard", tint=0x7AD0E8)
    for o in VANILLA_ORES:
        block("alien_%s_ore" % o, "tore", base="alien_stone", overlay="alien_%s_ore_overlay" % o, tint=TINT_STONE)
    block("exotic_ore", "exotic", base="alien_stone", overlay="exotic_ore_overlay", tint=TINT_STONE)
    block("deep_exotic_ore", "exotic", base="alien_deep_stone", overlay="deep_exotic_ore_overlay", tint=TINT_DEEP)
    block("thruster", "bottom_top", top="thruster_top", side="thruster_side", bottom="thruster_bottom")
    block("airlock_door", "door")
    block("navigation_console", "orient", top="navigation_console_top", front="navigation_console_front", side="navigation_console_side")
    block("life_support", "bottom_top", top="life_support_top", side="life_support_side", bottom="life_support_bottom")
    block("thermal_armor_table", "bottom_top", top="thermal_armor_table_top", side="thermal_armor_table_side",
          bottom="thermal_armor_table_bottom")
    block("stellar_forge", "bottom_top", top="stellar_forge_top", side="stellar_forge_side", bottom="stellar_forge_bottom")
    block("fuel_block", "column", end="fuel_block_top", side="fuel_block_side")


def face(texture, cull=None, tint=None):
    f = {"texture": texture}
    if cull:
        f["cullface"] = cull
    if tint is not None:
        f["tintindex"] = tint
    return f


SIDES = ("down", "up", "north", "south", "west", "east")


def parent_models():
    m = os.path.join(A, "models", "block")
    write_json(os.path.join(m, "tinted_cube_all.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#all"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {s: face("#all", s, 0) for s in SIDES}}],
    })
    write_json(os.path.join(m, "tinted_ore.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#base"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {s: face("#base", s, 0) for s in SIDES}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {s: face("#overlay", s) for s in SIDES}},
        ],
    })
    write_json(os.path.join(m, "exotic_ore.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#base"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {s: face("#base", s, 0) for s in SIDES}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {s: face("#overlay", s, 1) for s in SIDES}},
        ],
    })
    write_json(os.path.join(m, "tinted_grass_block.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#bottom"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "down": face("#bottom", "down"), "up": face("#top", "up", 0),
                "north": face("#side", "north"), "south": face("#side", "south"),
                "west": face("#side", "west"), "east": face("#side", "east")}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                s: face("#overlay", s, 0) for s in ("north", "south", "west", "east")}},
        ],
    })


def block_models_and_states():
    ms = os.path.join(A, "models", "block")
    bs = os.path.join(A, "blockstates")
    items = os.path.join(A, "items")
    im = os.path.join(A, "models", "item")

    def model(name, obj):
        write_json(os.path.join(ms, name + ".json"), obj)

    def state(name, variants):
        write_json(os.path.join(bs, name + ".json"), {"variants": variants})

    def item_def(name, model_ref, tints=None):
        m = {"type": "minecraft:model", "model": model_ref}
        if tints:
            m["tints"] = [{"type": "minecraft:constant", "value": argb(c)} for c in tints]
        write_json(os.path.join(items, name + ".json"), {"model": m})

    def flat_item(name, texture, tint=None):
        write_json(os.path.join(im, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
        item_def(name, ref("item", name), [tint] if tint is not None else None)

    for name, b in BLOCKS.items():
        k = b["kind"]
        simple = {"": {"model": ref("block", name)}}
        if k == "cube":
            model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": ref("block", name)}})
            state(name, simple)
            item_def(name, ref("block", name))
        elif k == "tcube":
            model(name, {"parent": ref("block", "tinted_cube_all"), "textures": {"all": ref("block", name)}})
            state(name, simple)
            item_def(name, ref("block", name), [b["tint"]])
        elif k == "grass":
            model(name, {"parent": ref("block", "tinted_grass_block"), "textures": {
                "top": ref("block", name + "_top"), "side": ref("block", name + "_side"),
                "bottom": ref("block", name + "_bottom"), "overlay": ref("block", name + "_side_overlay")}})
            state(name, simple)
            item_def(name, ref("block", name), [b["tint"]])
        elif k == "turf":
            model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": ref("block", name + "_top"), "side": ref("block", name + "_side"),
                "bottom": ref("block", "alien_grass_bottom")}})
            state(name, simple)
            item_def(name, ref("block", name))
        elif k == "bottom_top":
            model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": ref("block", b["top"]), "side": ref("block", b["side"]), "bottom": ref("block", b["bottom"])}})
            state(name, simple)
            item_def(name, ref("block", name))
        elif k == "column":
            model(name, {"parent": "minecraft:block/cube_column", "textures": {
                "end": ref("block", b["end"]), "side": ref("block", b["side"])}})
            state(name, simple)
            item_def(name, ref("block", name))
        elif k == "log":
            tx = {"end": ref("block", name + "_top"), "side": ref("block", name)}
            model(name, {"parent": "minecraft:block/cube_column", "textures": tx})
            model(name + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": tx})
            state(name, {"axis=x": {"model": ref("block", name + "_horizontal"), "x": 90, "y": 90},
                         "axis=y": {"model": ref("block", name)},
                         "axis=z": {"model": ref("block", name + "_horizontal"), "x": 90}})
            item_def(name, ref("block", name))
        elif k == "tore":
            model(name, {"parent": ref("block", "tinted_ore"), "textures": {
                "base": ref("block", b["base"]), "overlay": ref("block", b["overlay"])}})
            state(name, simple)
            item_def(name, ref("block", name), [b["tint"]])
        elif k == "exotic":
            model(name, {"parent": ref("block", "exotic_ore"), "textures": {
                "base": ref("block", b["base"]), "overlay": ref("block", b["overlay"])}})
            state(name, simple)
            item_def(name, ref("block", name), [b["tint"], 0xC080FF])
        elif k == "leaves":
            model(name, {"parent": "minecraft:block/leaves", "textures": {"all": ref("block", name)}})
            state(name, simple)
            item_def(name, ref("block", name), [b["tint"]])
        elif k in ("cross", "tcross"):
            parent = "minecraft:block/tinted_cross" if k == "tcross" else "minecraft:block/cross"
            model(name, {"parent": parent, "textures": {"cross": ref("block", name)}})
            state(name, simple)
            flat_item(name, ref("block", name), b.get("tint"))
        elif k == "shard":
            model(name, {"parent": "minecraft:block/tinted_cross", "textures": {"cross": ref("block", name)}})
            m = ref("block", name)
            state(name, {"facing=up": {"model": m}, "facing=down": {"model": m, "x": 180},
                         "facing=north": {"model": m, "x": 90}, "facing=south": {"model": m, "x": 90, "y": 180},
                         "facing=east": {"model": m, "x": 90, "y": 90}, "facing=west": {"model": m, "x": 90, "y": 270}})
            flat_item(name, ref("block", name), b["tint"])
        elif k == "door":
            tx = {"bottom": ref("block", name + "_bottom"), "top": ref("block", name + "_top")}
            for half in ("bottom", "top"):
                for hinge in ("left", "right"):
                    for op in ("", "_open"):
                        model(f"{name}_{half}_{hinge}{op}", {"parent": f"minecraft:block/door_{half}_{hinge}{op}", "textures": tx})
            variants = {}
            base_rot = {"east": 0, "south": 90, "west": 180, "north": 270}
            for facing, rot in base_rot.items():
                for half, hname in (("lower", "bottom"), ("upper", "top")):
                    for hinge in ("left", "right"):
                        for op in (False, True):
                            r = rot + ((90 if hinge == "left" else 270) if op else 0)
                            v = {"model": ref("block", f"{name}_{hname}_{hinge}{'_open' if op else ''}")}
                            if r % 360:
                                v["y"] = r % 360
                            variants[f"facing={facing},half={half},hinge={hinge},open={'true' if op else 'false'}"] = v
            state(name, variants)
            flat_item(name, ref("item", name))
        elif k == "orient":
            model(name, {"parent": "minecraft:block/orientable", "textures": {
                "top": ref("block", b["top"]), "front": ref("block", b["front"]), "side": ref("block", b["side"])}})
            m = ref("block", name)
            state(name, {"facing=north": {"model": m}, "facing=east": {"model": m, "y": 90},
                         "facing=south": {"model": m, "y": 180}, "facing=west": {"model": m, "y": 270}})
            item_def(name, m)


# ============================================================================================== items

ITEM_ART = {
    "raw_titanium": lambda n: px.lump(n, ["#2a343e", "#5a6878", "#7a8a9a", "#a0b0c0", "#d0dce8"]),
    "titanium_ingot": lambda n: px.ingot(n, ["#3a444e", "#7a8894", "#9aa8b4", "#c0ccd8", "#eef4fa"]),
    "raw_cobalt": lambda n: px.lump(n, ["#0a1030", "#1a2a6a", "#2a44a0", "#3a5cd0", "#7a9aff"]),
    "cobalt_ingot": lambda n: px.ingot(n, ["#0a1440", "#1a3490", "#2a4cc0", "#4a6ae0", "#9ab4ff"]),
    "raw_iridium": lambda n: px.lump(n, ["#303038", "#6a6a78", "#9090a0", "#b8b8c8", "#f0f0ff"]),
    "iridium_ingot": lambda n: px.ingot(n, ["#40404c", "#9a9aa8", "#babac8", "#dcdce8", "#ffffff"]),
    "sulfur": lambda n: px.powder(n, ["#5a5000", "#b8a810", "#d8c818", "#f0e040", "#ffffa0"]),
    "helionite_shard": lambda n: px.gem(n, ["#5a3000", "#e08a10", "#ffc030", "#ffe880", "#ffffff"], "shard"),
    "storm_crystal": lambda n: px.gem(n, ["#200a50", "#6040c0", "#a080ff", "#d0c0ff", "#ffffff"], "diamond"),
    "cryonite_crystal": lambda n: px.gem(n, ["#08304e", "#1a8ac0", "#40d0ff", "#a0f0ff", "#ffffff"], "shard"),
    "pyrocite_crystal": lambda n: px.gem(n, ["#380606", "#a01810", "#ff4020", "#ff9060", "#ffe0c0"], "diamond"),
    "methane_crystal": lambda n: px.gem(n, ["#1a3030", "#5a8a88", "#8ac0b8", "#c0e8e0", "#ffffff"], "hex"),
    "plutonite": lambda n: px.gem(n, ["#063006", "#208020", "#40d040", "#90ff90", "#e0ffe0"], "round"),
    "moon_cheese": lambda n: px.wedge(n),
    "xeno_hide": lambda n: px.hide(n, ["#1a1020", "#4a3050", "#6a4a70", "#8a6a90", "#a888b0"]),
    "bio_gel": lambda n: px.blob(n, ["#064030", "#20a070", "#40d090", "#80f0b0", "#c0ffe0"]),
    "chitin_plate": lambda n: px.gem(n, ["#2a1e08", "#6a5020", "#8a6a30", "#a88a48", "#c8a860"], "hex"),
    "venom_sac": lambda n: px.blob(n, ["#1a2a08", "#4a7020", "#6aa030", "#90c850", "#c0f080"]),
    "glow_spore": lambda n: px.spores(n, ["#004040", "#20c0c0", "#b0ffff"]),
    "thermal_fiber": lambda n: px.spool(n, ["#4a0a0a", "#c83030", "#e85040", "#f08060", "#ffb090"]),
    "cryo_gland": lambda n: px.blob(n, ["#061e3a", "#1a5aa0", "#3a8ad0", "#70b8f0", "#c0e8ff"]),
    "titan_core": lambda n: px.core(n),
    "worldeater_fang": lambda n: px.fang(n, ["#3a3020", "#c8c0a8", "#e0d8c0", "#f0ecdc", "#ffffff"]),
    "leviathan_scale": lambda n: px.scale_item(n, ["#061e2a", "#1a5a6a", "#2a8a9a", "#50b8c0", "#a0e8f0"]),
    "xeno_meat": lambda n: px.meat(n, ["#3a0a24", "#a03a6a", "#c05080", "#d87098", "#f0a0c0"]),
    "cooked_xeno_meat": lambda n: px.meat(n, ["#2a1008", "#6a3a20", "#8a5030", "#a86a40", "#c88a58"]),
    "rocket_fuel_canister": lambda n: px.canister(n),
    "rocket_blueprint": lambda n: px.blueprint(n, ["#1a3a7a", "#2a5ab0", "#4a7ad0"], "#e0f0ff", "#f08a10"),
    "starship_blueprint": lambda n: px.blueprint(n, ["#3a1a6a", "#5a2ab0", "#7a4ad0"], "#fff0c0", "#40e0ff"),
    "thermometer": lambda n: px.thermometer(n),
    "space_helmet": lambda n: px.suit_piece(n, "helmet"),
    "space_chestplate": lambda n: px.suit_piece(n, "chestplate"),
    "space_leggings": lambda n: px.suit_piece(n, "leggings"),
    "space_boots": lambda n: px.suit_piece(n, "boots"),
}


def items(block_tex):
    im = os.path.join(A, "models", "item")
    idef = os.path.join(A, "items")
    for name, fn in ITEM_ART.items():
        tex("item/" + name, fn(name))
        write_json(os.path.join(im, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("item", name)}})
        write_json(os.path.join(idef, name + ".json"), {"model": {"type": "minecraft:model", "model": ref("item", name)}})

    # Procedural items: layer0 is tinted with the stack's own colour (custom model data), layer1 is not.
    for name, layers in (("exotic_mineral", px.mineral_layers("exotic_mineral")), ("relic", px.relic_layers("relic"))):
        tex("item/" + name, layers[0])
        tex("item/" + name + "_overlay", layers[1])
        write_json(os.path.join(im, name + ".json"), {"parent": "minecraft:item/generated", "textures": {
            "layer0": ref("item", name), "layer1": ref("item", name + "_overlay")}})
        default = 0xC080FF if name == "exotic_mineral" else 0x60D0FF
        write_json(os.path.join(idef, name + ".json"), {"model": {
            "type": "minecraft:model", "model": ref("item", name),
            "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": argb(default)}]}})

    # Airlock door item icon: the two door halves squeezed side by side into one tall sprite.
    top, bottom = block_tex["airlock_door_top"], block_tex["airlock_door_bottom"]
    icon = np.zeros((16, 16, 4), np.uint8)
    icon[0:8, 4:12] = top[0:16:2, 0:16:2]
    icon[8:16, 4:12] = bottom[0:16:2, 0:16:2]
    tex("item/airlock_door", icon)

    # Space suit worn texture and equipment asset.
    tex("entity/equipment/humanoid/space_suit", px.suit_equipment(False))
    tex("entity/equipment/humanoid_leggings/space_suit", px.suit_equipment(True))
    write_json(os.path.join(A, "equipment", "space_suit.json"), {"layers": {
        "humanoid": [{"texture": f"{NS}:space_suit"}],
        "humanoid_leggings": [{"texture": f"{NS}:space_suit"}]}})

    px.save(px.icon(), os.path.join(A, "icon.png"))


# ============================================================================================== loot

SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}
SURVIVES = {"condition": "minecraft:survives_explosion"}


def self_drop(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:{name}"}], "conditions": [SURVIVES]}],
        "random_sequence": f"{NS}:blocks/{name}"}


def ore_drop(name, item, count=None, formula="ore_drops"):
    fns = []
    if count:
        fns.append({"function": "minecraft:set_count", "add": False,
                    "count": {"type": "minecraft:uniform", "min": float(count[0]), "max": float(count[1])}})
    bonus = {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": f"minecraft:{formula}"}
    if formula == "uniform_bonus_count":
        bonus["parameters"] = {"bonusMultiplier": 1}
    fns.append(bonus)
    fns.append({"function": "minecraft:explosion_decay"})
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{name}", "conditions": [SILK]},
            {"type": "minecraft:item", "name": item, "functions": fns}]}]}],
        "random_sequence": f"{NS}:blocks/{name}"}


def silk_or(name, item, count, extra=None):
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{name}", "conditions": [SILK]},
            {"type": "minecraft:item", "name": item, "functions": [
                {"function": "minecraft:set_count", "add": False,
                 "count": {"type": "minecraft:uniform", "min": float(count[0]), "max": float(count[1])}},
                {"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{NS}:blocks/{name}"}


def loot():
    L = os.path.join(D, "loot_table", "blocks")
    special = {
        "lunar_titanium_ore": ore_drop("lunar_titanium_ore", f"{NS}:raw_titanium"),
        "mars_cobalt_ore": ore_drop("mars_cobalt_ore", f"{NS}:raw_cobalt"),
        "mercury_iridium_ore": ore_drop("mercury_iridium_ore", f"{NS}:raw_iridium"),
        "venus_sulfur_ore": ore_drop("venus_sulfur_ore", f"{NS}:sulfur", (2, 4)),
        "helionite_ore": ore_drop("helionite_ore", f"{NS}:helionite_shard", (1, 2)),
        "storm_crystal_ore": ore_drop("storm_crystal_ore", f"{NS}:storm_crystal", (1, 2)),
        "cryonite_ore": ore_drop("cryonite_ore", f"{NS}:cryonite_crystal", (1, 3)),
        "pyrocite_ore": ore_drop("pyrocite_ore", f"{NS}:pyrocite_crystal", (1, 3)),
        "methane_clathrate_ore": ore_drop("methane_clathrate_ore", f"{NS}:methane_crystal", (2, 4)),
        "plutonite_ore": ore_drop("plutonite_ore", f"{NS}:plutonite"),
        "alien_coal_ore": ore_drop("alien_coal_ore", "minecraft:coal"),
        "alien_iron_ore": ore_drop("alien_iron_ore", "minecraft:raw_iron"),
        "alien_copper_ore": ore_drop("alien_copper_ore", "minecraft:raw_copper", (2, 5)),
        "alien_gold_ore": ore_drop("alien_gold_ore", "minecraft:raw_gold"),
        "alien_redstone_ore": ore_drop("alien_redstone_ore", "minecraft:redstone", (4, 5), "uniform_bonus_count"),
        "alien_lapis_ore": ore_drop("alien_lapis_ore", "minecraft:lapis_lazuli", (4, 9)),
        "alien_diamond_ore": ore_drop("alien_diamond_ore", "minecraft:diamond"),
        "alien_emerald_ore": ore_drop("alien_emerald_ore", "minecraft:emerald"),
        "moon_cheese_block": silk_or("moon_cheese_block", f"{NS}:moon_cheese", (2, 4)),
        "sulfur_block": silk_or("sulfur_block", f"{NS}:sulfur", (4, 9)),
    }
    # Exotic ores are handled in code (the planet decides the mineral); plasma cannot be picked up.
    empty = {"type": "minecraft:block", "pools": []}
    for n in ("exotic_ore", "deep_exotic_ore", "solar_plasma"):
        special[n] = dict(empty, random_sequence=f"{NS}:blocks/{n}")
    special["xeno_leaves"] = {"type": "minecraft:block", "pools": [
        {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:xeno_leaves",
             "conditions": [{"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}]},
            {"type": "minecraft:item", "name": f"{NS}:glow_spore", "conditions": [
                SURVIVES, {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                           "chances": [0.05, 0.0625, 0.083333336, 0.1]}]}]}]},
        {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:inverted", "term": {
            "condition": "minecraft:any_of", "terms": [SHEARS, SILK]}}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:stick", "functions": [
             {"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}},
             {"function": "minecraft:explosion_decay"}], "conditions": [
             {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
              "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}]}]}],
        "random_sequence": f"{NS}:blocks/xeno_leaves"}
    special["xeno_grass"] = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:xeno_grass", "conditions": [SHEARS]}]}],
        "random_sequence": f"{NS}:blocks/xeno_grass"}
    special["crystal_shard"] = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:crystal_shard", "conditions": [SILK]},
            {"type": "minecraft:item", "name": f"{NS}:storm_crystal", "conditions": [
                {"condition": "minecraft:random_chance", "chance": 0.25}], "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{NS}:blocks/crystal_shard"}
    special["airlock_door"] = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:airlock_door", "conditions": [
            {"condition": "minecraft:block_state_property", "block": f"{NS}:airlock_door", "properties": {"half": "lower"}}]}],
        "conditions": [SURVIVES]}], "random_sequence": f"{NS}:blocks/airlock_door"}
    for name in BLOCKS:
        write_json(os.path.join(L, name + ".json"), special.get(name) or self_drop(name))


# ============================================================================================== recipes

def I(n):
    return n if ":" in n else f"{NS}:{n}"


def shaped(name, pattern, key, result, count=1, category="misc"):
    return name, {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                  "key": {k: I(v) if not v.startswith("#") else v for k, v in key.items()},
                  "result": {"id": I(result), "count": count}}


def shapeless(name, ingredients, result, count=1, category="misc"):
    return name, {"type": "minecraft:crafting_shapeless", "category": category,
                  "ingredients": [I(i) if not i.startswith("#") else i for i in ingredients],
                  "result": {"id": I(result), "count": count}}


def cooking(name, kind, ingredient, result, xp, time, category="misc"):
    return name, {"type": f"minecraft:{kind}", "category": category, "ingredient": I(ingredient),
                  "result": {"id": I(result)}, "experience": xp, "cookingtime": time}


def recipes():
    R = []
    for metal in ("titanium", "cobalt", "iridium"):
        R.append(cooking(f"{metal}_ingot_from_smelting", "smelting", f"raw_{metal}", f"{metal}_ingot", 0.7, 200))
        R.append(cooking(f"{metal}_ingot_from_blasting", "blasting", f"raw_{metal}", f"{metal}_ingot", 0.7, 100))
        R.append(shaped(f"{metal}_block", ["###", "###", "###"], {"#": f"{metal}_ingot"}, f"{metal}_block", category="building"))
        R.append(shapeless(f"{metal}_ingot_from_block", [f"{metal}_block"], f"{metal}_ingot", 9))
    R.append(shaped("sulfur_block", ["###", "###", "###"], {"#": "sulfur"}, "sulfur_block", category="building"))
    R.append(shapeless("sulfur_from_block", ["sulfur_block"], "sulfur", 9))
    R.append(shapeless("gunpowder_from_sulfur", ["sulfur", "sulfur", "minecraft:charcoal"], "minecraft:gunpowder", 3))
    R.append(cooking("cooked_xeno_meat", "smelting", "xeno_meat", "cooked_xeno_meat", 0.35, 200, "food"))
    R.append(cooking("cooked_xeno_meat_from_smoking", "smoking", "xeno_meat", "cooked_xeno_meat", 0.35, 100, "food"))
    R.append(cooking("cooked_xeno_meat_from_campfire_cooking", "campfire_cooking", "xeno_meat", "cooked_xeno_meat", 0.35, 600, "food"))
    R.append(cooking("glass_from_alien_sand", "smelting", "alien_sand", "minecraft:glass", 0.1, 200, "blocks"))
    R.append(cooking("glass_from_mars_sand", "smelting", "mars_sand", "minecraft:glass", 0.1, 200, "blocks"))
    R.append(cooking("leather_from_xeno_hide", "smelting", "xeno_hide", "minecraft:leather", 0.1, 200))
    R.append(shaped("polished_moon_rock", ["##", "##"], {"#": "moon_rock"}, "polished_moon_rock", 4, "building"))
    R.append(shaped("moon_rock_bricks", ["##", "##"], {"#": "polished_moon_rock"}, "moon_rock_bricks", 4, "building"))
    R.append(shaped("mars_rock_bricks", ["##", "##"], {"#": "mars_rock"}, "mars_rock_bricks", 4, "building"))
    R.append(shaped("alien_stone_bricks", ["##", "##"], {"#": "alien_stone"}, "alien_stone_bricks", 4, "building"))
    R.append(shapeless("xeno_planks", ["xeno_log"], "xeno_planks", 4, "building"))
    R.append(shaped("moon_cheese_block", ["##", "##"], {"#": "moon_cheese"}, "moon_cheese_block", category="building"))
    R.append(shapeless("glowing_crystal_block", ["crystal_block", "minecraft:glowstone_dust"], "glowing_crystal_block", category="building"))
    # Ship parts.
    R.append(shaped("hull_plating", ["TI", "IT"], {"T": "titanium_ingot", "I": "minecraft:iron_ingot"}, "hull_plating", 4, "building"))
    R.append(shaped("hull_plating_dark", ["###", "#D#", "###"], {"#": "hull_plating", "D": "minecraft:black_dye"}, "hull_plating_dark", 8, "building"))
    R.append(shaped("hull_stripe", ["###", "#D#", "###"], {"#": "hull_plating", "D": "minecraft:orange_dye"}, "hull_stripe", 8, "building"))
    R.append(shaped("ship_floor", ["BB", "TT"], {"B": "minecraft:iron_bars", "T": "titanium_ingot"}, "ship_floor", 4, "building"))
    R.append(shapeless("ship_light", ["hull_plating", "minecraft:glowstone"], "ship_light", 1, "building"))
    R.append(shaped("reinforced_glass", ["GTG", "TGT", "GTG"], {"G": "minecraft:glass", "T": "titanium_ingot"}, "reinforced_glass", 5, "building"))
    R.append(shaped("airlock_door", ["TT", "TT", "TT"], {"T": "titanium_ingot"}, "airlock_door", 3, "redstone"))
    R.append(shaped("thruster", ["CIC", "CBC", "I I"], {"C": "cobalt_ingot", "I": "minecraft:iron_ingot", "B": "minecraft:blast_furnace"}, "thruster"))
    R.append(shaped("navigation_console", ["GGG", "RCR", "TTT"], {"G": "minecraft:glass", "R": "minecraft:redstone",
                                                                    "C": "minecraft:compass", "T": "titanium_ingot"}, "navigation_console"))
    R.append(shaped("life_support", ["TGT", "LCL", "TRT"], {"T": "titanium_ingot", "G": "minecraft:glass", "L": "#minecraft:leaves",
                                                            "C": "minecraft:copper_block", "R": "minecraft:redstone"}, "life_support"))
    R.append(shaped("thermal_armor_table", ["WCI", "PPP", "P P"], {"W": "#minecraft:wool", "C": "minecraft:crafting_table",
                                                                   "I": "minecraft:ice", "P": "#minecraft:planks"}, "thermal_armor_table"))
    R.append(shaped("stellar_forge", ["IDI", "OAO", "OOO"], {"I": "iridium_ingot", "D": "minecraft:diamond",
                                                             "A": "minecraft:anvil", "O": "minecraft:obsidian"}, "stellar_forge"))
    R.append(shaped("rocket_fuel_canister", ["ICI", "CBC", "ICI"], {"I": "minecraft:iron_ingot", "C": "minecraft:coal_block",
                                                                    "B": "minecraft:blaze_powder"}, "rocket_fuel_canister"))
    R.append(shaped("fuel_block", ["###", "###", "###"], {"#": "rocket_fuel_canister"}, "fuel_block", category="building"))
    R.append(shapeless("rocket_fuel_canister_from_fuel_block", ["fuel_block"], "rocket_fuel_canister", 9))
    R.append(shaped("rocket_blueprint", ["IPI", "PRP", "IPI"], {"I": "minecraft:iron_block", "P": "minecraft:paper",
                                                                "R": "minecraft:redstone_block"}, "rocket_blueprint"))
    R.append(shaped("starship_blueprint", ["TPT", "CHC", "IPI"], {"T": "titanium_block", "P": "minecraft:paper", "C": "cobalt_block",
                                                                  "H": "helionite_shard", "I": "iridium_block"}, "starship_blueprint"))
    R.append(shaped("thermometer", ["G", "R", "C"], {"G": "minecraft:glass", "R": "minecraft:redstone", "C": "minecraft:copper_ingot"},
                    "thermometer", category="equipment"))
    R.append(shaped("space_helmet", ["TTT", "TGT"], {"T": "titanium_ingot", "G": "minecraft:glass"}, "space_helmet", category="equipment"))
    R.append(shaped("space_chestplate", ["T T", "TWT", "TTT"], {"T": "titanium_ingot", "W": "#minecraft:wool"}, "space_chestplate",
                    category="equipment"))
    R.append(shaped("space_leggings", ["TTT", "T T", "T T"], {"T": "titanium_ingot"}, "space_leggings", category="equipment"))
    R.append(shaped("space_boots", ["T T", "T T"], {"T": "titanium_ingot"}, "space_boots", category="equipment"))
    R.append(shapeless("thermal_fiber_from_wool", ["#minecraft:wool", "#minecraft:wool", "pyrocite_crystal"], "thermal_fiber", 4))
    for name, obj in R:
        write_json(os.path.join(D, "recipe", name + ".json"), obj)
    return len(R)


# ============================================================================================== tags, registries

def tags():
    def t(path, values, ns_root=DM):
        write_json(os.path.join(ns_root, "tags", path + ".json"), {"replace": False, "values": values})

    def g(names):
        return [f"{NS}:{n}" for n in names]

    shovel = ["moon_regolith", "mars_sand", "venus_regolith", "mercury_dust", "io_ash", "titan_sediment", "tholin_dust", "alien_grass",
              "alien_soil", "alien_sand", "alien_regolith", "fungal_turf", "toxic_turf", "ash_block", "europa_frost"]
    axe = ["xeno_log", "xeno_planks", "thermal_armor_table"]
    hoe = ["xeno_leaves", "moon_cheese_block", "jovian_cloud"]
    pick = [n for n, b in BLOCKS.items()
            if n not in shovel + axe + hoe and b["kind"] not in ("cross", "tcross") and n not in ("solar_plasma",)]
    t("block/mineable/pickaxe", g(pick))
    t("block/mineable/shovel", g(shovel))
    t("block/mineable/axe", g(axe))
    t("block/mineable/hoe", g(hoe))
    t("block/needs_stone_tool", g(["lunar_titanium_ore", "venus_sulfur_ore", "methane_clathrate_ore", "alien_iron_ore", "alien_copper_ore",
                                   "alien_lapis_ore", "titanium_block", "hull_plating", "hull_plating_dark", "hull_stripe", "ship_floor",
                                   "airlock_door", "navigation_console", "life_support", "fuel_block"]))
    t("block/needs_iron_tool", g(["mars_cobalt_ore", "mercury_iridium_ore", "storm_crystal_ore", "cryonite_ore", "pyrocite_ore",
                                  "exotic_ore", "alien_gold_ore", "alien_redstone_ore", "alien_diamond_ore", "alien_emerald_ore",
                                  "cobalt_block", "iridium_block", "stellar_forge", "thruster"]))
    t("block/needs_diamond_tool", g(["helionite_ore", "plutonite_ore", "deep_exotic_ore"]))
    t("block/logs_that_burn", g(["xeno_log"]))
    t("block/logs", g(["xeno_log"]))
    t("block/leaves", g(["xeno_leaves"]))
    t("block/planks", g(["xeno_planks"]))
    t("block/doors", g(["airlock_door"]))
    t("block/dirt", g(["alien_soil", "alien_grass", "fungal_turf", "toxic_turf"]))
    t("block/beacon_base_blocks", g(["titanium_block", "cobalt_block", "iridium_block"]))
    t("block/crystal_sound_blocks", g(["crystal_block", "glowing_crystal_block"]))
    t("item/logs_that_burn", g(["xeno_log"]))
    t("item/logs", g(["xeno_log"]))
    t("item/leaves", g(["xeno_leaves"]))
    t("item/planks", g(["xeno_planks"]))
    t("item/doors", g(["airlock_door"]))
    t("item/dirt", g(["alien_soil", "alien_grass", "fungal_turf", "toxic_turf"]))
    t("item/beacon_payment_items", g(["titanium_ingot", "cobalt_ingot", "iridium_ingot"]))
    t("item/meat", g(["xeno_meat", "cooked_xeno_meat"]))
    suit = ["space_helmet", "space_chestplate", "space_leggings", "space_boots"]
    for tag in ("enchantable/armor", "enchantable/equippable", "enchantable/durability", "trimmable_armor"):
        t("item/" + tag, g(suit))
    for piece, slot in zip(suit, ("head", "chest", "leg", "foot")):
        t(f"item/{slot}_armor", g([piece]))
        t(f"item/enchantable/{slot}_armor", g([piece]))
    t("item/fuel_enchantable", ["minecraft:coal", "minecraft:charcoal", "minecraft:coal_block", "minecraft:blaze_rod"]
      + g(["rocket_fuel_canister", "fuel_block", "methane_crystal", "plutonite"]), D)
    t("item/repairs_space_suit", g(["titanium_ingot"]), D)
    t("enchantment/in_enchanting_table", [f"{NS}:fuel_efficiency"])
    t("enchantment/non_treasure", [f"{NS}:fuel_efficiency"])
    t("damage_type/bypasses_armor", [f"{NS}:hypothermia", f"{NS}:heatstroke"])
    t("damage_type/bypasses_shield", [f"{NS}:hypothermia", f"{NS}:heatstroke"])
    t("damage_type/no_knockback", [f"{NS}:hypothermia", f"{NS}:heatstroke"])


def registries():
    # Fuel Efficiency I-V. Costs are set so level V appears at a 30-level table with full bookshelves;
    # each level doubles the multiplier (x4 ... x64), so a stack of 64 coal at V burns like 4096.
    write_json(os.path.join(D, "enchantment", "fuel_efficiency.json"), {
        "description": {"translate": f"enchantment.{NS}.fuel_efficiency"},
        "supported_items": f"#{NS}:fuel_enchantable",
        "primary_items": f"#{NS}:fuel_enchantable",
        "weight": 10,
        "max_level": 5,
        "min_cost": {"base": 1, "per_level_above_first": 7},
        "max_cost": {"base": 51, "per_level_above_first": 7},
        "anvil_cost": 2,
        "slots": ["any"],
        "effects": {},
    })
    write_json(os.path.join(D, "damage_type", "hypothermia.json"), {
        "message_id": f"{NS}.hypothermia", "exhaustion": 0.0, "scaling": "never", "effects": "freezing"})
    write_json(os.path.join(D, "damage_type", "heatstroke.json"), {
        "message_id": f"{NS}.heatstroke", "exhaustion": 0.0, "scaling": "never", "effects": "burning"})


# ============================================================================================== lang

NAMES = {
    "polished_moon_rock": "Polished Moon Rock", "moon_rock_bricks": "Moon Rock Bricks", "mars_rock_bricks": "Mars Rock Bricks",
    "venus_sulfur_ore": "Venusian Sulfur Ore", "mercury_iridium_ore": "Mercurian Iridium Ore", "mars_cobalt_ore": "Martian Cobalt Ore",
    "io_sulfur_rock": "Io Sulfur Rock", "io_ash": "Io Ash", "hull_plating_dark": "Dark Hull Plating", "hull_stripe": "Striped Hull Plating",
    "exotic_ore": "Exotic Ore", "deep_exotic_ore": "Deep Exotic Ore", "space_helmet": "Space Suit Helmet",
    "space_chestplate": "Space Suit Chestplate", "space_leggings": "Space Suit Leggings", "space_boots": "Space Suit Boots",
    "relic": "Ancient Relic", "exotic_mineral": "Exotic Mineral", "glow_shroom": "Glowshroom", "worldeater_fang": "Worldeater Fang",
    "rocket_fuel_canister": "Rocket Fuel Canister", "bio_gel": "Bio-Gel", "cooked_xeno_meat": "Cooked Xeno Meat",
}


def title(name):
    return NAMES.get(name) or " ".join(w.capitalize() for w in name.split("_"))


STRINGS = {
    "itemGroup.galaxy_mc": "Galaxy MC",
    "enchantment.galaxy_mc.fuel_efficiency": "Fuel Efficiency",
    "death.attack.galaxy_mc.hypothermia": "%1$s froze solid on an alien world",
    "death.attack.galaxy_mc.hypothermia.player": "%1$s froze solid while fleeing %2$s",
    "death.attack.galaxy_mc.heatstroke": "%1$s was cooked by an alien sun",
    "death.attack.galaxy_mc.heatstroke.player": "%1$s was cooked by the heat while fighting %2$s",
    "container.galaxy_mc.rocket": "Rocket",
    "container.galaxy_mc.starship": "Starship",
    "container.galaxy_mc.stellar_forge": "Stellar Forge",
    "container.galaxy_mc.thermal_armor_table": "Thermal Armor Table",
    "hud.galaxy_mc.life_support": "Life support",
    "hud.galaxy_mc.warp": "Flying to %s",
    "hud.galaxy_mc.warp_interstellar": "Warping to %s",
    "message.galaxy_mc.blueprint.blocked": "Not enough room - blocked at %s, %s, %s",
    "message.galaxy_mc.blueprint.built": "%s assembled. Fuel it at the navigation console.",
    "message.galaxy_mc.console_no_ship": "This console isn't part of a ship. Build one from a blueprint.",
    "message.galaxy_mc.freezing": "You are freezing! Stitch wool into your armour at a Thermal Armor Table.",
    "message.galaxy_mc.overheating": "You are overheating! Stitch ice into your armour at a Thermal Armor Table.",
    "message.galaxy_mc.launch.already_here": "You are already there.",
    "message.galaxy_mc.launch.arrived": "Arrived at %s.",
    "message.galaxy_mc.launch.countdown": "Launch in %s...",
    "message.galaxy_mc.launch.no_fuel": "Not enough fuel: the jump needs %s coal, the tank holds %s.",
    "message.galaxy_mc.launch.rocket_range": "A rocket can't leave the solar system - build a starship.",
    "message.galaxy_mc.launch.unknown": "Unknown destination.",
    "message.galaxy_mc.thermometer.home": "Earth: comfortable. No suit needed.",
    "message.galaxy_mc.thermometer.reading": "Ambient %s°C | suit rated %s to %s°C",
    "message.galaxy_mc.thermometer.safe": "You're safe here.",
    "message.galaxy_mc.thermometer.sheltered": "Sheltered by life support.",
    "message.galaxy_mc.thermometer.too_cold": "Too cold: add %s warmth.",
    "message.galaxy_mc.thermometer.too_hot": "Too hot: add %s cooling.",
    "screen.galaxy_mc.forge.armor_only": "That trait only works on armour",
    "screen.galaxy_mc.forge.cost": "Cost: %s levels",
    "screen.galaxy_mc.forge.full": "No room for another infusion",
    "screen.galaxy_mc.forge.volatile": "Volatile minerals are fuel, not infusions",
    "screen.galaxy_mc.nav.danger": "Danger %s",
    "screen.galaxy_mc.nav.filter.all": "All",
    "screen.galaxy_mc.nav.filter.habitable": "Habitable",
    "screen.galaxy_mc.nav.filter.hot": "Hot",
    "screen.galaxy_mc.nav.filter.cold": "Cold",
    "screen.galaxy_mc.nav.filter.water": "Water",
    "screen.galaxy_mc.nav.filter.rocky": "Rocky",
    "screen.galaxy_mc.nav.filter.giants": "Giants",
    "screen.galaxy_mc.nav.filter.stars": "Stars",
    "screen.galaxy_mc.nav.filter.rich": "Rich ores",
    "screen.galaxy_mc.nav.fuel_needed": "Fuel: %s coal",
    "screen.galaxy_mc.nav.intake": "Fuel intake",
    "screen.galaxy_mc.nav.intake_value": "Worth %s coal",
    "screen.galaxy_mc.nav.launch": "Launch",
    "screen.galaxy_mc.nav.launching": "Launching...",
    "screen.galaxy_mc.nav.need_starship": "Needs a starship",
    "screen.galaxy_mc.nav.nothing": "No destinations match.",
    "screen.galaxy_mc.nav.range.system": "System",
    "screen.galaxy_mc.nav.range.near": "Nearby",
    "screen.galaxy_mc.nav.range.far": "Far",
    "screen.galaxy_mc.nav.search": "Search",
    "screen.galaxy_mc.nav.search_hint": "Search by name...",
    "screen.galaxy_mc.nav.short": "Short by %s coal",
    "screen.galaxy_mc.nav.sort.fuel": "Fuel",
    "screen.galaxy_mc.nav.sort.distance": "Distance",
    "screen.galaxy_mc.nav.sort.danger": "Danger",
    "screen.galaxy_mc.nav.sort.name": "Name",
    "screen.galaxy_mc.nav.tank": "Fuel tank",
    "screen.galaxy_mc.nav.tier": "Tier %s",
    "screen.galaxy_mc.thermal.cool": "Cooling",
    "screen.galaxy_mc.thermal.strip": "Strip",
    "screen.galaxy_mc.thermal.suit": "Suit: %s",
    "screen.galaxy_mc.thermal.warm": "Warming",
    "tooltip.galaxy_mc.cooling": "+%s cooling",
    "tooltip.galaxy_mc.warmth": "+%s warmth",
    "tooltip.galaxy_mc.infused": "Infused: %s (%s)",
    "tooltip.galaxy_mc.mineral_tier": "Tier %s mineral",
    "tooltip.galaxy_mc.relic_level": "Power %s | recharge %ss",
}


def lang():
    path = os.path.join(A, "lang", "en_us.json")
    existing = json.load(open(path)) if os.path.exists(path) else {}
    out = {k: v for k, v in existing.items() if k.startswith("entity.") or k.endswith("_spawn_egg")}
    for name in BLOCKS:
        out[f"block.{NS}.{name}"] = title(name)
    for name in list(ITEM_ART) + ["exotic_mineral", "relic"]:
        out[f"item.{NS}.{name}"] = title(name)
    out.update(STRINGS)
    write_json(path, dict(sorted(out.items())))
    return out


def main():
    define_blocks()
    parent_models()
    block_tex = paint_blocks()
    block_models_and_states()
    items(block_tex)
    loot()
    n = recipes()
    tags()
    registries()
    out = lang()
    print(f"{len(BLOCKS)} blocks, {len(ITEM_ART) + 2} items, {n} recipes, {len(out)} lang keys")


if __name__ == "__main__":
    main()
