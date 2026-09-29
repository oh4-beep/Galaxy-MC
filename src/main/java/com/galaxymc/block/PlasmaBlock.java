package com.galaxymc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Crusted stellar plasma. Walking on it scorches anything without heat shielding. */
public class PlasmaBlock extends Block {
    public PlasmaBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
        if (!level.isClientSide() && entity instanceof LivingEntity && !entity.fireImmune() && level.getGameTime() % 10 == 0) {
            entity.hurt(level.damageSources().hotFloor(), 2.0F);
            entity.igniteForSeconds(3.0F);
        }
        super.stepOn(level, pos, onState, entity);
    }
}
