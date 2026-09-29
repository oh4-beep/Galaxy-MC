package com.galaxymc.world;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.FrontierMap;
import com.galaxymc.galaxy.PlanetProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biomes for the frontier. Frontier biomes carry only atmosphere (sky, fog, water colour, weather and
 * ambient particles); terrain comes from the planet. Each planet picks one "sky" by index, and the
 * void between planets is deep space.
 */
public class FrontierBiomeSource extends BiomeSource {
    public static final MapCodec<FrontierBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(i, i.stable(FrontierBiomeSource::new)));

    private final HolderSet<Biome> biomes;
    private final List<Holder<Biome>> list;

    public FrontierBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
        this.list = biomes.stream().toList();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int x = quartX << 2;
        int z = quartZ << 2;
        PlanetProfile p = FrontierMap.planetAt(GalaxyMC.galaxySeed(), x, z);
        if (p == null || list.isEmpty()) {
            return list.get(0);
        }
        if (p.radius > 0 && FrontierMap.distanceFromCenter(x, z) > p.radius + 160) {
            return list.get(0);
        }
        return list.get(Math.min(p.biomeIndex, list.size() - 1));
    }
}
