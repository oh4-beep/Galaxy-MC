package com.galaxymc.galaxy;

/**
 * Terrain archetypes. Every planet in the galaxy, hand-made or procedural, is one of these; the
 * archetype picks the terrain shaper, the block palette family, the flora and the creature habitats.
 */
public enum PlanetType {
    CRATERED("Cratered Moon", Climate.ANY, false, 0x9a9a9a),
    BARREN_ROCK("Barren Rock", Climate.ANY, false, 0x8b7b6b),
    DESERT("Desert World", Climate.HOT, true, 0xd8b36a),
    DUNE_SEA("Dune Sea", Climate.HOT, false, 0xe0c080),
    CANYON("Canyon World", Climate.TEMPERATE, false, 0xc0643a),
    OCEAN("Ocean World", Climate.TEMPERATE, true, 0x3a78c8),
    ICE("Ice World", Climate.COLD, false, 0xcfe8ff),
    TUNDRA("Tundra", Climate.COLD, true, 0xb8d0d8),
    LAVA("Lava World", Climate.HOT, false, 0xe0501a),
    ASH("Ash World", Climate.HOT, false, 0x5a5550),
    JUNGLE("Jungle World", Climate.TEMPERATE, true, 0x3ea040),
    FUNGAL("Fungal World", Climate.TEMPERATE, true, 0x9a5ac8),
    CRYSTAL("Crystal World", Climate.ANY, true, 0x7ad0e8),
    TOXIC("Toxic World", Climate.TEMPERATE, true, 0x9ac83a),
    GRASSLAND("Verdant Plains", Climate.TEMPERATE, true, 0x78b048),
    GAS_GIANT("Gas Giant Cloudlands", Climate.COLD, false, 0xd8b890),
    SHATTERED("Shattered World", Climate.ANY, false, 0x6a6a8a),
    STELLAR("Stellar Corona", Climate.HOT, false, 0xffa030),
    /** Earth-like: oceans, rivers, forests, plains, deserts, taiga and snowy peaks, with vanilla nature. */
    TERRAN("Terran World", Climate.TEMPERATE, true, 0x4aa83a),
    /** Black rock plains under a ring of great stratovolcanoes with lava-filled craters. */
    VOLCANIC("Volcanic World", Climate.HOT, false, 0xb0301a),
    /** Windswept grassland under a bruised sky; tornado alley. */
    STORM("Storm World", Climate.TEMPERATE, true, 0x5a7090);

    public enum Climate { HOT, TEMPERATE, COLD, ANY }

    public final String displayName;
    public final Climate climate;
    public final boolean flora;
    public final int mapColor;

    PlanetType(String displayName, Climate climate, boolean flora, int mapColor) {
        this.displayName = displayName;
        this.climate = climate;
        this.flora = flora;
        this.mapColor = mapColor;
    }

    public boolean hasSolidGround() {
        return this != GAS_GIANT && this != SHATTERED;
    }
}
