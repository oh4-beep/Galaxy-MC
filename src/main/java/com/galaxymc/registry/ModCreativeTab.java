package com.galaxymc.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The Galaxy MC creative tab, listing everything in declaration order. */
public final class ModCreativeTab {
    private ModCreativeTab() {}

    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Reg.id("galaxy_mc"));

    public static void init() {
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.galaxy_mc"))
                .icon(() -> new ItemStack(ModItems.ROCKET_BLUEPRINT))
                .displayItems((params, output) -> {
                    for (Item item : Reg.TAB_ORDER) {
                        output.accept(item);
                    }
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
    }
}
