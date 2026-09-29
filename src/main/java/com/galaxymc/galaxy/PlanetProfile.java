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

        public Palette withSnow(BlockState snow) {
            return new Palette(top, under, stone, deep, fluid, fluidCap, shore, snow, underDepth, alt);
        }

        public Palette withStone(BlockState stone, BlockState deep) {
            return new Palette(top, under, stone, deep, fluid, fluidCap, shore, snow, underDepth, alt);
        }
    }

    /**
     * One ore rule: {@code attempts} blobs of up to {@code size} blocks per chunk within [minY, maxY].
     * Fractional attempts are a per-chunk chance, so very rare ores can be expressed directly.
     */
    public record OreSpec(BlockState ore, BlockState deepOre, double attempts, int size, int minY, int maxY) {}

    /**
     * Inland lakes, independent of sea level. Each basin holds {@code fluid} (water, lava, or a solid
     * block such as packed ice for lakes frozen to the bottom); {@code cap} is an optional surface crust
     * (ice over water, obsidian skins on lava). A fraction {@code secondMix} of basins uses the second
     * fluid instead, which is how a cold volcanic world gets both frozen lakes and lava pools. A null
     * fluid makes dry basins: salt flats floored with {@code bed}.
     */
    public record Lakes(BlockState fluid, BlockState cap, BlockState shore, BlockState bed, double chance,
                        BlockState fluid2, BlockState cap2, BlockState shore2, double secondMix, double scale) {
        public Lakes(BlockState fluid, BlockState cap, BlockState shore, BlockState bed, double chance,
                     BlockState fluid2, BlockState cap2, BlockState shore2, double secondMix) {
            this(fluid, cap, shore, bed, chance, fluid2, cap2, shore2, secondMix, 1.0);
        }

        public static Lakes of(BlockState fluid, BlockState cap, BlockState shore, double chance) {
            return new Lakes(fluid, cap, shore, shore, chance, null, null, null, 0.0, 1.0);
        }

        public Lakes dry(BlockState bed) {
            return new Lakes(null, null, shore, bed, chance, fluid2, cap2, shore2, secondMix, scale);
        }

        public Lakes mixed(BlockState f2, BlockState c2, BlockState s2, double mix) {
            return new Lakes(fluid, cap, shore, bed, chance, f2, c2, s2, mix, scale);
        }

        /** Great lakes: basins (and the cells they are spread over) scaled up by {@code factor}. */
        public Lakes scaled(double factor) {
            return new Lakes(fluid, cap, shore, bed, chance, fluid2, cap2, shore2, secondMix, factor);
        }

        public Lakes withChance(double c) {
            return new Lakes(fluid, cap, shore, bed, c, fluid2, cap2, shore2, secondMix, scale);
        }
    }

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
    /** 0 = no ranges at all, 1 = alpine, 1.4 = titanic peaks brushing the build limit. */
    public final double mountains;
    /** Slope (blocks of rise per block) beyond which bare rock shows through the surface. */
    public final double cliffSlope;
    /** -1 (basalt-black worlds) .. +1 (chalk-white worlds). Drives stone tint and palette choice. */
    public final double brightness;
    /** Inland lakes, or null for none. */
    public final Lakes lakes;
    /** Rock strata exposed in cliffs and canyon walls, bottom to top; null for plain stone. */
    public final BlockState[] strata;
    /** Tint of the dust layer (alien sand, regolith, soil) on the client. */
    public final int dustTint;

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
        this.mountains = b.mountains;
        this.cliffSlope = b.cliffSlope;
        this.brightness = b.brightness;
        this.lakes = b.lakes;
        this.strata = b.strata == null ? null : b.strata.clone();
        this.dustTint = b.dustTint;
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
        private double mountains = 0.0;
        private double cliffSlope = 1.6;
        private double brightness = 0.0;
        private Lakes lakes;
        private BlockState[] strata;
        private int dustTint = 0xffffff;
        private String mountainText;
        private String lakeText;

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
        public Builder mountains(double v) { mountains = v; return this; }
        public Builder cliffs(double slope) { cliffSlope = slope; return this; }
        public Builder brightness(double v) { brightness = v; return this; }
        public Builder lakes(Lakes v) { lakes = v; return this; }
        public Builder strata(BlockState... v) { strata = v; return this; }
        public Builder dust(int tint) { dustTint = tint; return this; }
        public Builder stoneTint(int tint) { stoneTint = tint; return this; }
        public Builder describeMountains(String v) { mountainText = v; return this; }
        public Builder describeLakes(String v) { lakeText = v; return this; }
        public String describedMountains() { return mountainText; }
        public String describedLakes() { return lakeText; }
        public int grassColor() { return grassColor; }
        public PlanetType type() { return type; }
        public double baseTemp() { return baseTemp; }
        public int snowLine() { return snowLine; }
        public int baseHeight() { return baseHeight; }
        public int seaLevel() { return seaLevel; }
        public Palette palette() { return palette; }

        public PlanetProfile build() {
            return new PlanetProfile(this);
        }
    }
}
