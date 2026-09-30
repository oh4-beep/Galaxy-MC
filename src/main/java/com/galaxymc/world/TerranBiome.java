package com.galaxymc.world;

/**
 * Local biomes of a Terran (Earth-like) world. A Terran planet is not one biome but a whole climate map:
 * temperature and rainfall noise, the planet's own mean temperature and altitude pick one of these per
 * column. The surface blocks, the vegetation and the sky (so grass colour, rain and snow) all follow it.
 */
public enum TerranBiome {
    OCEAN("terran", false),
    FROZEN_OCEAN("terran_frozen", true),
    BEACH("terran", false),
    SNOWY_BEACH("terran_frozen", true),
    RIVER("terran", false),
    PLAINS("terran", false),
    SUNFLOWER_PLAINS("terran", false),
    FOREST("terran", false),
    BIRCH_FOREST("terran", false),
    DARK_FOREST("terran", false),
    FLOWER_FOREST("terran", false),
    MEADOW("terran", false),
    CHERRY_GROVE("terran", false),
    TAIGA("terran_boreal", false),
    OLD_GROWTH_TAIGA("terran_boreal", false),
    SNOWY_TAIGA("terran_frozen", true),
    SNOWY_PLAINS("terran_frozen", true),
    SNOWY_PEAKS("terran_frozen", true),
    STONY_PEAKS("terran", false),
    DESERT("terran_arid", false),
    SAVANNA("terran_arid", false),
    BADLANDS("terran_arid", false),
    JUNGLE("terran_tropical", false),
    SWAMP("terran_tropical", false);

    /** Name of the frontier sky/biome this local biome is rendered with (see FrontierPlanets.SKIES). */
    public final String sky;
    public final boolean snowy;

    TerranBiome(String sky, boolean snowy) {
        this.sky = sky;
        this.snowy = snowy;
    }

    public boolean wet() {
        return this == OCEAN || this == FROZEN_OCEAN || this == RIVER;
    }
}
