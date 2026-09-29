package com.galaxymc.registry;

import com.galaxymc.item.ExoticMineralItem;
import com.galaxymc.item.RelicItem;
import com.galaxymc.item.ShipBlueprintItem;
import com.galaxymc.item.ThermometerItem;
import com.galaxymc.ship.ShipType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;

/** Every standalone item in Galaxy MC (block items are registered alongside their blocks). */
public final class ModItems {
    private ModItems() {}

    // ------------------------------------------------------------------ metals & crystals from Sol
    public static final Item RAW_TITANIUM = Reg.item("raw_titanium");
    public static final Item TITANIUM_INGOT = Reg.item("titanium_ingot");
    public static final Item RAW_COBALT = Reg.item("raw_cobalt");
    public static final Item COBALT_INGOT = Reg.item("cobalt_ingot");
    public static final Item RAW_IRIDIUM = Reg.item("raw_iridium");
    public static final Item IRIDIUM_INGOT = Reg.item("iridium_ingot", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item SULFUR = Reg.item("sulfur");
    public static final Item HELIONITE_SHARD = Reg.item("helionite_shard", new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    public static final Item STORM_CRYSTAL = Reg.item("storm_crystal", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item CRYONITE_CRYSTAL = Reg.item("cryonite_crystal", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item PYROCITE_CRYSTAL = Reg.item("pyrocite_crystal", new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
    public static final Item METHANE_CRYSTAL = Reg.item("methane_crystal");
    public static final Item PLUTONITE = Reg.item("plutonite", new Item.Properties().rarity(Rarity.RARE));
    public static final Item MOON_CHEESE = Reg.item("moon_cheese", new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F).build(),
                    Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
                            new MobEffectInstance(MobEffects.JUMP_BOOST, 600, 2))).build()));

    // ------------------------------------------------------------------ creature drops
    public static final Item XENO_HIDE = Reg.item("xeno_hide");
    public static final Item BIO_GEL = Reg.item("bio_gel");
    public static final Item CHITIN_PLATE = Reg.item("chitin_plate");
    public static final Item VENOM_SAC = Reg.item("venom_sac");
    public static final Item GLOW_SPORE = Reg.item("glow_spore");
    public static final Item THERMAL_FIBER = Reg.item("thermal_fiber");
    public static final Item CRYO_GLAND = Reg.item("cryo_gland");
    public static final Item TITAN_CORE = Reg.item("titan_core", new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    public static final Item WORLDEATER_FANG = Reg.item("worldeater_fang", new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    public static final Item LEVIATHAN_SCALE = Reg.item("leviathan_scale", new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    public static final Item XENO_MEAT = Reg.item("xeno_meat", new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));
    public static final Item COOKED_XENO_MEAT = Reg.item("cooked_xeno_meat", new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()));

    // ------------------------------------------------------------------ procedural
    public static final Item EXOTIC_MINERAL = Reg.item("exotic_mineral", ExoticMineralItem::new, new Item.Properties());
    public static final Item RELIC = Reg.item("relic", RelicItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // ------------------------------------------------------------------ fuel & ships
    public static final Item ROCKET_FUEL_CANISTER = Reg.item("rocket_fuel_canister", new Item.Properties().stacksTo(16).enchantable(12));
    public static final Item ROCKET_BLUEPRINT = Reg.item("rocket_blueprint", p -> new ShipBlueprintItem(ShipType.ROCKET, p),
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final Item STARSHIP_BLUEPRINT = Reg.item("starship_blueprint", p -> new ShipBlueprintItem(ShipType.STARSHIP, p),
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final Item THERMOMETER = Reg.item("thermometer", ThermometerItem::new, new Item.Properties().stacksTo(1));

    // ------------------------------------------------------------------ space suit
    public static final TagKey<Item> REPAIRS_SPACE_SUIT = TagKey.create(Registries.ITEM, Reg.id("repairs_space_suit"));
    public static final ResourceKey<EquipmentAsset> SPACE_SUIT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("space_suit"));
    public static final ArmorMaterial SPACE_SUIT = new ArmorMaterial(24,
            Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2, ArmorType.BODY, 5),
            12, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F, REPAIRS_SPACE_SUIT, SPACE_SUIT_ASSET);
    public static final Item SPACE_HELMET = Reg.item("space_helmet", new Item.Properties().humanoidArmor(SPACE_SUIT, ArmorType.HELMET));
    public static final Item SPACE_CHESTPLATE = Reg.item("space_chestplate", new Item.Properties().humanoidArmor(SPACE_SUIT, ArmorType.CHESTPLATE));
    public static final Item SPACE_LEGGINGS = Reg.item("space_leggings", new Item.Properties().humanoidArmor(SPACE_SUIT, ArmorType.LEGGINGS));
    public static final Item SPACE_BOOTS = Reg.item("space_boots", new Item.Properties().humanoidArmor(SPACE_SUIT, ArmorType.BOOTS));

    public static void init() {
        // Coal and the other fuels can be enchanted with Fuel Efficiency at an ordinary enchanting
        // table - including a whole stack at once, which is the point.
        DefaultItemComponentEvents.MODIFY.register(context -> context.modify(
                List.of(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.BLAZE_ROD, METHANE_CRYSTAL, PLUTONITE,
                        ModBlocks.FUEL_BLOCK.asItem()),
                (builder, item) -> builder.set(DataComponents.ENCHANTABLE, new Enchantable(12))));
    }
}
