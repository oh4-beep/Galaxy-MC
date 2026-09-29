package com.galaxymc.registry;

import com.galaxymc.block.entity.LifeSupportBlockEntity;
import com.galaxymc.block.entity.NavigationConsoleBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final BlockEntityType<NavigationConsoleBlockEntity> NAVIGATION_CONSOLE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Reg.id("navigation_console"),
            new BlockEntityType<>(NavigationConsoleBlockEntity::new, Set.of(ModBlocks.NAVIGATION_CONSOLE)));
    public static final BlockEntityType<LifeSupportBlockEntity> LIFE_SUPPORT = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Reg.id("life_support"),
            new BlockEntityType<>(LifeSupportBlockEntity::new, Set.of(ModBlocks.LIFE_SUPPORT)));

    public static void init() {
    }
}
