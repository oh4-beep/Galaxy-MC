package com.galaxymc.world;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.item.Relics;
import com.galaxymc.mineral.Minerals;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModItems;
import com.galaxymc.util.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Rare hand-built structures scattered across planet surfaces, each holding loot that scales with the
 * world's tier: the ruins of whoever was here before (relics, minerals), crashed survey probes (fuel,
 * metals) and crystal obelisks that serve as landmarks visible for kilometres.
 */
public final class Structures {
    private Structures() {}

    private static final int FLAGS = 2;

    public static void maybePlace(WorldGenLevel level, Hash.Rng rng, ChunkAccess chunk, PlanetProfile p, TerrainShaper shaper,
                                  PlanetChunkGenerator gen) {
        if (p.type == PlanetType.GAS_GIANT || p.type == PlanetType.SHATTERED || p.type == PlanetType.STELLAR) {
            return;
        }
        int x = chunk.getPos().getMinBlockX() + 8;
        int z = chunk.getPos().getMinBlockZ() + 8;
        double roll = rng.nextDouble();
        if (roll < 1.0 / 260.0) {
            ruins(level, rng, x, z, p);
        } else if (roll < 1.0 / 260.0 + 1.0 / 480.0) {
            probe(level, rng, x, z, p);
        } else if (roll < 1.0 / 260.0 + 1.0 / 480.0 + 1.0 / 700.0) {
            obelisk(level, rng, x, z, p);
        }
    }

    private static int ground(WorldGenLevel level, int x, int z) {
        return SurfaceDecorator.groundY(level, x, z);
    }

    private static boolean dry(WorldGenLevel level, int x, int y, int z) {
        BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
        return below.getFluidState().isEmpty() && !below.isAir();
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    private static void chest(WorldGenLevel level, BlockPos pos, Hash.Rng rng, PlanetProfile p, boolean relic, boolean fuel) {
        set(level, pos, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        int slot = 0;
        if (relic) {
            chest.setItem(slot++, Relics.random(rng.nextLong(), p.tier));
        }
        int minerals = rng.nextInt(1, 3);
        for (int i = 0; i < minerals; i++) {
            chest.setItem(rng.nextInt(27), Minerals.stack(Minerals.forPlanet(p, rng.nextInt(4)), rng.nextInt(2, 6)));
        }
        if (fuel) {
            chest.setItem(rng.nextInt(27), new ItemStack(ModItems.ROCKET_FUEL_CANISTER, rng.nextInt(1, 4)));
            chest.setItem(rng.nextInt(27), new ItemStack(ModItems.TITANIUM_INGOT, rng.nextInt(2, 7)));
        }
        if (rng.chance(0.5)) {
            chest.setItem(rng.nextInt(27), new ItemStack(ModItems.XENO_MEAT, rng.nextInt(2, 6)));
        }
        if (rng.chance(0.3)) {
            chest.setItem(rng.nextInt(27), new ItemStack(ModItems.THERMAL_FIBER, rng.nextInt(2, 8)));
        }
    }

    /** A ring of broken pillars around an altar, half-buried in the regolith. */
    private static void ruins(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState brick = ModBlocks.ALIEN_STONE_BRICKS.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        int radius = rng.nextInt(5, 8);
        int pillars = rng.nextInt(6, 11);
        for (int i = 0; i < pillars; i++) {
            double a = i * Math.PI * 2.0 / pillars;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int pz = cz + (int) Math.round(Math.sin(a) * radius);
            int py = ground(level, px, pz) - 1;
            int height = rng.chance(0.3) ? rng.nextInt(1, 3) : rng.nextInt(3, 8);
            for (int h = 0; h < height; h++) {
                set(level, new BlockPos(px, py + h, pz), brick);
            }
            if (height >= 5 && rng.chance(0.5)) {
                set(level, new BlockPos(px, py + height, pz), glow);
            }
        }
        // Flagstone floor and altar.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (rng.chance(0.8)) {
                    set(level, new BlockPos(cx + dx, cy - 1, cz + dz), brick);
                }
            }
        }
        set(level, new BlockPos(cx, cy, cz), brick);
        chest(level, new BlockPos(cx, cy + 1, cz), rng, p, true, false);
    }

    /** A survey probe that came down hard: a scorched crater with hull debris and a salvage crate. */
    private static void probe(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        int r = rng.nextInt(3, 5);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r) {
                    continue;
                }
                int depth = (int) Math.round((1.0 - d / r) * 2.5);
                for (int k = 0; k < depth; k++) {
                    set(level, new BlockPos(cx + dx, cy - 1 - k, cz + dz), Blocks.AIR.defaultBlockState());
                }
                if (rng.chance(0.35)) {
                    set(level, new BlockPos(cx + dx, cy - 1 - depth, cz + dz), Blocks.BLACKSTONE.defaultBlockState());
                }
            }
        }
        BlockState[] debris = {ModBlocks.HULL_PLATING.defaultBlockState(), ModBlocks.HULL_PLATING_DARK.defaultBlockState(),
                ModBlocks.HULL_STRIPE.defaultBlockState()};
        int pieces = rng.nextInt(8, 16);
        for (int i = 0; i < pieces; i++) {
            int dx = rng.nextInt(-r - 3, r + 3);
            int dz = rng.nextInt(-r - 3, r + 3);
            int y = ground(level, cx + dx, cz + dz);
            set(level, new BlockPos(cx + dx, y, cz + dz), rng.pick(debris));
        }
        int floor = cy - 3;
        set(level, new BlockPos(cx, floor, cz), ModBlocks.HULL_PLATING_DARK.defaultBlockState());
        set(level, new BlockPos(cx + 1, floor + 1, cz), ModBlocks.SHIP_LIGHT.defaultBlockState());
        set(level, new BlockPos(cx - 1, floor + 1, cz), ModBlocks.THRUSTER.defaultBlockState());
        chest(level, new BlockPos(cx, floor + 1, cz), rng, p, rng.chance(0.25), true);
    }

    /** A tall crystal obelisk: a landmark, with a relic sealed in its base on richer worlds. */
    private static void obelisk(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState crystal = ModBlocks.CRYSTAL_BLOCK.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        int height = rng.nextInt(14, 30);
        for (int h = -2; h < height; h++) {
            int w = h < height / 3 ? 1 : 0;
            for (int dx = -w; dx <= w + 1; dx++) {
                for (int dz = -w; dz <= w + 1; dz++) {
                    boolean edge = dx == -w || dz == -w || dx == w + 1 || dz == w + 1;
                    set(level, new BlockPos(cx + dx, cy + h, cz + dz), h % 5 == 0 && edge ? glow : crystal);
                }
            }
        }
        set(level, new BlockPos(cx, cy + height, cz), glow);
        set(level, new BlockPos(cx + 1, cy + height, cz + 1), glow);
        if (p.tier >= 2 && rng.chance(0.6)) {
            chest(level, new BlockPos(cx, cy - 1, cz), rng, p, true, false);
        }
    }
}
