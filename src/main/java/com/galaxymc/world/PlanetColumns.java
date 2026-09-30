package com.galaxymc.world;

import com.galaxymc.galaxy.FrontierMap;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.util.Hash;
import com.galaxymc.util.Noise;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns terrain maths into block columns. Pure and stateless: the chunk generator, height queries,
 * landing search and the offline preview tool all build columns through here, so they always agree.
 *
 * <p>Surface choice is climate and slope aware:
 * <ul>
 *   <li>slopes steeper than the planet's {@code cliffSlope} lose their soil and show bare rock (or the
 *   planet's rock strata, which is what gives canyon walls and mountain faces their bands);</li>
 *   <li>above the snow line flat ground is buried in snow while steep faces stay dark rock;</li>
 *   <li>lakes get beaches and beds, frozen crusts or patchy obsidian skins over lava;</li>
 *   <li>on scorching worlds a network of glowing magma cracks runs through the ground;</li>
 *   <li>volcano craters fill with lava and their flanks are streaked with basalt and glowing flows;</li>
 *   <li>Terran worlds pick grass, sand, podzol, snow or red sand per column from their local biome.</li>
 * </ul>
 */
public final class PlanetColumns {
    private PlanetColumns() {}

    public static final int MIN_Y = -64;
    public static final int HEIGHT = 576;
    public static final int MAX_TERRAIN = MIN_Y + HEIGHT - 20;
    /** Width of the crumbling edge band around a frontier disc. */
    public static final int RIM = 96;

    private static BlockState bedrock() {
        return Blocks.BEDROCK.defaultBlockState();
    }

    // ------------------------------------------------------------------ geometry

    /** Frontier edge falloff in [0, 1]: 0 inside the planet, 1 at and beyond the rim. */
    public static double edge(PlanetProfile p, TerrainShaper shaper, double x, double z) {
        if (p.radius <= 0) {
            return 0.0;
        }
        double d = FrontierMap.distanceFromCenter(x, z);
        double wobble = 1.0 + 0.07 * shaper.warpNoise().sample(x / 260.0, 7.7, z / 260.0);
        double r = p.radius * wobble;
        if (d >= r) {
            return 1.0;
        }
        return Noise.smoothstep(r - RIM, r, d);
    }

    /**
     * Continuous surface height of a column including lakes and edge effects, or NaN over the void.
     * Fills {@code col} with lake information when given.
     */
    public static double height(PlanetProfile p, TerrainShaper shaper, double x, double z, TerrainShaper.Column col) {
        double e = edge(p, shaper, x, z);
        if (e >= 1.0) {
            if (col != null) {
                col.lakeLevel = TerrainShaper.NO_LAKE;
                col.lakeShore = false;
            }
            return Double.NaN;
        }
        double h = shaper.sample(x, z, col) - e * 10.0;
        if (e > 0.0) {
            if (col != null) {
                col.lakeLevel = TerrainShaper.NO_LAKE;
                col.lakeShore = false;
            }
            if (hasSea(p) && e < 0.45) {
                // Coastal lip: oceans are held in by a rocky ridge at the world's rim instead of pouring
                // off the edge of the disc into the void.
                h = Math.max(h, p.seaLevel + 1.0 + 5.0 * Math.sin(Math.PI * e / 0.45));
            }
        }
        return Noise.clamp(h, MIN_Y + 6, MAX_TERRAIN);
    }

    private static boolean hasSea(PlanetProfile p) {
        return p.palette.fluid() != null && !p.subsurfaceOcean && p.seaLevel > MIN_Y + 8;
    }

    public static int floorY(double h) {
        return Double.isNaN(h) ? MIN_Y - 1 : (int) Math.floor(h);
    }

    /** Integer surface height of a column (y of the top block); MIN_Y - 1 over the void. */
    public static int surfaceY(PlanetProfile p, TerrainShaper shaper, int x, int z) {
        return floorY(height(p, shaper, x, z, null));
    }

    /** Steepest rise per block across a column from its four neighbours' heights (NaN-safe). */
    public static double slope(double centre, double east, double west, double south, double north) {
        double dx = gradient(centre, east, west);
        double dz = gradient(centre, south, north);
        return Math.max(dx, dz);
    }

    private static double gradient(double c, double a, double b) {
        boolean ha = !Double.isNaN(a);
        boolean hb = !Double.isNaN(b);
        if (ha && hb) {
            return Math.abs(a - b) * 0.5;
        }
        if (ha) {
            return Math.abs(a - c);
        }
        if (hb) {
            return Math.abs(b - c);
        }
        return 0.0;
    }

    // ------------------------------------------------------------------ columns

    /** Builds a single column (sky islands included), computing its slope from the neighbouring columns. */
    public static void fill(PlanetProfile p, TerrainShaper shaper, int x, int z, BlockState[] out) {
        int surface = fillTerrain(p, shaper, x, z, out);
        if (surface >= MIN_Y && !shaper.isVolumetric()) {
            islands(p, shaper, x, z, surface, out);
        }
    }

    /** The ground alone (no sky islands), as vanilla's height queries and ship landings see it. */
    public static int fillTerrain(PlanetProfile p, TerrainShaper shaper, int x, int z, BlockState[] out) {
        if (shaper.isVolumetric()) {
            fillVolumetric(p, shaper, x, z, out);
            return MIN_Y - 1;
        }
        TerrainShaper.Column col = new TerrainShaper.Column();
        double h = height(p, shaper, x, z, col);
        if (Double.isNaN(h)) {
            return MIN_Y - 1;
        }
        double s = slope(h, height(p, shaper, x + 1, z, null), height(p, shaper, x - 1, z, null),
                height(p, shaper, x, z + 1, null), height(p, shaper, x, z - 1, null));
        fill(p, shaper, x, z, floorY(h), s, col, out);
        return floorY(h);
    }

    /**
     * Floating sky-islands over a column: flat-topped, soil-capped rock tapering to a point underneath.
     * Only fills air, and never where the ground rises close to the island layer.
     */
    public static void islands(PlanetProfile p, TerrainShaper shaper, int x, int z, int ground, BlockState[] out) {
        double m = shaper.islandMask(x, z);
        if (m <= 0.0 || edge(p, shaper, x, z) > 0.0) {
            return;
        }
        double base = shaper.islandBase(x, z);
        int top = (int) Math.floor(base + m * 7.0 + shaper.patch(x * 1.5, z * 1.5) * 2.0);
        double keel = 16.0 + 16.0 * (shaper.patch(x * 0.7 + 300.0, z * 0.7) * 0.5 + 0.5);
        int bottom = (int) Math.floor(base - Math.pow(m, 0.6) * keel);
        if (top <= bottom || bottom < ground + 14 || top > MAX_TERRAIN) {
            return;
        }
        PlanetProfile.Palette pal = p.palette;
        boolean earthlike = p.type == PlanetType.TERRAN;
        BlockState topState = earthlike ? Blocks.GRASS_BLOCK.defaultBlockState() : pal.top();
        BlockState under = earthlike ? Blocks.DIRT.defaultBlockState() : pal.under();
        for (int y = bottom; y <= top; y++) {
            int idx = y - MIN_Y;
            if (out[idx] != null) {
                continue;
            }
            int depth = top - y;
            out[idx] = depth == 0 ? topState : depth <= 3 ? under : pal.stone();
        }
    }

    /**
     * Builds one column of terrain (no caves, no ores) into {@code out}, indexed from MIN_Y. Entries left
     * null are air.
     */
    public static void fill(PlanetProfile p, TerrainShaper shaper, int x, int z, int surface, double slope,
                            TerrainShaper.Column col, BlockState[] out) {
        if (shaper.isVolumetric()) {
            fillVolumetric(p, shaper, x, z, out);
            return;
        }
        if (surface < MIN_Y) {
            return;
        }
        PlanetProfile.Palette pal = p.palette;
        double e = edge(p, shaper, x, z);
        int bottom = e <= 0 ? MIN_Y : (int) (MIN_Y + e * (surface - MIN_Y - 3));
        boolean edgeZone = bottom > MIN_Y;
        long colHash = Hash.of(p.seed, x, z);
        int bedrockTop = edgeZone ? MIN_Y - 1 : MIN_Y + 1 + (int) Long.remainderUnsigned(colHash, 3);
        int deepLine = (int) Math.floorMod(colHash >> 8, 7) - 3;

        if (p.subsurfaceOcean) {
            fillIceShell(p, shaper, x, z, surface, out, bedrockTop, deepLine);
            return;
        }

        int sea = p.seaLevel;
        boolean seaFluid = pal.fluid() != null && e <= 0.0;
        boolean underwater = seaFluid && surface < sea;
        boolean seaShore = seaFluid && surface <= sea + 1 && surface >= sea - 3;

        // ---- lakes
        PlanetProfile.Lakes lakes = p.lakes;
        boolean inLake = lakes != null && col != null && col.lakeLevel != TerrainShaper.NO_LAKE;
        int lakeLevel = inLake ? col.lakeLevel : TerrainShaper.NO_LAKE;
        BlockState lakeFluid = null;
        BlockState lakeCap = null;
        BlockState lakeShore = null;
        BlockState lakeBed = null;
        if (lakes != null && col != null && (inLake || col.lakeShore)) {
            boolean second = col.lakeKind == 1 && lakes.fluid2() != null;
            lakeFluid = second ? lakes.fluid2() : lakes.fluid();
            lakeCap = second ? lakes.cap2() : lakes.cap();
            lakeShore = second ? lakes.shore2() : lakes.shore();
            lakeBed = second ? lakes.shore2() : lakes.bed();
        }
        boolean lakeCovered = inLake && surface < lakeLevel;
        boolean lakeRim = !lakeCovered && col != null && col.lakeShore && lakeShore != null;

        // ---- surface climate
        boolean steep = slope > p.cliffSlope && !lakeCovered && !underwater;
        int snowLine = p.snowLine == Integer.MAX_VALUE ? Integer.MAX_VALUE
                : p.snowLine + (int) Math.round(shaper.patch(x * 2.3, z * 2.3) * 6.0);
        boolean snowy = pal.snow() != null && surface >= snowLine && !lakeCovered && !underwater
                && slope < p.cliffSlope + 0.9 && snowCovers(p, shaper, x, z, surface - snowLine);
        BlockState top = pal.top();
        BlockState under = pal.under();
        BlockState shore = pal.shore();
        int underDepth = steep ? 0 : pal.underDepth();
        if (pal.alt() != null && shaper.patch(x, z) > 0.22) {
            top = pal.alt();
        }
        TerranBiome biome = p.type == PlanetType.TERRAN ? shaper.terranBiome(x, z, surface) : null;
        if (biome != null) {
            top = terranTop(biome, shaper, x, z);
            under = terranUnder(biome);
            shore = terranShore(biome, shaper, x, z, surface, sea);
            if (biome == TerranBiome.DESERT || biome == TerranBiome.BADLANDS) {
                underDepth = steep ? 0 : 5;
            }
            if (biome.snowy && !steep && !underwater && pal.snow() != null
                    && (biome == TerranBiome.SNOWY_PEAKS || biome == TerranBiome.SNOWY_BEACH)) {
                top = pal.snow();
            }
        }
        if (p.baseTemp > 250 && p.type != PlanetType.GAS_GIANT && !underwater && !lakeCovered
                && shaper.cracks(x, z) < 0.028) {
            top = Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        // ---- volcanoes: scorched summits, glowing flows down the flanks, a lava-filled crater
        boolean crater = col != null && col.craterLava != TerrainShaper.NO_LAKE && surface < col.craterLava;
        if (col != null && col.volcano < 1.0 && !underwater && !lakeCovered) {
            double t = col.volcano;
            double flow = shaper.lavaFlow(x, z);
            if (crater) {
                top = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (t < 0.9 && flow < 0.04 * (1.0 - t)) {
                top = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (t < 0.45 || t < 0.7 && shaper.patch(x * 1.3, z * 1.3) > 0.1) {
                top = shaper.patch(x * 2.1, z * 2.1) > 0.0 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                under = Blocks.BASALT.defaultBlockState();
            }
        }
        int wobble = (int) (shaper.patch(x * 0.25, z * 0.25) * 3.0);
        int strataFloor = p.baseHeight - 24;

        for (int y = bottom; y <= surface; y++) {
            int depth = surface - y;
            BlockState st;
            if (y <= bedrockTop) {
                st = bedrock();
            } else if (depth == 0) {
                if (snowy && !steep) {
                    st = pal.snow();
                } else if (steep) {
                    st = rock(p, pal, y, deepLine, strataFloor, wobble);
                } else if (lakeCovered) {
                    st = lakeLevel - surface <= 2 || lakeBed == null ? nonNull(lakeShore, shore) : lakeBed;
                } else if (lakeRim) {
                    st = lakeShore;
                } else if (underwater || seaShore) {
                    st = shore;
                } else {
                    st = top;
                }
            } else if (depth <= underDepth) {
                if (snowy && depth == 1 && pal.snow() != null) {
                    st = pal.snow();
                } else if ((lakeCovered || lakeRim) && depth <= 2 && lakeShore != null) {
                    st = lakeCovered && lakeBed != null && lakeLevel - surface > 2 ? lakeBed : lakeShore;
                } else if ((underwater || seaShore) && depth <= 2) {
                    st = shore;
                } else {
                    st = under;
                }
            } else {
                st = rock(p, pal, y, deepLine, strataFloor, wobble);
            }
            out[y - MIN_Y] = st;
        }
        if (edgeZone) {
            // Rocky underside of a floating world-disc.
            out[bottom - MIN_Y] = pal.deep();
        }
        if (seaFluid && surface < sea) {
            for (int y = surface + 1; y <= sea; y++) {
                out[y - MIN_Y] = pal.fluid();
            }
            if (pal.fluidCap() != null && seaCapHere(p, shaper, x, z)) {
                out[sea - MIN_Y] = pal.fluidCap();
            } else if (biome == TerranBiome.FROZEN_OCEAN) {
                out[sea - MIN_Y] = Blocks.ICE.defaultBlockState();
            }
        }
        if (crater) {
            for (int y = surface + 1; y <= col.craterLava; y++) {
                out[y - MIN_Y] = Blocks.LAVA.defaultBlockState();
            }
        }
        if (lakeCovered && lakeFluid != null) {
            for (int y = surface + 1; y <= lakeLevel; y++) {
                out[y - MIN_Y] = lakeFluid;
            }
            if (lakeCap != null && lakeCapHere(lakeFluid, shaper, x, z)) {
                out[lakeLevel - MIN_Y] = lakeCap;
            }
        }
    }

    // ------------------------------------------------------------------ terran surfaces

    private static BlockState terranTop(TerranBiome b, TerrainShaper shaper, int x, int z) {
        double patch = shaper.patch(x * 1.7, z * 1.7);
        return switch (b) {
            case BEACH, SNOWY_BEACH, DESERT -> Blocks.SAND.defaultBlockState();
            case BADLANDS -> patch > 0.35 ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.RED_SAND.defaultBlockState();
            case RIVER, OCEAN, FROZEN_OCEAN -> Blocks.SAND.defaultBlockState();
            case TAIGA, OLD_GROWTH_TAIGA -> patch > 0.15 ? Blocks.PODZOL.defaultBlockState()
                    : patch < -0.45 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            case SAVANNA -> patch > 0.3 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            case JUNGLE -> patch > 0.4 ? Blocks.PODZOL.defaultBlockState() : patch < -0.5 ? Blocks.MOSS_BLOCK.defaultBlockState()
                    : Blocks.GRASS_BLOCK.defaultBlockState();
            case SWAMP -> patch > 0.3 ? Blocks.MUD.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            case STONY_PEAKS -> patch > 0.3 ? Blocks.CALCITE.defaultBlockState() : patch < -0.3 ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();
            case SNOWY_PEAKS -> Blocks.SNOW_BLOCK.defaultBlockState();
            default -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private static BlockState terranUnder(TerranBiome b) {
        return switch (b) {
            case BEACH, SNOWY_BEACH, RIVER, OCEAN, FROZEN_OCEAN -> Blocks.SAND.defaultBlockState();
            case DESERT -> Blocks.SANDSTONE.defaultBlockState();
            case BADLANDS -> Blocks.TERRACOTTA.defaultBlockState();
            case STONY_PEAKS, SNOWY_PEAKS -> Blocks.STONE.defaultBlockState();
            default -> Blocks.DIRT.defaultBlockState();
        };
    }

    /** Sea floors and shores: sand in the shallows, gravel and clay further out, gravel on cold coasts. */
    private static BlockState terranShore(TerranBiome b, TerrainShaper shaper, int x, int z, int surface, int sea) {
        double patch = shaper.patch(x * 2.3, z * 2.3);
        if (b == TerranBiome.FROZEN_OCEAN || b == TerranBiome.SNOWY_BEACH) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (b == TerranBiome.SWAMP) {
            return Blocks.MUD.defaultBlockState();
        }
        if (surface < sea - 8) {
            return patch > 0.25 ? Blocks.GRAVEL.defaultBlockState() : patch < -0.35 ? Blocks.CLAY.defaultBlockState()
                    : Blocks.SAND.defaultBlockState();
        }
        return b == TerranBiome.RIVER && patch > 0.3 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
    }

    private static BlockState nonNull(BlockState a, BlockState b) {
        return a != null ? a : b;
    }

    /**
     * Snow lies in drifts: patchy just above the snow line, unbroken higher up, and thicker the colder
     * the world. Worlds whose ground is snow anyway (ice worlds) are always covered.
     */
    private static boolean snowCovers(PlanetProfile p, TerrainShaper shaper, int x, int z, int aboveLine) {
        if (p.type == PlanetType.ICE || p.palette.top() == p.palette.snow()) {
            return true;
        }
        double cover = 0.3 + aboveLine / 45.0 + Noise.clamp((-p.baseTemp - 10.0) / 120.0, 0.0, 0.35);
        if (cover >= 1.0) {
            return true;
        }
        double drift = shaper.patch(x * 0.6 + 1234.0, z * 0.6 - 777.0) * 0.5 + 0.5;
        return drift < cover;
    }

    /** Bedrock-free rock at height y: strata bands in the upper crust, stone, then deep stone. */
    private static BlockState rock(PlanetProfile p, PlanetProfile.Palette pal, int y, int deepLine, int strataFloor, int wobble) {
        BlockState[] strata = p.strata;
        if (strata != null && strata.length > 0 && y >= strataFloor) {
            return strata[Math.floorMod((y + wobble) / 2, strata.length)];
        }
        return y < deepLine ? pal.deep() : pal.stone();
    }

    private static boolean seaCapHere(PlanetProfile p, TerrainShaper shaper, int x, int z) {
        if (p.type == PlanetType.STELLAR) {
            return shaper.patch(x, z) > 0.15;
        }
        return true;
    }

    private static boolean lakeCapHere(BlockState fluid, TerrainShaper shaper, int x, int z) {
        if (fluid == Blocks.LAVA.defaultBlockState()) {
            // Lava lakes wear a broken skin of cooled crust.
            return shaper.patch(x * 1.7, z * 1.7) > 0.28;
        }
        return true;
    }

    /** Europa: an ice crust floating on a dark ocean, with a rock seabed far below. */
    private static void fillIceShell(PlanetProfile p, TerrainShaper shaper, int x, int z, int surface, BlockState[] out,
                                     int bedrockTop, int deepLine) {
        PlanetProfile.Palette pal = p.palette;
        double n = shaper.detailNoise().fbm(x / 140.0, z / 140.0, 3, 2.0, 0.5);
        int crust = (int) (16 + 7 * n);
        int seabed = (int) (6 + 20 * shaper.detailNoise().fbm(x / 220.0 + 50, z / 220.0 - 50, 3, 2.0, 0.5));
        int iceBottom = surface - crust;
        for (int y = MIN_Y; y <= surface; y++) {
            int idx = y - MIN_Y;
            if (y <= bedrockTop) {
                out[idx] = bedrock();
            } else if (y <= seabed) {
                out[idx] = y < deepLine ? pal.deep() : Blocks.TUFF.defaultBlockState();
            } else if (y < iceBottom) {
                out[idx] = pal.fluid();
            } else if (y == surface) {
                out[idx] = pal.top();
            } else {
                out[idx] = pal.under();
            }
        }
    }

    /** Cloud-islands and asteroid fields: fully 3D density, no heightmap. */
    public static void fillVolumetric(PlanetProfile p, TerrainShaper shaper, int x, int z, BlockState[] out) {
        double e = edge(p, shaper, x, z);
        if (e >= 1.0) {
            return;
        }
        PlanetProfile.Palette pal = p.palette;
        int depth = -1;
        for (int y = MIN_Y + HEIGHT - 8; y >= MIN_Y; y--) {
            double d = shaper.density(x, y, z) - e * 1.5;
            int idx = y - MIN_Y;
            if (d > 0) {
                depth++;
                out[idx] = depth == 0 ? pal.top() : depth <= pal.underDepth() ? pal.under() : (d > 0.25 ? pal.deep() : pal.stone());
            } else {
                depth = -1;
            }
        }
        if (p.type == PlanetType.GAS_GIANT) {
            // The storm floor: a deck of dense cloud that catches anything falling off an island.
            for (int y = MIN_Y; y < MIN_Y + 8; y++) {
                out[y - MIN_Y] = pal.top();
            }
        }
    }
}
