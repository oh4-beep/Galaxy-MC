package com.galaxymc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Alien undergrowth. Unlike vanilla plants these root on any sturdy block, because the ground on other
 * worlds is rarely dirt; a few species sting whatever brushes through them.
 */
public class AlienPlantBlock extends VegetationBlock {
    public static final MapCodec<AlienPlantBlock> CODEC = simpleCodec(p -> new AlienPlantBlock(p, 12, 0.0F));

    private final VoxelShape shape;
    private final float stingDamage;

    public AlienPlantBlock(BlockBehaviour.Properties properties, int height, float stingDamage) {
        super(properties);
        this.shape = net.minecraft.world.level.block.Block.column(12.0, 0.0, height);
        this.stingDamage = stingDamage;
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (stingDamage > 0 && level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living
                && !(entity instanceof com.galaxymc.entity.AlienMob)
                && (entity.xOld != entity.getX() || entity.zOld != entity.getZ())
                && level.getGameTime() % 10 == 0) {
            living.hurtServer(serverLevel, level.damageSources().sweetBerryBush(), stingDamage);
        }
    }
}
