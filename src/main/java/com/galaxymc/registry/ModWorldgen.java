package com.galaxymc.registry;

import com.galaxymc.world.FrontierBiomeSource;
import com.galaxymc.world.PlanetChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Codec registrations for the custom chunk generator and biome source used by planet dimensions. */
public final class ModWorldgen {
    private ModWorldgen() {}

    public static void init() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Reg.id("planet"), PlanetChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Reg.id("frontier"), FrontierBiomeSource.CODEC);
    }
}
