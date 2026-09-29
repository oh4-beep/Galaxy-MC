package com.galaxymc.menu;

import com.galaxymc.registry.ModMenus;
import com.galaxymc.ship.ShipFuel;
import com.galaxymc.ship.ShipType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container side of the navigation console: the fuel intake slot plus the player's inventory, and the
 * tank level synced as data slots. Route search happens entirely on the client; launching goes through
 * {@link com.galaxymc.network.LaunchPayload}.
 */
public class NavigationMenu extends AbstractContainerMenu {
    public record OpenData(BlockPos pos, int shipType) {
        public static final StreamCodec<ByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenData::pos,
                ByteBufCodecs.VAR_INT, OpenData::shipType,
                OpenData::new);
    }

    public static final int FUEL_X = 296;
    public static final int FUEL_Y = 170;
    public static final int INV_X = 8;
    public static final int INV_Y = 150;

    private final Container fuelSlot;
    private final ContainerData data;
    private final BlockPos pos;
    private final ShipType type;

    public NavigationMenu(int containerId, Inventory inventory, OpenData open) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(3), open.pos(),
                open.shipType() < 0 ? null : ShipType.values()[open.shipType()]);
    }

    public NavigationMenu(int containerId, Inventory inventory, Container fuelSlot, ContainerData data, BlockPos pos) {
        this(containerId, inventory, fuelSlot, data, pos, fuelSlot instanceof com.galaxymc.block.entity.NavigationConsoleBlockEntity be ? be.shipType() : null);
    }

    private NavigationMenu(int containerId, Inventory inventory, Container fuelSlot, ContainerData data, BlockPos pos, ShipType type) {
        super(ModMenus.NAVIGATION, containerId);
        this.fuelSlot = fuelSlot;
        this.data = data;
        this.pos = pos;
        this.type = type;
        checkContainerSize(fuelSlot, 1);
        Player player = inventory.player;
        addSlot(new Slot(fuelSlot, 0, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ShipFuel.isFuel(stack, player.level());
            }
        });
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public ShipType shipType() {
        return type;
    }

    public double fuel() {
        return (data.get(0) & 0xFFFF) | ((long) (data.get(1) & 0xFFFF) << 16);
    }

    public boolean launching() {
        return data.get(2) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return fuelSlot.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
