package com.galaxymc.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Ore whose identity is decided by the planet it sits on. {@link #SLOT} picks which of the planet's
 * four procedural minerals this is; the client tints the crystal overlay to that mineral's colour and
 * the drop carries the mineral's full procedural identity.
 */
public class ExoticOreBlock extends Block {
    public static final IntegerProperty SLOT = IntegerProperty.create("slot", 0, 3);

    public ExoticOreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SLOT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLOT);
    }
}
