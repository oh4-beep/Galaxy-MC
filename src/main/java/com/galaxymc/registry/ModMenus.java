package com.galaxymc.registry;

import com.galaxymc.menu.NavigationMenu;
import com.galaxymc.menu.StellarForgeMenu;
import com.galaxymc.menu.ThermalArmorMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private ModMenus() {}

    public static final MenuType<ThermalArmorMenu> THERMAL_ARMOR = Registry.register(BuiltInRegistries.MENU, Reg.id("thermal_armor"),
            new MenuType<>(ThermalArmorMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<StellarForgeMenu> STELLAR_FORGE = Registry.register(BuiltInRegistries.MENU, Reg.id("stellar_forge"),
            new MenuType<>(StellarForgeMenu::new, FeatureFlags.VANILLA_SET));
    public static final ExtendedMenuType<NavigationMenu, NavigationMenu.OpenData> NAVIGATION = Registry.register(BuiltInRegistries.MENU,
            Reg.id("navigation"), new ExtendedMenuType<>(NavigationMenu::new, NavigationMenu.OpenData.STREAM_CODEC));

    public static void init() {
    }
}
