package com.galaxymc.block;

import com.galaxymc.menu.StellarForgeMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Star-hot anvil that fuses procedural minerals into gear. */
public class StellarForgeBlock extends Block {
    public static final MapCodec<StellarForgeBlock> CODEC = simpleCodec(StellarForgeBlock::new);
    private static final Component TITLE = Component.translatable("container.galaxy_mc.stellar_forge");

    public StellarForgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inv, player) -> new StellarForgeMenu(id, inv, ContainerLevelAccess.create(level, pos)), TITLE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
        }
        return InteractionResult.SUCCESS;
    }
}
