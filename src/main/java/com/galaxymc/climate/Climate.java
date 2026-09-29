package com.galaxymc.climate;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ambient temperature model for planets.
 *
 * <p>Start from the planet's mean, swing it with the day/night cycle (airless worlds swing hardest),
 * damp the swing underground where rock shelters you, then add whatever is burning or freezing nearby -
 * standing beside a lava lake on Pluto genuinely helps.
 */
public final class Climate {
    private Climate() {}

    /** Day fraction in [0,1): 0 sunrise, 0.25 noon, 0.5 sunset, 0.75 midnight. */
    public static double dayFraction(ServerLevel level) {
        return Math.floorMod(level.getDefaultClockTime(), 24000L) / 24000.0;
    }

    public static double baseAt(ServerLevel level, PlanetProfile p, BlockPos pos) {
        double sun = Math.sin(dayFraction(level) * Math.PI * 2.0);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        boolean underground = pos.getY() < surface - 6;
        if (underground) {
            // Rock is a good insulator: the deep crust sits near the planet's mean, a touch warmer.
            double depth = Math.min(1.0, (surface - pos.getY()) / 48.0);
            return p.baseTemp * (1.0 - 0.25 * depth) + 12.0 * depth + p.tempSwing * sun * 0.2;
        }
        return p.baseTemp + p.tempSwing * sun;
    }

    /** Heat added (or removed) by blocks within three blocks of the position. */
    public static double localSources(ServerLevel level, BlockPos center) {
        double hot = 0;
        double cold = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) {
                        continue;
                    }
                    Block b = st.getBlock();
                    double falloff = 1.0 / (1.0 + (dx * dx + dy * dy + dz * dz) * 0.15);
                    if (b == Blocks.LAVA) {
                        hot += 40 * falloff;
                    } else if (b == ModBlocks.SOLAR_PLASMA) {
                        hot += 60 * falloff;
                    } else if (st.is(BlockTags.FIRE) || st.is(BlockTags.CAMPFIRES)) {
                        hot += 20 * falloff;
                    } else if (b == Blocks.MAGMA_BLOCK || b == Blocks.FURNACE && st.getLightEmission() > 0) {
                        hot += 8 * falloff;
                    } else if (b == Blocks.BLUE_ICE) {
                        cold += 20 * falloff;
                    } else if (b == Blocks.PACKED_ICE || b == ModBlocks.CRYONITE_ORE) {
                        cold += 10 * falloff;
                    } else if (b == Blocks.ICE) {
                        cold += 6 * falloff;
                    } else if (b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW) {
                        cold += 2 * falloff;
                    }
                }
            }
        }
        return Math.min(160, hot) - Math.min(60, cold);
    }
}
