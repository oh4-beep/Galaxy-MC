package com.galaxymc.menu;

import com.galaxymc.ship.ShipFuel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Replacement for the enchanting table's input slot: ordinary items still go in one at a time, but
 * ship fuels take a whole stack, so one enchantment upgrades all 64 pieces of coal at once.
 */
public class FuelStackSlot extends Slot {
    public FuelStackSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.is(ShipFuel.FUEL_ENCHANTABLE) ? stack.getMaxStackSize() : 1;
    }
}
