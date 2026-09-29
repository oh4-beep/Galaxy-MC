#!/usr/bin/env python3
"""Writes minimal stand-ins for the handful of Minecraft classes the terrain code touches.

The terrain pipeline (Galaxy -> FrontierPlanets -> TerrainShaper -> PlanetColumns) only needs block
identities, so a few tiny classes are enough to run it on a plain JDK, with no game, no Loom and no
network. Each stub block carries an approximate map colour so the previewer can paint it.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(ROOT, "src", "main", "java")
OUT = os.path.join(HERE, "build", "stubs")

# Approximate top-down colours (0xRRGGBB). Tinted alien blocks are light greys; the previewer
# multiplies them by the planet's tint exactly like the client's colour provider does.
COLORS = {
    "AIR": 0x000000, "WATER": 0x3a64c8, "LAVA": 0xff6a10, "BEDROCK": 0x3a3a3a, "STONE": 0x7d7d7d,
    "ANDESITE": 0x888888, "BASALT": 0x505055, "BLACKSTONE": 0x2e2a30, "BLUE_ICE": 0x74a8fd, "CALCITE": 0xdfe0dc,
    "CLAY": 0xa0a6b4, "DEEPSLATE": 0x4a4a50, "DIORITE": 0xbcbcbc, "DRIPSTONE_BLOCK": 0x866b5c, "GRANITE": 0x956754,
    "GRAVEL": 0x837f7e, "ICE": 0x91b7fd, "MAGMA_BLOCK": 0xc85a1a, "MUD": 0x3c3a3e, "MYCELIUM": 0x6f6265,
    "OBSIDIAN": 0x14121e, "PACKED_ICE": 0x8db4fa, "RED_SAND": 0xbe6621, "SANDSTONE": 0xd8cb9b, "SMOOTH_BASALT": 0x48484e,
    "SNOW_BLOCK": 0xf4fcfc, "TERRACOTTA": 0x985e43, "TUFF": 0x6c6d66,
    "DYED_TERRACOTTA_orange": 0xa2531f, "DYED_TERRACOTTA_yellow": 0xba8523, "DYED_TERRACOTTA_brown": 0x4d3323,
    "DYED_TERRACOTTA_red": 0x8f3d2e, "DYED_TERRACOTTA_white": 0xd1b2a1, "DYED_TERRACOTTA_lightGray": 0x876b62,
    # Galaxy MC blocks
    "ALIEN_STONE": 0xc8c8c8, "ALIEN_DEEP_STONE": 0x8a8a8a, "ALIEN_SAND": 0xe8e0cc, "ALIEN_REGOLITH": 0xb8b8b8,
    "ALIEN_SOIL": 0x9a8a78, "ALIEN_GRASS": 0xd0d0d0, "FUNGAL_TURF": 0x8a4ab0, "TOXIC_TURF": 0x9ad030,
    "ASH_BLOCK": 0x55504b, "SCORCHED_ROCK": 0x2a2624, "CRYSTAL_BLOCK": 0x80d0f0, "MOON_REGOLITH": 0x9a9a9a,
    "MOON_ROCK": 0x7a7a7a, "MOON_DEEP_ROCK": 0x505055, "MARS_SAND": 0xb5552c, "MARS_ROCK": 0x9a4a2a,
    "MARS_DEEP_ROCK": 0x6a2a1a, "MARS_POLAR_ICE": 0xe8eef0, "VENUS_BASALT": 0x5a4a38, "VENUS_REGOLITH": 0xb39a5e,
    "MERCURY_ROCK": 0x5a5550, "MERCURY_DUST": 0x6e6760, "SOLAR_SLAG": 0x2a1a10, "SOLAR_PLASMA": 0xffd040,
    "JOVIAN_CLOUD": 0xe8d8b8, "JOVIAN_STONE": 0xc8b898, "EUROPA_ICE": 0xc0dcf0, "EUROPA_FROST": 0xf0f8ff,
    "IO_SULFUR_ROCK": 0xd8c830, "IO_ASH": 0x4a4238, "TITAN_SEDIMENT": 0x8a5a2e, "TITAN_ROCK": 0x5a3a20,
    "RING_ICE": 0xe0e8f0, "RING_ROCK": 0x9a9a98, "NITROGEN_ICE": 0xf0e8e4, "PLUTO_ROCK": 0xa09890,
    "THOLIN_DUST": 0x7a3a2a, "SULFUR_BLOCK": 0xe0d040, "MOON_CHEESE_BLOCK": 0xf0d040,
}


def scan(pattern):
    found = set()
    for d, _, files in os.walk(SRC):
        for f in files:
            if f.endswith(".java"):
                with open(os.path.join(d, f)) as fh:
                    found.update(re.findall(pattern, fh.read()))
    return found


def write(rel, text):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(text)


def main():
    vanilla = sorted(scan(r"\bBlocks\.([A-Z_]+)\b") - {"DYED_TERRACOTTA"})
    dyed = sorted(scan(r"\bBlocks\.DYED_TERRACOTTA\.([a-zA-Z]+)\(\)"))
    ours = []
    with open(os.path.join(SRC, "com", "galaxymc", "registry", "ModBlocks.java")) as fh:
        ours = re.findall(r"public static final Block ([A-Z_]+)\s*=", fh.read())

    write("net/minecraft/world/level/block/state/BlockState.java", """package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;

public final class BlockState {
    private final Block block;
    public BlockState(Block block) { this.block = block; }
    public Block getBlock() { return block; }
    public boolean isAir() { return block.name.equals("AIR"); }
    @Override public String toString() { return block.name; }
}
""")
    write("net/minecraft/world/level/block/Block.java", """package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockState;

public class Block {
    public final String name;
    public final int color;
    private final BlockState state;
    public Block(String name, int color) { this.name = name; this.color = color; this.state = new BlockState(this); }
    public BlockState defaultBlockState() { return state; }
    @Override public String toString() { return name; }
}
""")
    lines = []
    for n in vanilla:
        lines.append(f'    public static final Block {n} = new Block("{n}", 0x{COLORS.get(n, 0xff00ff):06x});')
    dyed_fields = "\n".join(
        f'        private final Block {c} = new Block("DYED_TERRACOTTA_{c}", 0x{COLORS.get("DYED_TERRACOTTA_" + c, 0xff00ff):06x});\n'
        f'        public Block {c}() {{ return {c}; }}' for c in dyed)
    write("net/minecraft/world/level/block/Blocks.java", f"""package net.minecraft.world.level.block;

public final class Blocks {{
    private Blocks() {{}}
{chr(10).join(lines)}

    public static final class ColorSet {{
{dyed_fields}
    }}

    public static final ColorSet DYED_TERRACOTTA = new ColorSet();
}}
""")
    lines = [f'    public static final Block {n} = new Block("{n}", 0x{COLORS.get(n, 0x9a9a9a):06x});' for n in ours]
    write("com/galaxymc/registry/ModBlocks.java", f"""package com.galaxymc.registry;

import net.minecraft.world.level.block.Block;

public final class ModBlocks {{
    private ModBlocks() {{}}
{chr(10).join(lines)}
}}
""")
    write("net/minecraft/resources/ResourceKey.java", """package net.minecraft.resources;

public final class ResourceKey<T> {
    private final String id;
    private ResourceKey(String id) { this.id = id; }
    public static <T> ResourceKey<T> of(String id) { return new ResourceKey<>(id); }
    @Override public boolean equals(Object o) { return o instanceof ResourceKey<?> k && k.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
""")
    write("net/minecraft/world/level/Level.java", """package net.minecraft.world.level;

import net.minecraft.resources.ResourceKey;

public class Level {
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.of("minecraft:overworld");
}
""")
    dims = ["MOON", "MARS", "VENUS", "MERCURY", "SUN", "JUPITER", "EUROPA", "IO", "TITAN", "SATURN", "PLUTO", "FRONTIER"]
    body = "\n".join(f'    public static final ResourceKey<Level> {d} = ResourceKey.of("galaxy_mc:{d.lower()}");' for d in dims)
    write("com/galaxymc/world/ModDimensions.java", f"""package com.galaxymc.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {{
    private ModDimensions() {{}}
{body}
}}
""")
    print(f"stubs: {len(vanilla)} vanilla blocks, {len(dyed)} dyed, {len(ours)} Galaxy MC blocks")


if __name__ == "__main__":
    sys.exit(main())
