package com.galaxymc.menu;

import com.galaxymc.climate.ThermalData;
import com.galaxymc.climate.ThermalMaterials;
import com.galaxymc.entity.Voices;
import com.galaxymc.mineral.Infusions;
import com.galaxymc.mineral.MineralData;
import com.galaxymc.mineral.MineralTrait;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModComponents;
import com.galaxymc.registry.ModMenus;
import com.galaxymc.registry.Reg;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stellar Forge: fuses a procedural mineral into a tool, weapon or armour piece. Each mineral's trait
 * becomes a permanent attribute bonus scaled by its power, so gear keeps improving as long as you keep
 * finding richer worlds. Up to three infusions per item; costs one XP level per mineral tier.
 */
public class StellarForgeMenu extends ItemCombinerMenu {
    public static final int GEAR = 0;
    public static final int MINERAL = 1;
    public static final int RESULT = 2;

    public StellarForgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public StellarForgeMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.STELLAR_FORGE, containerId, inventory, access, ItemCombinerMenuSlotDefinition.create()
                .withSlot(GEAR, 27, 47, StellarForgeMenu::infusable)
                .withSlot(MINERAL, 76, 47, s -> s.has(ModComponents.MINERAL))
                .withResultSlot(RESULT, 134, 47)
                .build());
    }

    public static boolean infusable(ItemStack stack) {
        return !stack.isEmpty() && (stack.isDamageableItem() || ThermalMaterials.isArmor(stack) || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.TOOL));
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.STELLAR_FORGE);
    }

    public int levelCost() {
        MineralData m = inputSlots.getItem(MINERAL).get(ModComponents.MINERAL);
        return m == null ? 0 : Math.max(1, Math.min(30, m.tier()));
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return hasItem && (player.hasInfiniteMaterials() || player.experienceLevel >= levelCost());
    }

    /** Reason the forge refuses, or null when the combination works. */
    public String problem() {
        ItemStack gear = inputSlots.getItem(GEAR);
        MineralData m = inputSlots.getItem(MINERAL).get(ModComponents.MINERAL);
        if (gear.isEmpty() || m == null) {
            return null;
        }
        if (gear.getOrDefault(ModComponents.INFUSIONS, Infusions.EMPTY).full()) {
            return "screen.galaxy_mc.forge.full";
        }
        if (m.trait() == MineralTrait.VOLATILE) {
            return "screen.galaxy_mc.forge.volatile";
        }
        boolean armor = ThermalMaterials.isArmor(gear);
        if ((m.trait() == MineralTrait.WARMTH || m.trait() == MineralTrait.COOLING || m.trait() == MineralTrait.DENSE) && !armor) {
            return "screen.galaxy_mc.forge.armor_only";
        }
        return null;
    }

    @Override
    public void createResult() {
        ItemStack gear = inputSlots.getItem(GEAR);
        ItemStack mineralStack = inputSlots.getItem(MINERAL);
        MineralData m = mineralStack.get(ModComponents.MINERAL);
        if (gear.isEmpty() || m == null || problem() != null) {
            resultSlots.setItem(0, ItemStack.EMPTY);
            return;
        }
        ItemStack out = gear.copyWithCount(1);
        Infusions infusions = out.getOrDefault(ModComponents.INFUSIONS, Infusions.EMPTY);
        int index = infusions.entries().size();
        boolean armor = ThermalMaterials.isArmor(out);
        EquipmentSlotGroup group = EquipmentSlotGroup.MAINHAND;
        Equippable eq = out.get(DataComponents.EQUIPPABLE);
        if (armor && eq != null) {
            group = EquipmentSlotGroup.bySlot(eq.slot());
        }
        switch (m.trait()) {
            case WARMTH -> out.set(ModComponents.THERMAL, out.getOrDefault(ModComponents.THERMAL, ThermalData.NONE).add(m.power() * 4, 0));
            case COOLING -> out.set(ModComponents.THERMAL, out.getOrDefault(ModComponents.THERMAL, ThermalData.NONE).add(0, m.power() * 4));
            case CONDUCTIVE -> addModifier(out, Attributes.MINING_EFFICIENCY, m.power(), AttributeModifier.Operation.ADD_VALUE, group, index);
            case SHARP -> addModifier(out, Attributes.ATTACK_DAMAGE, m.power() / 10.0, AttributeModifier.Operation.ADD_VALUE, group, index);
            case DENSE -> addModifier(out, Attributes.ARMOR, m.power() / 10.0, AttributeModifier.Operation.ADD_VALUE, group, index);
            case VITAL -> addModifier(out, Attributes.MAX_HEALTH, m.power() / 10.0, AttributeModifier.Operation.ADD_VALUE, group, index);
            case SWIFT -> addModifier(out, Attributes.MOVEMENT_SPEED, m.power() / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, group, index);
            default -> {
            }
        }
        out.set(ModComponents.INFUSIONS, infusions.with(new Infusions.Entry(m.name(), m.trait(), m.power(), m.color())));
        out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        resultSlots.setItem(0, out);
        broadcastChanges();
    }

    private static void addModifier(ItemStack stack, Holder<Attribute> attribute, double amount, AttributeModifier.Operation op,
                                    EquipmentSlotGroup group, int index) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        mods = mods.withModifierAdded(attribute, new AttributeModifier(Reg.id("infusion_" + index), amount, op), group);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-levelCost());
        }
        inputSlots.removeItem(GEAR, 1);
        inputSlots.removeItem(MINERAL, 1);
        access.execute((level, pos) -> Voices.play(level, pos, "block.smithing_table.use", SoundSource.BLOCKS, 1.0F, 0.8F));
    }
}
