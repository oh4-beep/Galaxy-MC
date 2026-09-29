package com.galaxymc.climate;

import com.galaxymc.mineral.MineralData;
import com.galaxymc.mineral.MineralTrait;
import com.galaxymc.registry.ModComponents;
import com.galaxymc.registry.ModItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How much protection each material gives when stitched into armour, and the resulting safe range of a
 * whole suit. Numbers are degrees Celsius per item.
 *
 * <p>Wool is the everyday insulator (1 per block, so a stack per piece gets you to Pluto); ice cools
 * the same way, with packed and blue ice concentrating it. Creature parts and planet crystals are far
 * denser, and procedural minerals scale without limit.
 */
public final class ThermalMaterials {
    private ThermalMaterials() {}

    /** An unprotected player is comfortable in this range. */
    public static final int COMFORT_LOW = -5;
    public static final int COMFORT_HIGH = 40;

    public static int warmthOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ItemTags.WOOL)) {
            return 1;
        }
        Item item = stack.getItem();
        if (item == ModItems.THERMAL_FIBER) {
            return 6;
        }
        if (item == ModItems.PYROCITE_CRYSTAL) {
            return 12;
        }
        if (item == Items.BLAZE_POWDER) {
            return 3;
        }
        if (item == Items.MAGMA_CREAM) {
            return 4;
        }
        if (item == ModItems.XENO_HIDE || item == Items.LEATHER || item == Items.RABBIT_HIDE) {
            return 1;
        }
        MineralData m = stack.get(ModComponents.MINERAL);
        if (m != null && m.trait() == MineralTrait.WARMTH) {
            return m.power();
        }
        return 0;
    }

    public static int coolingOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == Items.ICE || item == Items.SNOW_BLOCK) {
            return 1;
        }
        if (item == Items.PACKED_ICE) {
            return 4;
        }
        if (item == Items.BLUE_ICE) {
            return 16;
        }
        if (item == Items.POWDER_SNOW_BUCKET) {
            return 3;
        }
        if (item == ModItems.CRYO_GLAND) {
            return 6;
        }
        if (item == ModItems.CRYONITE_CRYSTAL) {
            return 24;
        }
        MineralData m = stack.get(ModComponents.MINERAL);
        if (m != null && m.trait() == MineralTrait.COOLING) {
            return m.power();
        }
        return 0;
    }

    /** Natural insulation of an armour item before anything is added. */
    public static ThermalData inherent(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ModItems.SPACE_HELMET || item == ModItems.SPACE_CHESTPLATE || item == ModItems.SPACE_LEGGINGS
                || item == ModItems.SPACE_BOOTS) {
            return new ThermalData(12, 12);
        }
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) {
            return new ThermalData(4, 0);
        }
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS
                || item == Items.NETHERITE_BOOTS) {
            return new ThermalData(0, 6);
        }
        return ThermalData.NONE;
    }

    public static ThermalData total(ItemStack stack) {
        ThermalData added = stack.getOrDefault(ModComponents.THERMAL, ThermalData.NONE);
        ThermalData base = inherent(stack);
        return new ThermalData(added.warmth() + base.warmth(), added.cooling() + base.cooling());
    }

    public static boolean isArmor(ItemStack stack) {
        var eq = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    /** Safe temperature range of everything the entity is wearing: {low, high}. */
    public static int[] suitRange(LivingEntity entity) {
        int warmth = 0;
        int cooling = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ThermalData t = total(entity.getItemBySlot(slot));
            warmth += t.warmth();
            cooling += t.cooling();
        }
        return new int[]{COMFORT_LOW - warmth, COMFORT_HIGH + cooling};
    }
}
