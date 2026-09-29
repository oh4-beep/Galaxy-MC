# Galaxy MC

A Fabric mod for Minecraft **26.2**. Build a rocket, leave Earth, and explore the solar system. Then build a starship and warp into an endless procedural galaxy.

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Loom | 1.18.2 |
| Java | 25 |

## What's in it

- **Sol:** the Moon, Mars, Venus, Mercury, the Sun, Jupiter, Europa, Io, Titan, Saturn's rings and Pluto. Each body has its own blocks, ores and climate.
- **The frontier:** millions of generated star systems.
  - Planets come in 18 archetypes, from ice, ocean, jungle, fungal and toxic worlds to lava, ash, crystal, canyon, shattered worlds, gas-giant cloudlands and stellar coronas. Their terrain uses noise-driven mountain ranges, cliffs, strata, and sealed water, lava, ice and salt lakes.
  - Colours are tinted per planet, so no two worlds look alike.
  - Each world has four procedural minerals, and richer tiers appear the further you travel.
- **Ships:**
  - **Placing ships.** A blueprint places a full walk-in ship. The rocket covers the solar system; the starship makes interstellar jumps. Both were modelled in Blender and voxelised (`tools/blender`).
  - **Navigation console.** Search destinations by name, filter them (habitable, hot, cold, water, rocky, giants, stars, rich), sort by fuel, distance, danger or name, and see the exact coal cost of each jump.
  - **Fuel.** Coal, charcoal, coal blocks, lava buckets, blaze rods, rocket fuel canisters and fuel blocks all work.
- **Fuel Efficiency I–V:** apply it at an ordinary enchanting table. The table accepts a **whole stack** of fuel at once. Each level doubles the multiplier (×4 … ×64), so 64 coal at Fuel Efficiency V burns like 4096.
- **Temperature survival:**
  - At the Thermal Armor Table, stitch wool into armour for warmth and ice for cooling. Colder or hotter worlds need more of it.
  - Leaving your suit's range causes hypothermia or heatstroke damage.
  - The thermometer tells you where you stand, and life support blocks make safe zones.
- **Bestiary: 153 alien species.**
  - Walkers, flyers, floaters, ambushers and spitters, with coloured strains.
  - Giants that shake the ground.
  - Segmented worms, from small to world-eating. They burrow, surface or swim.
- **Relics and infusions:** ancient relics with active powers turn up in ruins. The Stellar Forge infuses gear with the traits of exotic minerals.

## Progression

1. **Leave Earth.**
   - Craft a **Rocket Blueprint** from iron blocks, paper and a redstone block, and use it on open ground.
   - Load coal into the navigation console and launch for the Moon.
2. **Survive the temperatures.**
   - Craft a **Thermal Armor Table** and a **Thermometer**.
   - Before landing somewhere extreme, stitch wool or ice into your armour.
3. **Gather Sol's metals.**
   - Titanium from the Moon, cobalt from Mars and iridium from Mercury.
   - Helionite from the Sun, where you'll need a diamond pickaxe and a lot of ice.
4. **Go interstellar.** Craft a **Starship Blueprint** from titanium, cobalt and iridium blocks plus a helionite shard. The frontier is open.

Handy commands (operators):
- `/galaxy info`, `/galaxy nearby`, `/galaxy fauna`
- `/galaxy tp <body>`, `/galaxy tp frontier <sx> <sz> <slot> <orbit>`
- `/galaxy spawn <species>`, `/galaxy random`

## Building

```
./gradlew build        # jar in build/libs
./gradlew runClient
```

- `gradle.properties` pins `org.gradle.java.home` to a Homebrew JDK 25 path. Change or remove that line on other machines.
- Loom needs `maven.fabricmc.net` and Mojang's servers (`piston-meta`, `piston-data`, `libraries.minecraft.net`, `resources.download.minecraft.net`).

## Tools

All tools are deterministic and re-runnable from the repository root.

| Tool | What it does |
|---|---|
| `tools/assets/gen_assets.py` | Procedural block and item textures, models, blockstates, item definitions, loot tables, recipes, tags, the enchantment, damage types, the space-suit equipment asset and English names. Needs Pillow and numpy. |
| `tools/mobs/gen_mobs.py` | The bestiary. Species, body plans, creature models and textures, spawn eggs, loot and names. `render_mobs.py` renders line-ups. |
| `tools/blender/build_ships.py` | Models the rocket and starship in Blender (`bpy`) and voxelises them into `data/galaxy_mc/ships/*.json`. `render_ships.py` renders them. |
| `tools/terrain-preview/run.sh` | Runs the real terrain code offline against stubbed Minecraft classes. Modes: `list`, `sheet`, `map`, `view`, `check`, `heights`. It needs only a JDK. |
| `tools/api-check/check_imports.py` | Cross-checks imports against Fabric API and NeoForge 26.2 sources. |

## Status

- The code was written without access to the Minecraft or Fabric Maven servers, so it has **not yet been compiled against 26.2**.
- What has been verified:
  - Imports were checked against the Fabric API 0.161 and NeoForge 26.2 sources.
  - All sources parse under `javac`, and every reference between the mod's own classes resolves.
  - Terrain was tested in the offline harness.
  - Ship blueprints were checked for reachability and seals.
  - Asset and data references were checked for consistency.
- The first `./gradlew build` may still surface a few renamed vanilla members. Those are one-line fixes.
