package com.galaxymc.world;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.FrontierMap;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.galaxy.SolarSystem;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.util.Hash;
import com.galaxymc.util.Noise;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Galaxy MC's own terrain generator, shared by every planet dimension.
 *
 * <p>The {@code planet} field names a Sol body ("moon", "mars", ...) or "frontier", in which case the
 * planet is looked up per chunk from the galaxy map. Generation happens in a single pass per chunk
 * into a local buffer: base terrain from {@link TerrainShaper}, then cheese caves, then Perlin-worm
 * tunnels, then ore blobs, then sky islands, and finally a copy into the chunk sections with heightmap
 * updates. Surface
 * decoration (flora, crystals, ruins) runs later in {@link #applyBiomeDecoration}.
 */
public class PlanetChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.STRING.fieldOf("planet").forGetter(g -> g.planetKey)
    ).apply(i, i.stable(PlanetChunkGenerator::new)));

    public static final int MIN_Y = PlanetColumns.MIN_Y;
    public static final int HEIGHT = PlanetColumns.HEIGHT;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    private final String planetKey;

    public PlanetChunkGenerator(BiomeSource biomeSource, String planetKey) {
        super(biomeSource);
        this.planetKey = planetKey;
    }

    public boolean isFrontier() {
        return "frontier".equals(planetKey);
    }

    /** The planet generating at a block position, or null over the void between frontier worlds. */
    public PlanetProfile planetAt(int x, int z) {
        if (isFrontier()) {
            return FrontierMap.planetAt(GalaxyMC.galaxySeed(), x, z);
        }
        return SolarSystem.profile(planetKey);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        PlanetProfile profile = planetAt(x0 + 8, z0 + 8);
        if (profile != null) {
            SurfaceDecorator.decorate(level, chunk, profile, TerrainShaper.of(profile), this);
        }
    }

    // ------------------------------------------------------------------ column model

    /** Frontier edge falloff in [0, 1]: 0 inside the planet, 1 at and beyond the rim. */
    public double edge(PlanetProfile p, TerrainShaper shaper, double x, double z) {
        return PlanetColumns.edge(p, shaper, x, z);
    }

    /** Integer surface height of a 2D world column, including edge effects; MIN_Y - 1 for void. */
    public int surfaceY(PlanetProfile p, TerrainShaper shaper, int x, int z) {
        return PlanetColumns.surfaceY(p, shaper, x, z);
    }

    /** One column of terrain (no caves, ores or sky islands), indexed from MIN_Y; null entries are air. */
    private void fillColumn(PlanetProfile p, TerrainShaper shaper, int x, int z, BlockState[] out) {
        PlanetColumns.fillTerrain(p, shaper, x, z, out);
    }

    // ------------------------------------------------------------------ chunk fill

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager,
                                                        ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        PlanetProfile profile = planetAt(x0 + 8, z0 + 8);
        if (profile == null) {
            return CompletableFuture.completedFuture(chunk);
        }
        return CompletableFuture.supplyAsync(() -> {
            Set<LevelChunkSection> sections = new HashSet<>();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = chunk.getSection(i);
                section.acquire();
                sections.add(section);
            }
            try {
                fill(profile, chunk, x0, z0);
            } finally {
                for (LevelChunkSection section : sections) {
                    section.release();
                }
            }
            return chunk;
        }, Util.backgroundExecutor().forName("galaxy_mc_terrain"));
    }

    private void fill(PlanetProfile p, ChunkAccess chunk, int x0, int z0) {
        TerrainShaper shaper = TerrainShaper.of(p);
        BlockState[] buf = new BlockState[16 * 16 * HEIGHT];
        int[] surface = new int[256];
        boolean[] wet = new boolean[256];
        BlockState[] column = new BlockState[HEIGHT];
        if (shaper.isVolumetric()) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    java.util.Arrays.fill(column, null);
                    PlanetColumns.fillVolumetric(p, shaper, x0 + lx, z0 + lz, column);
                    surface[lx * 16 + lz] = copyColumn(column, buf, lx, lz);
                }
            }
        } else {
            // Heights on an 18x18 grid (the chunk plus a one-block border) give every column its slope
            // without evaluating the terrain five times per column.
            double[] grid = new double[18 * 18];
            TerrainShaper.Column[] cols = new TerrainShaper.Column[256];
            for (int gx = 0; gx < 18; gx++) {
                for (int gz = 0; gz < 18; gz++) {
                    int lx = gx - 1;
                    int lz = gz - 1;
                    TerrainShaper.Column col = null;
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                        col = new TerrainShaper.Column();
                        cols[lx * 16 + lz] = col;
                    }
                    grid[gx * 18 + gz] = PlanetColumns.height(p, shaper, x0 + lx, z0 + lz, col);
                }
            }
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int g = (lx + 1) * 18 + (lz + 1);
                    double h = grid[g];
                    java.util.Arrays.fill(column, null);
                    if (!Double.isNaN(h)) {
                        double slope = PlanetColumns.slope(h, grid[g + 18], grid[g - 18], grid[g + 1], grid[g - 1]);
                        TerrainShaper.Column col = cols[lx * 16 + lz];
                        PlanetColumns.fill(p, shaper, x0 + lx, z0 + lz, PlanetColumns.floorY(h), slope, col, column);
                        wet[lx * 16 + lz] = col.lakeLevel != TerrainShaper.NO_LAKE || col.lakeShore
                                || (p.palette.fluid() != null && h < p.seaLevel + 1);
                    }
                    surface[lx * 16 + lz] = copyColumn(column, buf, lx, lz);
                }
            }
        }

        if (!shaper.isVolumetric() && p.caveDensity > 0) {
            carveCheese(p, shaper, buf, surface, x0, z0);
        }
        if (!shaper.isVolumetric()) {
            carveWorms(p, shaper, buf, surface, wet, x0, z0);
        }
        placeOres(p, buf, x0, z0, chunk.getPos().x(), chunk.getPos().z());
        if (!shaper.isVolumetric() && p.islands > 0) {
            // Sky islands go in after the caves so no tunnel ever breaks out through one.
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    java.util.Arrays.fill(column, null);
                    PlanetColumns.islands(p, shaper, x0 + lx, z0 + lz, surface[lx * 16 + lz], column);
                    for (int i = 0; i < HEIGHT; i++) {
                        if (column[i] != null && buf[index(lx, i, lz)] == null) {
                            buf[index(lx, i, lz)] = column[i];
                        }
                    }
                }
            }
        }
        write(chunk, buf, x0, z0);
    }

    /** Copies a column into the chunk buffer and returns the y of its highest non-fluid block. */
    private static int copyColumn(BlockState[] column, BlockState[] buf, int lx, int lz) {
        int top = MIN_Y - 1;
        for (int i = 0; i < HEIGHT; i++) {
            BlockState st = column[i];
            if (st != null) {
                buf[index(lx, i, lz)] = st;
                if (st.getFluidState().isEmpty()) {
                    top = i + MIN_Y;
                }
            }
        }
        return top;
    }

    private static int index(int lx, int yi, int lz) {
        return (yi * 16 + lz) * 16 + lx;
    }

    private boolean carvable(PlanetProfile p, BlockState st) {
        if (st == null || st == BEDROCK || !st.getFluidState().isEmpty()) {
            return false;
        }
        PlanetProfile.Palette pal = p.palette;
        return st != pal.fluidCap();
    }

    private BlockState caveFill(PlanetProfile p, int y) {
        if (p.baseTemp > 150 && y < -20) {
            return Blocks.LAVA.defaultBlockState();
        }
        return null;
    }

    private void carveCheese(PlanetProfile p, TerrainShaper shaper, BlockState[] buf, int[] surface, int x0, int z0) {
        // Sample on a 4x4x4 lattice and trilinearly interpolate, as vanilla does with its noise cells.
        int nx = 5;
        int ny = HEIGHT / 4 + 1;
        double[] grid = new double[nx * nx * ny];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nx; j++) {
                for (int k = 0; k < ny; k++) {
                    grid[(k * nx + j) * nx + i] = shaper.caveNoise(x0 + i * 4, MIN_Y + k * 4, z0 + j * 4);
                }
            }
        }
        double threshold = shaper.caveThreshold();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int top = surface[lx * 16 + lz] - 7;
                int gi = lx >> 2;
                int gj = lz >> 2;
                double fx = (lx & 3) / 4.0;
                double fz = (lz & 3) / 4.0;
                for (int y = MIN_Y + 5; y < top; y++) {
                    int yi = y - MIN_Y;
                    int gk = yi >> 2;
                    double fy = (yi & 3) / 4.0;
                    double c000 = grid[(gk * nx + gj) * nx + gi];
                    double c100 = grid[(gk * nx + gj) * nx + gi + 1];
                    double c010 = grid[(gk * nx + gj + 1) * nx + gi];
                    double c110 = grid[(gk * nx + gj + 1) * nx + gi + 1];
                    double c001 = grid[((gk + 1) * nx + gj) * nx + gi];
                    double c101 = grid[((gk + 1) * nx + gj) * nx + gi + 1];
                    double c011 = grid[((gk + 1) * nx + gj + 1) * nx + gi];
                    double c111 = grid[((gk + 1) * nx + gj + 1) * nx + gi + 1];
                    double v = lerp3(fx, fz, fy, c000, c100, c010, c110, c001, c101, c011, c111);
                    // Caves thin out towards the surface so the ground rarely collapses.
                    double depthBias = Math.max(0, (y - (top - 24)) / 24.0) * 0.25;
                    if (v > threshold + depthBias) {
                        int idx = index(lx, yi, lz);
                        if (carvable(p, buf[idx])) {
                            buf[idx] = caveFill(p, y);
                        }
                    }
                }
            }
        }
    }

    private static double lerp3(double fx, double fz, double fy, double c000, double c100, double c010, double c110,
                                double c001, double c101, double c011, double c111) {
        double a = c000 + (c100 - c000) * fx;
        double b = c010 + (c110 - c010) * fx;
        double c = c001 + (c101 - c001) * fx;
        double d = c011 + (c111 - c011) * fx;
        double lo = a + (b - a) * fz;
        double hi = c + (d - c) * fz;
        return lo + (hi - lo) * fy;
    }

    private void carveWorms(PlanetProfile p, TerrainShaper shaper, BlockState[] buf, int[] surface, boolean[] wet, int x0, int z0) {
        List<PerlinWorms.Worm> worms = PerlinWorms.wormsNear(p, shaper, x0, z0);
        for (PerlinWorms.Worm worm : worms) {
            float[] path = worm.path();
            for (int s = 0; s < path.length; s += 4) {
                float cx = path[s];
                float cy = path[s + 1];
                float cz = path[s + 2];
                float r = path[s + 3];
                if (cx + r < x0 || cx - r > x0 + 15 || cz + r < z0 || cz - r > z0 + 15) {
                    continue;
                }
                int minX = Math.max(0, (int) Math.floor(cx - r) - x0);
                int maxX = Math.min(15, (int) Math.ceil(cx + r) - x0);
                int minZ = Math.max(0, (int) Math.floor(cz - r) - z0);
                int maxZ = Math.min(15, (int) Math.ceil(cz + r) - z0);
                int minY = Math.max(MIN_Y + 3, (int) Math.floor(cy - r));
                int maxY = Math.min(MIN_Y + HEIGHT - 2, (int) Math.ceil(cy + r));
                float r2 = r * r;
                for (int lx = minX; lx <= maxX; lx++) {
                    float dx = x0 + lx + 0.5f - cx;
                    for (int lz = minZ; lz <= maxZ; lz++) {
                        float dz = z0 + lz + 0.5f - cz;
                        int surf = surface[lx * 16 + lz];
                        boolean seaAbove = wet[lx * 16 + lz];
                        for (int y = minY; y <= maxY; y++) {
                            float dy = (y + 0.5f - cy) * 1.25f;
                            if (dx * dx + dy * dy + dz * dz > r2) {
                                continue;
                            }
                            if (seaAbove && y > surf - 4) {
                                continue;
                            }
                            int idx = index(lx, y - MIN_Y, lz);
                            if (carvable(p, buf[idx])) {
                                buf[idx] = caveFill(p, y);
                            }
                        }
                    }
                }
            }
        }
    }

    private void placeOres(PlanetProfile p, BlockState[] buf, int x0, int z0, int cx, int cz) {
        PlanetProfile.Palette pal = p.palette;
        Hash.Rng rng = new Hash.Rng(Hash.of(p.seed ^ 0x4F524553L, cx, cz));
        for (PlanetProfile.OreSpec ore : p.ores) {
            int attempts = (int) Math.floor(ore.attempts());
            if (rng.nextDouble() < ore.attempts() - attempts) {
                attempts++;
            }
            boolean exotic = ore.ore().getBlock() == ModBlocks.EXOTIC_ORE;
            for (int a = 0; a < attempts; a++) {
                int ox = rng.nextInt(16);
                int oz = rng.nextInt(16);
                int minY = Math.max(MIN_Y + 4, ore.minY());
                int maxY = Math.min(MIN_Y + HEIGHT - 4, ore.maxY());
                if (maxY <= minY) {
                    continue;
                }
                int oy = rng.nextInt(minY, maxY);
                int slot = rng.nextInt(4);
                for (int n = 0; n < ore.size(); n++) {
                    int bx = ox + rng.nextInt(3) - 1 + (n > 3 ? rng.nextInt(3) - 1 : 0);
                    int by = oy + rng.nextInt(3) - 1;
                    int bz = oz + rng.nextInt(3) - 1 + (n > 3 ? rng.nextInt(3) - 1 : 0);
                    if (bx < 0 || bx > 15 || bz < 0 || bz > 15 || by <= MIN_Y + 1 || by >= MIN_Y + HEIGHT) {
                        continue;
                    }
                    int idx = index(bx, by - MIN_Y, bz);
                    BlockState host = buf[idx];
                    if (host == null) {
                        continue;
                    }
                    boolean deepHost = host == pal.deep();
                    if (host != pal.stone() && !deepHost) {
                        continue;
                    }
                    BlockState place = deepHost && ore.deepOre() != null ? ore.deepOre() : ore.ore();
                    if (exotic) {
                        place = place.setValue(com.galaxymc.block.ExoticOreBlock.SLOT, slot);
                    }
                    buf[idx] = place;
                    ox = bx;
                    oz = bz;
                    oy = by;
                }
            }
        }
    }

    private void write(ChunkAccess chunk, BlockState[] buf, int x0, int z0) {
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int yi = 0; yi < HEIGHT; yi++) {
            int y = yi + MIN_Y;
            int sectionIndex = chunk.getSectionIndex(y);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(sectionIndex);
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState st = buf[index(lx, yi, lz)];
                    if (st == null || st.isAir()) {
                        continue;
                    }
                    section.setBlockState(lx, y & 15, lz, st, false);
                    oceanFloor.update(lx, y, lz, st);
                    worldSurface.update(lx, y, lz, st);
                    if (!st.getFluidState().isEmpty() && touchesAir(buf, lx, yi, lz)) {
                        chunk.markPosForPostProcessing(pos.set(x0 + lx, y, z0 + lz));
                    }
                }
            }
        }
    }

    private static boolean touchesAir(BlockState[] buf, int lx, int yi, int lz) {
        if (yi > 0 && buf[index(lx, yi - 1, lz)] == null) {
            return true;
        }
        if (lx > 0 && buf[index(lx - 1, yi, lz)] == null) {
            return true;
        }
        if (lx < 15 && buf[index(lx + 1, yi, lz)] == null) {
            return true;
        }
        if (lz > 0 && buf[index(lx, yi, lz - 1)] == null) {
            return true;
        }
        return lz < 15 && buf[index(lx, yi, lz + 1)] == null;
    }

    // ------------------------------------------------------------------ previews

    /**
     * Top block and its height for a column, computed straight from the terrain maths (no chunk
     * needed). Returns null over the void. Used by the terrain preview tool.
     */
    public Object[] previewColumn(int x, int z) {
        PlanetProfile p = planetAt(x, z);
        if (p == null) {
            return null;
        }
        BlockState[] column = new BlockState[HEIGHT];
        PlanetColumns.fill(p, TerrainShaper.of(p), x, z, column);
        for (int i = HEIGHT - 1; i >= 0; i--) {
            if (column[i] != null) {
                return new Object[]{column[i], i + MIN_Y};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ queries

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        PlanetProfile p = planetAt(x, z);
        if (p == null) {
            return heightAccessor.getMinY();
        }
        BlockState[] column = new BlockState[HEIGHT];
        fillColumn(p, TerrainShaper.of(p), x, z, column);
        for (int i = HEIGHT - 1; i >= 0; i--) {
            BlockState st = column[i];
            if (st != null && type.isOpaque().test(st)) {
                return i + MIN_Y + 1;
            }
        }
        return heightAccessor.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        PlanetProfile p = planetAt(x, z);
        BlockState[] column = new BlockState[HEIGHT];
        if (p != null) {
            fillColumn(p, TerrainShaper.of(p), x, z, column);
        }
        for (int i = 0; i < HEIGHT; i++) {
            if (column[i] == null) {
                column[i] = AIR;
            }
        }
        return new NoiseColumn(MIN_Y, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        PlanetProfile p = planetAt(feetPos.getX(), feetPos.getZ());
        if (p != null) {
            result.add("Galaxy MC planet: " + p.name + " [" + p.type.displayName + "] T" + p.tier + " " + Math.round(p.baseTemp) + "C");
        } else {
            result.add("Galaxy MC: deep space");
        }
    }
}
