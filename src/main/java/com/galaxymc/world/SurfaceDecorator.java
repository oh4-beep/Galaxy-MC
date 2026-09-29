package com.galaxymc.world;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.util.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Surface features: flora, trees, spires, boulders and the occasional ruin. Runs in the decoration
 * step with a 3x3-chunk writable region, so features may overhang the centre chunk by up to a chunk.
 * Everything is seeded from the planet seed and chunk position, so decoration is reproducible.
 */
public final class SurfaceDecorator {
    private SurfaceDecorator() {}

    private static final int FLAGS = 2;

    public static void decorate(WorldGenLevel level, ChunkAccess chunk, PlanetProfile p, TerrainShaper shaper, PlanetChunkGenerator gen) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        Hash.Rng rng = new Hash.Rng(Hash.of(p.seed ^ 0x44454352L, chunk.getPos().x(), chunk.getPos().z()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        switch (p.type) {
            case JUNGLE -> {
                trees(level, rng, x0, z0, pos, p, rng.nextInt(2, 5), true);
                flora(level, rng, x0, z0, pos, p, 22, ModBlocks.XENO_GRASS.defaultBlockState(), ModBlocks.GLOW_SHROOM.defaultBlockState());
            }
            case GRASSLAND -> {
                if (rng.chance(0.35)) {
                    trees(level, rng, x0, z0, pos, p, 1, false);
                }
                flora(level, rng, x0, z0, pos, p, 30, ModBlocks.XENO_GRASS.defaultBlockState(), ModBlocks.FROST_BLOOM.defaultBlockState());
            }
            case FUNGAL -> {
                if (rng.chance(0.55)) {
                    mushroom(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, p);
                }
                flora(level, rng, x0, z0, pos, p, 18, ModBlocks.GLOW_SHROOM.defaultBlockState(), ModBlocks.XENO_GRASS.defaultBlockState());
            }
            case TOXIC -> {
                if (rng.chance(0.2)) {
                    mushroom(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, p);
                }
                flora(level, rng, x0, z0, pos, p, 14, ModBlocks.SPINE_PLANT.defaultBlockState(), ModBlocks.GLOW_SHROOM.defaultBlockState());
            }
            case CRYSTAL -> {
                if (rng.chance(0.35)) {
                    spire(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, rng.nextInt(6, 22));
                }
                clusters(level, rng, x0, z0, pos, 8);
            }
            case ICE -> {
                if (rng.chance(0.18)) {
                    iceSpike(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos);
                }
                if (p.isFrontier()) {
                    flora(level, rng, x0, z0, pos, p, 3, ModBlocks.FROST_BLOOM.defaultBlockState(), ModBlocks.FROST_BLOOM.defaultBlockState());
                }
            }
            case TUNDRA -> {
                if (p.isFrontier()) {
                    if (rng.chance(0.2)) {
                        trees(level, rng, x0, z0, pos, p, 1, false);
                    }
                    flora(level, rng, x0, z0, pos, p, 10, ModBlocks.FROST_BLOOM.defaultBlockState(), ModBlocks.XENO_GRASS.defaultBlockState());
                } else {
                    boulders(level, rng, x0, z0, pos, p, 0.25);
                }
            }
            case DESERT, DUNE_SEA -> {
                flora(level, rng, x0, z0, pos, p, 3, ModBlocks.SPINE_PLANT.defaultBlockState(), ModBlocks.EMBER_BLOOM.defaultBlockState());
                if (rng.chance(0.02)) {
                    fossil(level, rng, x0 + 8, z0 + 8, pos);
                }
            }
            case LAVA, ASH -> {
                if (rng.chance(0.3)) {
                    pillar(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, Blocks.BASALT.defaultBlockState(), rng.nextInt(4, 14));
                }
                if (p.isFrontier()) {
                    flora(level, rng, x0, z0, pos, p, 4, ModBlocks.EMBER_BLOOM.defaultBlockState(), ModBlocks.EMBER_BLOOM.defaultBlockState());
                }
            }
            case CRATERED, BARREN_ROCK, CANYON -> boulders(level, rng, x0, z0, pos, p, p.type == PlanetType.CANYON ? 0.3 : 0.45);
            case STELLAR -> {
                if (rng.chance(0.25)) {
                    pillar(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, ModBlocks.SOLAR_PLASMA.defaultBlockState(), rng.nextInt(2, 6));
                }
            }
            case GAS_GIANT, SHATTERED -> clusters(level, rng, x0, z0, pos, 2);
            case OCEAN -> flora(level, rng, x0, z0, pos, p, 6, ModBlocks.XENO_GRASS.defaultBlockState(), ModBlocks.GLOW_SHROOM.defaultBlockState());
        }
        Structures.maybePlace(level, rng, chunk, p, shaper, gen);
    }

    // ------------------------------------------------------------------ helpers

    static int groundY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private static boolean solidTop(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP) && below.getFluidState().isEmpty()
                && !below.is(BlockTags.ICE) && !below.is(Blocks.MAGMA_BLOCK) && level.getBlockState(pos).isAir();
    }

    private static void flora(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p,
                              int count, BlockState common, BlockState rare) {
        for (int i = 0; i < count; i++) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (!solidTop(level, pos)) {
                continue;
            }
            BlockState below = level.getBlockState(pos.below());
            if (below.is(Blocks.SNOW_BLOCK) && common.getBlock() != ModBlocks.FROST_BLOOM) {
                continue;
            }
            level.setBlock(pos, rng.chance(0.15) ? rare : common, FLAGS);
        }
    }

    private static void trees(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p,
                              int count, boolean tall) {
        BlockState log = ModBlocks.XENO_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaves = ModBlocks.XENO_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int i = 0; i < count; i++) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (!solidTop(level, pos) || y < p.seaLevel - 1) {
                continue;
            }
            int height = tall ? rng.nextInt(9, 18) : rng.nextInt(5, 8);
            // A curving trunk that leans as it grows, topped with a flat umbrella crown.
            double lean = rng.nextDouble() * Math.PI * 2;
            double tx = x;
            double tz = z;
            for (int h = 0; h < height; h++) {
                tx += Math.cos(lean) * (h > height / 2 ? 0.35 : 0.1);
                tz += Math.sin(lean) * (h > height / 2 ? 0.35 : 0.1);
                setIfAir(level, pos.set((int) Math.round(tx), y + h, (int) Math.round(tz)), log);
            }
            int cx = (int) Math.round(tx);
            int cz = (int) Math.round(tz);
            int cy = y + height;
            int radius = tall ? rng.nextInt(3, 5) : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius + 0.3) {
                        continue;
                    }
                    setIfAir(level, pos.set(cx + dx, cy, cz + dz), leaves);
                    if (d < radius - 1) {
                        setIfAir(level, pos.set(cx + dx, cy + 1, cz + dz), leaves);
                    }
                    if (d > radius - 0.8 && rng.chance(0.4)) {
                        // Drooping tendrils at the crown's edge.
                        int len = rng.nextInt(1, 3);
                        for (int k = 1; k <= len; k++) {
                            setIfAir(level, pos.set(cx + dx, cy - k, cz + dz), leaves);
                        }
                    }
                }
            }
        }
    }

    private static void mushroom(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos, PlanetProfile p) {
        int y = groundY(level, x, z);
        pos.set(x, y, z);
        if (!solidTop(level, pos)) {
            return;
        }
        int height = rng.nextInt(6, 14);
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        BlockState cap = ModBlocks.XENO_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int h = 0; h < height; h++) {
            setIfAir(level, pos.set(x, y + h, z), stem);
        }
        int r = rng.nextInt(3, 6);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.4) {
                    continue;
                }
                int drop = (int) Math.max(0, (d - r * 0.55) * 1.2);
                setIfAir(level, pos.set(x + dx, y + height - drop, z + dz), cap);
                if (d > r - 1 && rng.chance(0.3)) {
                    setIfAir(level, pos.set(x + dx, y + height - drop - 1, z + dz), ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState());
                }
            }
        }
    }

    private static void spire(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos, int height) {
        int y = groundY(level, x, z);
        pos.set(x, y, z);
        if (!solidTop(level, pos)) {
            return;
        }
        BlockState crystal = ModBlocks.CRYSTAL_BLOCK.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        double tiltX = rng.range(-0.25, 0.25);
        double tiltZ = rng.range(-0.25, 0.25);
        for (int h = 0; h < height; h++) {
            double t = 1.0 - (double) h / height;
            int r = (int) Math.round(t * 2.2);
            int cx = x + (int) Math.round(tiltX * h);
            int cz = z + (int) Math.round(tiltZ * h);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r + 1) {
                        setIfAir(level, pos.set(cx + dx, y + h, cz + dz), h > height - 3 ? glow : crystal);
                    }
                }
            }
        }
    }

    private static void clusters(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, int count) {
        BlockState shard = ModBlocks.CRYSTAL_SHARD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        for (int i = 0; i < count; i++) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (solidTop(level, pos)) {
                level.setBlock(pos, shard, FLAGS);
            }
        }
    }

    private static void iceSpike(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = groundY(level, x, z);
        int height = rng.nextInt(8, 28);
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        for (int h = 0; h < height; h++) {
            double t = 1.0 - (double) h / height;
            int r = (int) Math.round(t * 2.6);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        setIfAir(level, pos.set(x + dx, y + h, z + dz), ice);
                    }
                }
            }
        }
    }

    private static void pillar(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos, BlockState state, int height) {
        int y = groundY(level, x, z);
        pos.set(x, y, z);
        if (!solidTop(level, pos)) {
            return;
        }
        for (int h = 0; h < height; h++) {
            setIfAir(level, pos.set(x, y + h, z), state);
            if (h < height / 3) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (rng.chance(0.5)) {
                        setIfAir(level, pos.set(x + d.getStepX(), y + h, z + d.getStepZ()), state);
                    }
                }
            }
        }
    }

    private static void boulders(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p, double chance) {
        if (!rng.chance(chance)) {
            return;
        }
        int x = x0 + rng.nextInt(16);
        int z = z0 + rng.nextInt(16);
        int y = groundY(level, x, z) - 1;
        double r = rng.range(1.2, 3.2);
        BlockState rock = p.palette.stone();
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dy * dy * 1.6 + dz * dz <= r * r) {
                        setIfAir(level, pos.set(x + dx, y + dy, z + dz), rock);
                    }
                }
            }
        }
    }

    private static void fossil(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = groundY(level, x, z);
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        int length = rng.nextInt(10, 20);
        for (int i = 0; i < length; i++) {
            setIfAir(level, pos.set(x + i - length / 2, y, z), bone);
            if (i % 3 == 0) {
                for (int k = 1; k <= 4; k++) {
                    int arc = k < 3 ? k : 2;
                    setIfAir(level, pos.set(x + i - length / 2, y + arc, z + k), bone);
                    setIfAir(level, pos.set(x + i - length / 2, y + arc, z - k), bone);
                }
            }
        }
    }

    static void setIfAir(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, FLAGS);
        }
    }
}
