package com.galaxymc.block.entity;

import com.galaxymc.climate.LifeSupportRegistry;
import com.galaxymc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LifeSupportBlockEntity extends BlockEntity {
    public LifeSupportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIFE_SUPPORT, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LifeSupportBlockEntity be) {
        if (level.getGameTime() % 20 == Math.floorMod(pos.asLong(), 20)) {
            LifeSupportRegistry.announce(level, pos);
        }
    }
}
