package com.galaxymc.galaxy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything the game needs to know about one world: identity, climate, gravity, terrain knobs, block
 * palette, ore table, minerals and fauna. Sol bodies are hand-authored in {@link SolarSystem}; frontier
 * worlds are rolled by {@link FrontierPlanets}. Profiles are immutable and cached.
 */
public final class PlanetProfile {
    /**
     * Blocks that make up a world, top to bottom. {@code fluid} may be null for dry worlds; {@code alt}
     * is an optional second surface block laid in noise-driven patches (outcrops, ash spots, ice fields).
     */
    public record Palette(BlockState top, BlockState under, BlockState stone, BlockState deep,
                          BlockState fluid, BlockState fluidCap, BlockState shore, BlockState snow, int underDepth,
                          BlockState alt) {
        public Palette(BlockState top, BlockState under, BlockState stone, BlockState deep, BlockState fluid,
                       BlockState fluidCap, BlockState shore, BlockState snow, int underDepth) {
            this(top, under, stone, deep, fluid, fluidCap, shore, snow, underDepth, null);
        }

        public Palette withAlt(BlockState alt) {
            return new Palette(top, under, stone, deep, fluid, fluidCap, shore, snow, underDepth, alt);
        }
    }

    /**
     * One ore rule: {@code attempts} blobs of up to {@code size} blocks per chunk within [minY, maxY].
     * Fractional attempts are a per-chunk chance, so very rare ores can be expressed directly.
     */
    public record OreSpec(BlockState ore, BlockState deepOre, double attempts, int size, int minY, int maxY) {}

    public final String id;
    public final String name;
    public final String systemName;
    public final PlanetType type;
    public final long seed;
    public final double baseTemp;
    public final double tempSwing;
    public final double gravity;
    public final int radius;
    public final int seaLevel;
    public final int baseHeight;
    public final double amplitude;
    public final double frequency;
    public final int snowLine;
    public final Palette palette;
    public final List<OreSpec> ores;
    public final int tier;
    public final int danger;
    public final int biomeIndex;
    public final int grassColor;
    public final int foliageColor;
    public final int crystalColor;
    public final int stoneTint;
    public final long[] mineralSeeds;
    public final double orbitAu;
    public final boolean atmosphere;
    public final boolean subsurfaceOcean;
    public final double caveDensity;
    public final int wormsPerRegion;
    public final boolean giantBurrows;
    public final List<String> fauna;
    public final String description;

    private PlanetProfile(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.systemName = b.systemName;
        this.type = b.type;
        this.seed = b.seed;
        this.baseTemp = b.baseTemp;
        this.tempSwing = b.tempSwing;
        this.gravity = b.gravity;
        this.radius = b.radius;
        this.seaLevel = b.seaLevel;
        this.baseHeight = b.baseHeight;
        this.amplitude = b.amplitude;
        this.frequency = b.frequency;
        this.snowLine = b.snowLine;
        this.palette = b.palette;
        this.ores = List.copyOf(b.ores);
        this.tier = b.tier;
        this.danger = b.danger;
        this.biomeIndex = b.biomeIndex;
        this.grassColor = b.grassColor;
        this.foliageColor = b.foliageColor;
        this.crystalColor = b.crystalColor;
        this.stoneTint = b.stoneTint;
        this.mineralSeeds = b.mineralSeeds.clone();
        this.orbitAu = b.orbitAu;
        this.atmosphere = b.atmosphere;
        this.subsurfaceOcean = b.subsurfaceOcean;
        this.caveDensity = b.caveDensity;
        this.wormsPerRegion = b.wormsPerRegion;
        this.giantBurrows = b.giantBurrows;
        this.fauna = List.copyOf(b.fauna);
        this.description = b.description;
    }

    public boolean isFrontier() {
        return id.startsWith("f:");
    }

    /** Ambient temperature at a given point of the day cycle (0..1, 0.25 = noon). */
    public double temperatureAt(double dayFraction) {
        double sun = Math.sin((dayFraction) * Math.PI * 2.0);
        return baseTemp + tempSwing * sun;
    }

    public static Builder builder(String id, String name, PlanetType type, long seed) {
        return new Builder(id, name, type, seed);
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final PlanetType type;
        private final long seed;
        private String systemName = "Sol";
        private double baseTemp = 20;
        private double tempSwing = 0;
        private double gravity = 1.0;
        private int radius = 0;
        private int seaLevel = 63;
        private int baseHeight = 72;
        private double amplitude = 24;
        private double frequency = 1.0;
        private int snowLine = Integer.MAX_VALUE;
        private Palette palette;
        private final List<OreSpec> ores = new ArrayList<>();
        private int tier = 1;
        private int danger = 1;
        private int biomeIndex = 0;
        private int grassColor = 0x6aa84f;
        private int foliageColor = 0x4a8a3a;
        private int crystalColor = 0x7ad0e8;
        private int stoneTint = 0xffffff;
        private long[] mineralSeeds = new long[4];
        private double orbitAu = 1.0;
        private boolean atmosphere = true;
        private boolean subsurfaceOcean = false;
        private double caveDensity = 1.0;
        private int wormsPerRegion = 2;
        private boolean giantBurrows = false;
        private final List<String> fauna = new ArrayList<>();
        private String description = "";

        private Builder(String id, String name, PlanetType type, long seed) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.seed = seed;
            for (int i = 0; i < 4; i++) {
                mineralSeeds[i] = com.galaxymc.util.Hash.of(seed, 0x4D494E4CL, i);
            }
        }

        public Builder system(String v) { systemName = v; return this; }
        public Builder temp(double base, double swing) { baseTemp = base; tempSwing = swing; return this; }
        public Builder gravity(double v) { gravity = v; return this; }
        public Builder radius(int v) { radius = v; return this; }
        public Builder sea(int v) { seaLevel = v; return this; }
        public Builder terrain(int base, double amp, double freq) { baseHeight = base; amplitude = amp; frequency = freq; return this; }
        public Builder snowLine(int v) { snowLine = v; return this; }
        public Builder palette(Palette v) { palette = v; return this; }
        public Builder ore(OreSpec v) { ores.add(v); return this; }
        public Builder tier(int v) { tier = v; return this; }
        public Builder danger(int v) { danger = v; return this; }
        public Builder biome(int v) { biomeIndex = v; return this; }
        public Builder colors(int grass, int foliage, int crystal, int stone) {
            grassColor = grass; foliageColor = foliage; crystalColor = crystal; stoneTint = stone; return this;
        }
        public Builder orbit(double au) { orbitAu = au; return this; }
        public Builder atmosphere(boolean v) { atmosphere = v; return this; }
        public Builder subsurfaceOcean(boolean v) { subsurfaceOcean = v; return this; }
        public Builder caves(double density, int worms, boolean burrows) {
            caveDensity = density; wormsPerRegion = worms; giantBurrows = burrows; return this;
        }
        public Builder fauna(List<String> v) { fauna.clear(); fauna.addAll(v); return this; }
        public Builder fauna(String... v) { fauna.clear(); fauna.addAll(List.of(v)); return this; }
        public Builder description(String v) { description = v; return this; }

        public PlanetProfile build() {
            return new PlanetProfile(this);
        }
    }
}
