#!/usr/bin/env python3
"""Generates Galaxy MC's worldgen datapack: dimension types, dimensions, biomes and the planet timeline.

Run from the repository root:  python3 tools/gen_worldgen.py [path/to/vanilla/data/minecraft]
The optional argument points at vanilla's extracted data folder; it is used to derive the planet
day/night timeline from minecraft:day so our skies track the vanilla sun exactly.
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "galaxy_mc")
NS = "galaxy_mc"


def write(rel, obj):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def empty_spawners():
    return {k: [] for k in ["ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature",
                            "water_ambient", "water_creature"]}


# Planet worlds are 576 blocks tall (y -64 to 511) so mountain ranges and volcanoes have room to rise.
WORLD_HEIGHT = 576
# Night skies on other worlds are twice as starry as Earth's.
STAR_BOOST = 2.0


def biome(sky, fog, water="#3f76e4", water_fog=None, stars=0.0, particles=None, fog_end=None, fog_start=None,
          precipitation=False, temperature=0.8, clouds=None, cloud_height=None, sunrise=None, downfall=None):
    attrs = {
        "minecraft:visual/sky_color": sky,
        "minecraft:visual/fog_color": fog,
    }
    if stars:
        attrs["minecraft:visual/star_brightness"] = stars
    if particles:
        attrs["minecraft:visual/ambient_particles"] = [
            {"particle": {"type": p}, "probability": prob} for p, prob in particles
        ]
    if fog_end is not None:
        attrs["minecraft:visual/fog_end_distance"] = fog_end
    if fog_start is not None:
        attrs["minecraft:visual/fog_start_distance"] = fog_start
    if clouds:
        attrs["minecraft:visual/cloud_color"] = clouds
    if cloud_height is not None:
        attrs["minecraft:visual/cloud_height"] = cloud_height
    if water_fog:
        attrs["minecraft:visual/water_fog_color"] = water_fog
    if sunrise:
        attrs["minecraft:visual/sunrise_sunset_color"] = sunrise
    return {
        "has_precipitation": precipitation,
        "temperature": temperature,
        "downfall": downfall if downfall is not None else (0.4 if precipitation else 0.0),
        "carvers": [],
        "effects": {"water_color": water},
        "attributes": attrs,
        "spawn_costs": {},
        "spawners": empty_spawners(),
        "features": [[] for _ in range(11)],
    }


SOL_BIOMES = {
    "moon": biome("#000000", "#050507", stars=1.0),
    "mars": biome("#c7926a", "#b0724a", particles=[("minecraft:white_ash", 0.004)], fog_end=260,
                  sunrise="#7090c0ff"),
    "venus": biome("#d8b25a", "#c8a048", particles=[("minecraft:ash", 0.02)], fog_start=6, fog_end=96,
                   clouds="#c8e0c070", cloud_height=150, precipitation=True, water="#c0a030"),
    "mercury": biome("#000000", "#080605", stars=1.0),
    "sun": biome("#ff8a1e", "#ff5a10", particles=[("minecraft:flame", 0.004), ("minecraft:lava", 0.002)],
                 fog_start=0, fog_end=128),
    "jupiter": biome("#d9b48a", "#c9985a", particles=[("minecraft:white_ash", 0.006)], clouds="#f0d8b0d0",
                     cloud_height=40, fog_end=300),
    "europa": biome("#000000", "#0a1020", stars=1.0, particles=[("minecraft:snowflake", 0.002)],
                    water="#1a3060", water_fog="#0a1830"),
    "io": biome("#05030a", "#1a1206", stars=0.85, particles=[("minecraft:ash", 0.008)]),
    "titan": biome("#c8782a", "#a86020", fog_start=4, fog_end=110, water="#8a5a20", water_fog="#5a3a10",
                   precipitation=True, clouds="#d8903aa0", cloud_height=128),
    "saturn": biome("#000000", "#04040a", stars=1.0, particles=[("minecraft:end_rod", 0.0008)]),
    "pluto": biome("#000000", "#0c0a10", stars=1.0, particles=[("minecraft:snowflake", 0.001)]),
}

# Order must match FrontierPlanets.SKIES.
FRONTIER_SKIES = [
    ("deep_space", biome("#000000", "#000000", stars=1.0)),
    ("azure", biome("#78a7ff", "#c0d8ff", water="#3f76e4")),
    ("teal", biome("#5fd3c8", "#a8f0e8", water="#30b0a0")),
    ("emerald", biome("#5fe07a", "#b0f0c0", water="#40c070")),
    ("toxic", biome("#b8d83a", "#d0e060", water="#9ad83a", water_fog="#4a6a10",
                    particles=[("minecraft:warped_spore", 0.01)])),
    ("amber", biome("#e8a850", "#f0c888", water="#c09040")),
    ("crimson", biome("#c83a2a", "#8a2a20", water="#aa3030", particles=[("minecraft:crimson_spore", 0.01)])),
    ("rose", biome("#e890b0", "#f0c8d8", water="#d070a0")),
    ("violet", biome("#9a6ae0", "#c8a8f0", water="#7a4ad0", particles=[("minecraft:spore_blossom_air", 0.006)])),
    ("indigo", biome("#3a3aa0", "#6a6ad0", water="#3a3ad0", stars=0.4)),
    ("ashen", biome("#7a7470", "#9a948e", water="#6a6460", particles=[("minecraft:white_ash", 0.05)])),
    ("black", biome("#000000", "#06060a", stars=1.0)),
    ("white", biome("#e8f0ff", "#ffffff", water="#a0c8ff")),
    ("gold", biome("#e8c84a", "#f8e090", water="#d0b040")),
    ("inferno", biome("#ff6a1a", "#c8301a", water="#ff5020", particles=[("minecraft:flame", 0.004), ("minecraft:ash", 0.02)],
                      fog_start=0, fog_end=120)),
    ("snow", biome("#b8d0e8", "#d8e8f8", water="#80a8e0", particles=[("minecraft:snowflake", 0.02)],
                   precipitation=True, temperature=-0.5)),
    ("aurora", biome("#1a3a4a", "#2a6a6a", water="#20a0a0", stars=0.7, particles=[("minecraft:end_rod", 0.003)])),
    # Terran climates: temperature and downfall pick the vanilla grass and foliage colours, and whether
    # it rains or snows.
    ("terran", biome("#78a7ff", "#c0d8ff", precipitation=True, temperature=0.75, downfall=0.7)),
    ("terran_boreal", biome("#7ba4ff", "#c0d8ff", water="#3d57d6", precipitation=True, temperature=0.25, downfall=0.8)),
    ("terran_frozen", biome("#7fa1ff", "#d0e0ff", water="#3938c9", precipitation=True, temperature=0.0, downfall=0.5)),
    ("terran_arid", biome("#6eb1ff", "#e0d8b8", water="#44aff5", temperature=2.0, downfall=0.0)),
    ("terran_tropical", biome("#77a8ff", "#c0e0d0", water="#14a2c5", precipitation=True, temperature=0.95, downfall=0.9)),
    ("storm", biome("#4a5566", "#6a7484", water="#3a5a7a", precipitation=True, temperature=0.7, downfall=0.6,
                    clouds="#40485080", cloud_height=160, fog_end=280)),
    ("volcanic", biome("#5a2a20", "#3a2018", water="#5a4030", temperature=1.5,
                       particles=[("minecraft:ash", 0.03), ("minecraft:white_ash", 0.01), ("minecraft:lava", 0.0006)],
                       fog_start=8, fog_end=200, sunrise="#c04020ff")),
]


def planet_timeline(vanilla_dir):
    """minecraft:day, with stars allowed to shine through the day wherever a biome says so."""
    src = os.path.join(vanilla_dir, "timeline", "day.json")
    with open(src) as f:
        day = json.load(f)
    tracks = {}
    for key, track in day["tracks"].items():
        if key.startswith("minecraft:gameplay/") and key != "minecraft:gameplay/sky_light_level":
            continue
        if key.startswith("minecraft:audio/"):
            continue
        track = dict(track)
        if key == "minecraft:visual/star_brightness":
            track["modifier"] = "maximum"
            track["keyframes"] = boost_stars(track["keyframes"])
        tracks[key] = track
    return {
        "clock": "minecraft:overworld",
        "period_ticks": day["period_ticks"],
        "tracks": tracks,
    }


def boost_stars(keyframes):
    return [dict(k, value=round(min(1.0, k["value"] * STAR_BOOST), 3)) for k in keyframes]


def boost_existing_timeline():
    """Applies the star boost to an already generated timeline (when vanilla data is not at hand)."""
    path = os.path.join(ROOT, "timeline", "planet_day.json")
    with open(path) as f:
        timeline = json.load(f)
    track = timeline["tracks"]["minecraft:visual/star_brightness"]
    if max(k["value"] for k in track["keyframes"]) < 1.0:
        track["keyframes"] = boost_stars(track["keyframes"])
        write("timeline/planet_day.json", timeline)


def dimension_type(stellar=False):
    attrs = {
        "minecraft:gameplay/bed_rule": {
            "can_set_spawn": "always",
            "can_sleep": "when_dark",
            "error_message": {"translate": "block.minecraft.bed.no_sleep"},
        },
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:gameplay/can_start_raid": False,
        "minecraft:visual/ambient_light_color": "#0a0a0a",
        "minecraft:audio/background_music": {
            "default": {"max_delay": 24000, "min_delay": 9000, "sound": "minecraft:music.end"}
        },
    }
    dt = {
        "ambient_light": 0.0,
        "attributes": attrs,
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_skylight": True,
        "height": WORLD_HEIGHT,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": WORLD_HEIGHT,
        "min_y": -64,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
    }
    if stellar:
        dt["ambient_light"] = 0.7
        dt["has_fixed_time"] = True
        dt["skybox"] = "none"
        attrs["minecraft:gameplay/bed_rule"] = {"can_set_spawn": "never", "can_sleep": "never", "explodes": True}
        attrs["minecraft:visual/ambient_light_color"] = "#ff9a40"
    else:
        dt["default_clock"] = "minecraft:overworld"
        dt["timelines"] = [f"{NS}:planet_day"]
    return dt


def main():
    vanilla = sys.argv[1] if len(sys.argv) > 1 else None
    if vanilla:
        write("timeline/planet_day.json", planet_timeline(vanilla))
    else:
        boost_existing_timeline()
    write("dimension_type/planet.json", dimension_type())
    write("dimension_type/stellar.json", dimension_type(stellar=True))

    for name, b in SOL_BIOMES.items():
        write(f"worldgen/biome/{name}.json", b)
        write(f"dimension/{name}.json", {
            "type": f"{NS}:stellar" if name == "sun" else f"{NS}:planet",
            "generator": {
                "type": f"{NS}:planet",
                "planet": name,
                "biome_source": {"type": "minecraft:fixed", "biome": f"{NS}:{name}"},
            },
        })

    for name, b in FRONTIER_SKIES:
        write(f"worldgen/biome/frontier_{name}.json", b)
    write("dimension/frontier.json", {
        "type": f"{NS}:planet",
        "generator": {
            "type": f"{NS}:planet",
            "planet": "frontier",
            "biome_source": {"type": f"{NS}:frontier", "biomes": [f"{NS}:frontier_{n}" for n, _ in FRONTIER_SKIES]},
        },
    })
    print("worldgen data written")


if __name__ == "__main__":
    main()
