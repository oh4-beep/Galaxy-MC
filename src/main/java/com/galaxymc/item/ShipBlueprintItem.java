package com.galaxymc.item;

import com.galaxymc.block.entity.NavigationConsoleBlockEntity;
import com.galaxymc.entity.Voices;
import com.galaxymc.ship.ShipBlueprint;
import com.galaxymc.ship.ShipType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Unfolds a ship. Use it on the ground: the whole hull assembles in front of you, facing the way you
 * are looking, provided the space is clear. The blueprint is consumed (except in creative).
 */
public class ShipBlueprintItem extends Item {
    private final ShipType type;

    public ShipBlueprintItem(ShipType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ShipType type() {
        return type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        ShipBlueprint blueprint = ShipBlueprint.get(type);
        Direction facing = context.getHorizontalDirection();
        Rotation rotation = ShipBlueprint.rotationFor(facing);
        // Anchor the ship a little in front of the player so they are not inside the hull.
        BlockPos clicked = context.getClickedPos();
        BlockPos anchor = clicked.above().relative(facing, blueprint.radius + 2);

        List<BlockPos> volume = blueprint.volume(anchor, rotation);
        for (BlockPos pos : volume) {
            BlockState existing = level.getBlockState(pos);
            if (!existing.canBeReplaced() || !existing.getFluidState().isEmpty() && !existing.isAir()) {
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("message.galaxy_mc.blueprint.blocked", pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.RED));
                }
                return InteractionResult.FAIL;
            }
            if (pos.getY() >= level.getMaxY() || pos.getY() <= level.getMinY()) {
                return InteractionResult.FAIL;
            }
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos pos : blueprint.interior) {
            level.setBlock(ShipBlueprint.place(anchor, pos, rotation), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), flags);
        }
        for (ShipBlueprint.Cell cell : blueprint.blocks) {
            level.setBlock(ShipBlueprint.place(anchor, cell.pos(), rotation), cell.state().rotate(rotation), flags);
        }
        BlockPos consolePos = blueprint.consoleWorld(anchor, rotation);
        if (level.getBlockEntity(consolePos) instanceof NavigationConsoleBlockEntity console) {
            console.initShip(type, anchor, rotation);
        }
        Voices.play(level, anchor, "block.anvil.use", SoundSource.BLOCKS, 1.0F, 0.7F);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.galaxy_mc.blueprint.built",
                    Component.translatable(type == ShipType.STARSHIP ? "container.galaxy_mc.starship" : "container.galaxy_mc.rocket"))
                    .withStyle(ChatFormatting.AQUA));
            ItemStack stack = context.getItemInHand();
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
