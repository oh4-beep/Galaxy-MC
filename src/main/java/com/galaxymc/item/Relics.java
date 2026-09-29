package com.galaxymc.item;

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
 * Procedural alien relics. A relic's seed fixes its name, power, colour and strength; its tier (the
 * tier of the world it was found on) scales the strength, so relics from the far frontier outclass
 * anything found near Sol. There are as many relics as there are seeds.
 */
public final class Relics {
    private Relics() {}

    private static final String[] FORMS = {"Idol", "Lens", "Shard", "Sigil", "Orb", "Totem", "Coil", "Prism", "Key", "Crown", "Spindle"};

    public static RelicData create(long seed, int tier) {
        Hash.Rng rng = new Hash.Rng(seed ^ 0x52454C43L);
        RelicPower power = RelicPower.values()[rng.nextInt(RelicPower.values().length)];
        int level = Math.max(1, Math.min(10, 1 + tier / 2 + rng.nextInt(2)));
        String maker = NameGenerator.word(rng.nextLong(), rng.nextInt(2, 3));
        String name = maker + " " + FORMS[rng.nextInt(FORMS.length)] + " of " + power.title;
        int color = NameGenerator.hsv(rng.nextDouble(), rng.range(0.5, 0.9), rng.range(0.8, 1.0));
        return new RelicData(seed, name, power, level, color);
    }

    public static ItemStack stack(RelicData data) {
        ItemStack stack = new ItemStack(ModItems.RELIC);
        stack.set(ModComponents.RELIC, data);
        stack.set(DataComponents.ITEM_NAME, Component.literal(data.name()));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(data.color())));
        stack.set(DataComponents.RARITY, data.level() >= 7 ? Rarity.EPIC : data.level() >= 4 ? Rarity.RARE : Rarity.UNCOMMON);
        return stack;
    }

    public static ItemStack random(long seed, int tier) {
        return stack(create(seed, tier));
    }
}
