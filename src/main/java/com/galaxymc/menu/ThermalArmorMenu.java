package com.galaxymc.menu;

import com.galaxymc.climate.ThermalData;
import com.galaxymc.climate.ThermalMaterials;
import com.galaxymc.entity.Voices;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModComponents;
import com.galaxymc.registry.ModMenus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thermal Armor Table: armour in the left slot, warming material (wool, thermal fibre, pyrocite...) in
 * the top slot, cooling material (ice, packed ice, cryonite...) in the bottom slot. The whole material
 * stacks are stitched in at once, so the preview shows exactly what you will get.
 */
public class ThermalArmorMenu extends ItemCombinerMenu {
    public static final int ARMOR = 0;
    public static final int WARM = 1;
    public static final int COOL = 2;
    public static final int RESULT = 3;
    public static final int BUTTON_STRIP = 0;

    public ThermalArmorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ThermalArmorMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.THERMAL_ARMOR, containerId, inventory, access, ItemCombinerMenuSlotDefinition.create()
                .withSlot(ARMOR, 27, 47, ThermalMaterials::isArmor)
                .withSlot(WARM, 76, 24, s -> ThermalMaterials.warmthOf(s) > 0)
                .withSlot(COOL, 76, 60, s -> ThermalMaterials.coolingOf(s) > 0)
                .withResultSlot(RESULT, 134, 47)
                .build());
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.THERMAL_ARMOR_TABLE);
    }

    public int addedWarmth() {
        ItemStack s = inputSlots.getItem(WARM);
        return ThermalMaterials.warmthOf(s) * s.getCount();
    }

    public int addedCooling() {
        ItemStack s = inputSlots.getItem(COOL);
        return ThermalMaterials.coolingOf(s) * s.getCount();
    }

    public ItemStack armor() {
        return inputSlots.getItem(ARMOR);
    }

    @Override
    public void createResult() {
        ItemStack armor = inputSlots.getItem(ARMOR);
        int warm = addedWarmth();
        int cool = addedCooling();
        if (armor.isEmpty() || (warm == 0 && cool == 0)) {
            resultSlots.setItem(0, ItemStack.EMPTY);
            return;
        }
        ItemStack out = armor.copyWithCount(1);
        ThermalData current = out.getOrDefault(ModComponents.THERMAL, ThermalData.NONE);
        out.set(ModComponents.THERMAL, current.add(warm, cool));
        resultSlots.setItem(0, out);
        broadcastChanges();
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        inputSlots.removeItem(ARMOR, 1);
        inputSlots.setItem(WARM, ItemStack.EMPTY);
        inputSlots.setItem(COOL, ItemStack.EMPTY);
        access.execute((level, pos) -> Voices.play(level, pos, "item.armor.equip_leather", SoundSource.BLOCKS, 1.0F, 1.0F));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_STRIP) {
            ItemStack armor = inputSlots.getItem(ARMOR);
            if (!armor.isEmpty() && armor.has(ModComponents.THERMAL)) {
                ItemStack stripped = armor.copy();
                stripped.remove(ModComponents.THERMAL);
                inputSlots.setItem(ARMOR, stripped);
                access.execute((level, pos) -> Voices.play(level, pos, "entity.sheep.shear", SoundSource.BLOCKS, 1.0F, 1.0F));
                return true;
            }
        }
        return false;
    }
}
