package com.galaxymc.world;

import com.galaxymc.registry.Reg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Level keys for every Galaxy MC dimension. The dimensions themselves are defined in the datapack. */
public final class ModDimensions {
    private ModDimensions() {}

    public static final ResourceKey<Level> MOON = key("moon");
    public static final ResourceKey<Level> MARS = key("mars");
    public static final ResourceKey<Level> VENUS = key("venus");
    public static final ResourceKey<Level> MERCURY = key("mercury");
    public static final ResourceKey<Level> SUN = key("sun");
    public static final ResourceKey<Level> JUPITER = key("jupiter");
    public static final ResourceKey<Level> EUROPA = key("europa");
    public static final ResourceKey<Level> IO = key("io");
    public static final ResourceKey<Level> TITAN = key("titan");
    public static final ResourceKey<Level> SATURN = key("saturn");
    public static final ResourceKey<Level> PLUTO = key("pluto");
    public static final ResourceKey<Level> FRONTIER = key("frontier");

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, Reg.id(name));
    }

    public static boolean isGalaxy(ResourceKey<Level> dim) {
        return dim.identifier().getNamespace().equals(com.galaxymc.GalaxyMC.MOD_ID);
    }
}
