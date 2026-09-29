package com.galaxymc.mineral;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.registry.ModComponents;
import com.galaxymc.registry.ModItems;
import com.galaxymc.util.Hash;
import com.galaxymc.util.NameGenerator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Factory for procedural minerals. Each planet owns four mineral seeds; the planet's tier sets how
 * potent they are. Because tier grows with distance from Sol, the only way to find better minerals is
 * to keep flying outward - and there is always further to go.
 */
public final class Minerals {
    private Minerals() {}

    private static final String[] FORMS = {"Crystal", "Shard", "Geode", "Ore", "Prism", "Nodule", "Spar", "Cluster"};

    public static MineralData create(long seed, int tier) {
        Hash.Rng rng = new Hash.Rng(seed ^ 0x4D494E52L);
        MineralTrait trait = MineralTrait.values()[rng.nextInt(MineralTrait.values().length)];
        double roll = rng.range(0.7, 1.3);
        int power = switch (trait) {
            case WARMTH, COOLING -> (int) Math.round((4 + tier * 6) * roll);
            case VOLATILE -> (int) Math.round(8 * tier * roll);
            case CONDUCTIVE -> (int) Math.round(2 * tier * roll);
            case SHARP, DENSE -> (int) Math.round(8 * tier * roll);
            case VITAL -> (int) Math.round(10 * tier * roll);
            case SWIFT -> (int) Math.max(1, Math.round(1.5 * tier * roll));
        };
        double hue = rng.nextDouble();
        int color = NameGenerator.hsv(hue, rng.range(0.45, 0.9), rng.range(0.7, 1.0));
        String name = NameGenerator.mineralName(seed) + " " + FORMS[rng.nextInt(FORMS.length)];
        return new MineralData(seed, tier, name, color, trait, Math.max(1, power));
    }

    /** The {@code slot}-th mineral of a planet. */
    public static MineralData forPlanet(PlanetProfile p, int slot) {
        return create(p.mineralSeeds[Math.floorMod(slot, 4)], p.tier);
    }

    public static ItemStack stack(MineralData data, int count) {
        ItemStack stack = new ItemStack(ModItems.EXOTIC_MINERAL, count);
        stack.set(ModComponents.MINERAL, data);
        stack.set(DataComponents.ITEM_NAME, Component.literal(data.name()));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(0xFF000000 | data.color())));
        stack.set(DataComponents.RARITY, rarity(data.tier()));
        return stack;
    }

    public static Rarity rarity(int tier) {
        if (tier >= 8) {
            return Rarity.EPIC;
        }
        if (tier >= 5) {
            return Rarity.RARE;
        }
        if (tier >= 3) {
            return Rarity.UNCOMMON;
        }
        return Rarity.COMMON;
    }

    public static Component tierText(int tier) {
        return Component.literal("Tier " + tier);
    }
}
