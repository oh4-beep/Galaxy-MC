package com.galaxymc.ship;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.FrontierMap;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.galaxy.SolarSystem;
import com.galaxymc.world.ModDimensions;
import com.galaxymc.world.PlanetChunkGenerator;
import com.galaxymc.world.TerrainShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds somewhere sensible to put a ship (or a player) on arrival: dry, solid, reasonably flat ground
 * near the planet's landing zone. Candidate columns are evaluated straight from the terrain maths so
 * the search never forces dozens of chunks to generate; only the winner is loaded.
 */
public final class Landing {
    private Landing() {}

    public record Target(ServerLevel level, int x, int z) {}

    /** Resolves a destination id to its level and the centre of its landing zone. */
    public static Target target(MinecraftServer server, String destinationId) {
        if (destinationId.startsWith("f:")) {
            FrontierMap.Cell c = FrontierMap.parse(destinationId);
            ServerLevel level = server.getLevel(ModDimensions.FRONTIER);
            if (c == null || level == null) {
                return null;
            }
            return new Target(level, FrontierMap.centerX(c.sx(), c.slot()), FrontierMap.centerZ(c.sz(), c.planet()));
        }
        SolarSystem.Body body = SolarSystem.body(destinationId);
        if (body == null) {
            return null;
        }
        ServerLevel level = server.getLevel(body.dimension());
        if (level == null) {
            return null;
        }
        if (body.dimension().equals(Level.OVERWORLD)) {
            BlockPos spawn = level.getRespawnData().pos();
            return new Target(level, spawn.getX(), spawn.getZ());
        }
        return new Target(level, 0, 0);
    }

    /**
     * Searches outward in a spiral for a column whose surface is solid and whose neighbourhood of
     * {@code radius} blocks varies by at most a few blocks in height. Returns the landing surface y
     * (first air block) packed in the BlockPos.
     */
    public static BlockPos findSpot(ServerLevel level, int cx, int cz, int radius) {
        if (!(level.getChunkSource().getGenerator() instanceof PlanetChunkGenerator gen)) {
            level.getChunk(cx >> 4, cz >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            return new BlockPos(cx, y, cz);
        }
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        int step = Math.max(8, radius);
        for (int ring = 0; ring <= 14 && bestScore > 2; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x = cx + i * step;
                    int z = cz + j * step;
                    int score = columnScore(level, gen, x, z, radius);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(x, surface(level, gen, x, z), z);
                    }
                }
            }
        }
        if (best == null) {
            best = new BlockPos(cx, 120, cz);
        }
        level.getChunk(best.getX() >> 4, best.getZ() >> 4);
        int realY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, best.getX(), best.getZ());
        if (realY > level.getMinY() + 2) {
            best = new BlockPos(best.getX(), realY, best.getZ());
        }
        return best;
    }

    private static int surface(ServerLevel level, PlanetChunkGenerator gen, int x, int z) {
        return gen.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, level.getChunkSource().randomState());
    }

    /** Lower is better; Integer.MAX_VALUE means unusable (fluid, void or plasma). */
    private static int columnScore(ServerLevel level, PlanetChunkGenerator gen, int x, int z, int radius) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int[][] probes = {{0, 0}, {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}, {radius, radius}, {-radius, -radius}};
        for (int[] o : probes) {
            int px = x + o[0];
            int pz = z + o[1];
            PlanetProfile here = gen.planetAt(px, pz);
            if (here != null && TerrainShaper.of(here).islandMask(px, pz) > 0.0) {
                // Never set a ship down on (or under) a sky island.
                return Integer.MAX_VALUE;
            }
            NoiseColumn column = gen.getBaseColumn(px, pz, level, level.getChunkSource().randomState());
            int y = surface(level, gen, px, pz);
            if (y <= level.getMinY() + 12) {
                return Integer.MAX_VALUE;
            }
            BlockState top = column.getBlock(y - 1);
            if (!top.getFluidState().isEmpty() || top.getBlock() == com.galaxymc.registry.ModBlocks.SOLAR_PLASMA) {
                return Integer.MAX_VALUE;
            }
            BlockState above = column.getBlock(y);
            if (!above.getFluidState().isEmpty()) {
                return Integer.MAX_VALUE;
            }
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        return max - min;
    }

    /** Current planet profile for a server level and position (null on Earth). */
    public static PlanetProfile profile(ServerLevel level, double x, double z) {
        return Planets.at(level.dimension(), GalaxyMC.galaxySeed(), x, z);
    }
}
