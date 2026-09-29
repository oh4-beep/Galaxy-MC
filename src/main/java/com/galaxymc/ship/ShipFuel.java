package com.galaxymc.ship;

import com.galaxymc.mineral.MineralData;
import com.galaxymc.mineral.MineralTrait;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModComponents;
import com.galaxymc.registry.ModItems;
import com.galaxymc.registry.Reg;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Starship fuel accounting. Everything is measured in "coal": one piece of coal is one unit.
 *
 * <p>Any furnace fuel works (at its furnace value), dedicated fuels are denser, and the Fuel Efficiency
 * enchantment multiplies whatever it is on: I x4, II x8, III x16, IV x32, V x64. Enchant a whole stack
 * of 64 coal with Fuel Efficiency V and it burns as 4096 coal.
 */
public final class ShipFuel {
    private ShipFuel() {}

    public static final ResourceKey<Enchantment> FUEL_EFFICIENCY = ResourceKey.create(Registries.ENCHANTMENT, Reg.id("fuel_efficiency"));
    public static final TagKey<Item> FUEL_ENCHANTABLE = TagKey.create(Registries.ITEM, Reg.id("fuel_enchantable"));

    /** Coal-equivalent of a single item, before enchantment. */
    public static double baseValue(ItemStack stack, Level level) {
        Item item = stack.getItem();
        if (item == Items.COAL || item == Items.CHARCOAL) {
            return 1.0;
        }
        if (item == Items.COAL_BLOCK) {
            return 10.0;
        }
        if (item == Items.LAVA_BUCKET) {
            return 12.0;
        }
        if (item == Items.BLAZE_ROD) {
            return 1.5;
        }
        if (item == ModItems.ROCKET_FUEL_CANISTER) {
            return 48.0;
        }
        if (item == ModBlocks.FUEL_BLOCK.asItem()) {
            return 432.0;
        }
        if (item == ModItems.METHANE_CRYSTAL) {
            return 6.0;
        }
        if (item == ModItems.PLUTONITE) {
            return 256.0;
        }
        MineralData m = stack.get(ModComponents.MINERAL);
        if (m != null) {
            return m.trait() == MineralTrait.VOLATILE ? m.power() : 0.0;
        }
        int burn = level.fuelValues().burnDuration(stack);
        return burn > 0 ? burn / 1600.0 : 0.0;
    }

    public static int efficiencyLevel(ItemStack stack) {
        for (Object2IntMap.Entry<Holder<Enchantment>> e : stack.getEnchantments().entrySet()) {
            if (e.getKey().is(FUEL_EFFICIENCY)) {
                return e.getIntValue();
            }
        }
        return 0;
    }

    public static double multiplier(int level) {
        return level <= 0 ? 1.0 : Math.pow(2.0, Math.min(5, level) + 1);
    }

    /** Coal-equivalent of the whole stack. */
    public static double value(ItemStack stack, Level level) {
        if (stack.isEmpty()) {
            return 0;
        }
        return baseValue(stack, level) * multiplier(efficiencyLevel(stack)) * stack.getCount();
    }

    public static boolean isFuel(ItemStack stack, Level level) {
        return !stack.isEmpty() && baseValue(stack, level) > 0;
    }

    public static String format(double coal) {
        if (coal >= 1_000_000) {
            return String.format("%.2fM", coal / 1_000_000.0);
        }
        if (coal >= 10_000) {
            return String.format("%.1fk", coal / 1000.0);
        }
        return String.format("%,d", (long) Math.ceil(coal));
    }
}
