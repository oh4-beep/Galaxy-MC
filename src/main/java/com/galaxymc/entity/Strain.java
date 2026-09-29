package com.galaxymc.entity;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.util.Hash;
import com.galaxymc.util.NameGenerator;

/**
 * A planet's local strain of a species. The same species evolves differently on every world: its
 * markings take on a new colour, it grows larger or smaller, and on richer (higher tier) worlds it is
 * tougher and hits harder. Strains are a pure function of planet and species, so every Frost Stalker on
 * one world belongs to the same named strain - and there are as many strains as there are worlds.
 */
public record Strain(long seed, String name, int color, double size, double might) {
    public static final Strain NONE = new Strain(0, "", -1, 1.0, 1.0);

    public static Strain of(PlanetProfile planet, Species species) {
        if (planet == null) {
            return NONE;
        }
        long seed = Hash.of(planet.seed ^ 0x53545241L, Hash.stringSeed(species.id));
        Hash.Rng rng = new Hash.Rng(seed);
        String name = NameGenerator.strainName(seed);
        int color = NameGenerator.hsv(rng.nextDouble(), rng.range(0.45, 0.9), rng.range(0.7, 1.0));
        double size = species.giant ? rng.range(0.9, 1.15) : rng.range(0.82, 1.25);
        double might = 1.0 + 0.18 * Math.max(0, planet.tier - 1) + rng.range(-0.05, 0.12);
        return new Strain(seed, name, color, Math.round(size * 100) / 100.0, Math.round(might * 100) / 100.0);
    }

    public boolean isNone() {
        return seed == 0;
    }
}
