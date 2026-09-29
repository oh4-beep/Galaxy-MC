package com.galaxymc.galaxy;

import com.galaxymc.galaxy.PlanetProfile.OreSpec;
import com.galaxymc.galaxy.PlanetProfile.Palette;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.util.Hash;
import com.galaxymc.util.NameGenerator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rolls a full {@link PlanetProfile} for any orbit of any procedural star.
 *
 * <p>Temperature comes from real-ish physics (stellar luminosity over orbital distance, plus a
 * greenhouse term), the archetype is chosen from what is plausible at that temperature, and every
 * remaining knob - terrain scale, sea level, colours, ores, danger, mineral tier - is drawn from the
 * planet seed. Distance from Sol raises the tier, so the richest worlds are always further out.
 */
public final class FrontierPlanets {
    private FrontierPlanets() {}

    /** Sky/biome variants for the frontier dimension, in the order listed by its biome source. */
    public static final String[] SKIES = {
            "deep_space", "azure", "teal", "emerald", "toxic", "amber", "crimson", "rose", "violet",
            "indigo", "ashen", "black", "white", "gold", "inferno", "snow", "aurora"
    };

    private static final Map<String, PlanetProfile> CACHE = new ConcurrentHashMap<>();

    public static String id(Star star, int index) {
        return "f:" + star.sx() + ":" + star.sz() + ":" + star.slot() + ":" + index;
    }

    /** Profile for orbit {@code index} of {@code star}; index 0 is the stellar corona. Null if absent. */
    public static PlanetProfile planet(long galaxySeed, Star star, int index) {
        if (star == null || star.sol() || index < 0 || index > star.planetCount()) {
            return null;
        }
        if (index == 0 && !star.starClass().hasCorona()) {
            return null;
        }
        String key = galaxySeed + "/" + id(star, index);
        PlanetProfile cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        PlanetProfile p = index == 0 ? corona(galaxySeed, star) : generate(galaxySeed, star, index);
        if (CACHE.size() > 4096) {
            CACHE.clear();
        }
        CACHE.put(key, p);
        return p;
    }

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    private static int tierFor(long galaxySeed, Star star, int danger) {
        Star sol = Galaxy.sol(galaxySeed);
        double ly = sol == null ? 0 : star.distanceTo(sol);
        return Math.max(1, 1 + (int) (ly / 120.0) + danger / 3);
    }

    private static PlanetProfile corona(long galaxySeed, Star star) {
        long seed = Hash.of(star.seed(), 0x434F524EL);
        Hash.Rng rng = new Hash.Rng(seed);
        int temp = star.starClass().coronaTemp;
        int danger = Math.min(10, 7 + temp / 3000);
        PlanetProfile.Builder b = PlanetProfile.builder(id(star, 0), star.name() + " Corona", PlanetType.STELLAR, seed)
                .system(star.name())
                .temp(temp, 0).gravity(rng.range(1.8, 2.5)).radius(rng.nextInt(700, 1000))
                .terrain(58, rng.range(20, 40), rng.range(0.8, 1.3)).sea(64).atmosphere(true).orbit(0.0)
                .danger(danger).biome(indexOf("inferno"))
                .palette(new Palette(s(ModBlocks.SOLAR_SLAG), s(ModBlocks.SOLAR_SLAG), s(ModBlocks.SOLAR_SLAG),
                        s(Blocks.MAGMA_BLOCK), s(Blocks.LAVA), s(ModBlocks.SOLAR_PLASMA), s(ModBlocks.SOLAR_PLASMA), null, 3))
                .ore(new OreSpec(s(ModBlocks.HELIONITE_ORE), null, 3 + rng.nextInt(3), 5, 0, 140))
                .ore(new OreSpec(s(ModBlocks.EXOTIC_ORE), s(ModBlocks.DEEP_EXOTIC_ORE), 4, 6, -60, 130))
                .caves(0.4, 1, false)
                .colors(star.starClass().color, 0xff5010, star.starClass().color, 0xffffff)
                .description("The burning surface of " + star.name() + ".");
        b.tier(tierFor(galaxySeed, star, danger) + 1);
        return b.build();
    }

    private static PlanetProfile generate(long galaxySeed, Star star, int index) {
        long seed = Hash.of(star.seed(), 0x504C4E54L, index);
        Hash.Rng rng = new Hash.Rng(seed);
        StarClass cls = star.starClass();

        double lum = Math.max(cls.luminosity, 0.0005);
        // Orbits scale with the star's light, as real systems do: red dwarfs keep their worlds close,
        // blue giants fling theirs far out. Hot inner worlds and cold outer ones exist around every star.
        double au = rng.range(0.24, 0.5) * Math.pow(1.62, index - 1) * rng.range(0.85, 1.15) * Math.pow(lum, 0.42);
        double kelvin = 278.0 * Math.pow(lum, 0.25) / Math.sqrt(au);
        if (cls == StarClass.NEUTRON || cls == StarClass.BLACK_HOLE) {
            kelvin = rng.range(20, 90);
        }
        double greenhouse = rng.chance(0.6) ? rng.range(0, 70) : rng.range(0, 12);
        double baseTemp = kelvin - 273.0 + greenhouse;

        PlanetType type = pickType(rng, baseTemp, index);
        if (cls == StarClass.NEUTRON || cls == StarClass.BLACK_HOLE) {
            type = rng.chance(0.5) ? PlanetType.SHATTERED : PlanetType.CRYSTAL;
        }

        // Pull "life-bearing" archetypes towards temperatures they can plausibly have.
        baseTemp = switch (type) {
            case JUNGLE, GRASSLAND, OCEAN, FUNGAL -> clamp(baseTemp, -15, 55);
            case ICE, TUNDRA -> Math.min(baseTemp, -5);
            case LAVA -> Math.max(baseTemp, 250);
            case ASH -> Math.max(baseTemp, 80);
            default -> baseTemp;
        };

        boolean atmosphere = switch (type) {
            case CRATERED, SHATTERED, BARREN_ROCK -> rng.chance(0.2);
            default -> true;
        };
        double swing = atmosphere ? rng.range(2, 25) : rng.range(40, 160);

        String name = NameGenerator.planetName(seed, star.name(), index);
        int radius = switch (type) {
            case GAS_GIANT -> 1020;
            case CRATERED -> rng.nextInt(420, 760);
            case SHATTERED -> rng.nextInt(700, 960);
            default -> rng.nextInt(560, 1000);
        };
        double gravity = switch (type) {
            case GAS_GIANT -> rng.range(1.6, 2.6);
            case SHATTERED -> rng.range(0.15, 0.45);
            case CRATERED -> rng.range(0.12, 0.5);
            default -> rng.range(0.35, 1.7);
        };

        int danger = 1;
        danger += (int) Math.min(4, Math.abs(baseTemp - 20) / 90.0);
        danger += switch (type) {
            case LAVA, TOXIC, SHATTERED, STELLAR -> 2;
            case JUNGLE, FUNGAL, CRYSTAL, ASH -> 1;
            default -> 0;
        };
        danger += rng.nextInt(3);
        danger = Math.min(10, danger);

        PlanetProfile.Builder b = PlanetProfile.builder(id(star, index), name, type, seed)
                .system(star.name()).temp(Math.round(baseTemp), Math.round(swing)).gravity(Math.round(gravity * 100) / 100.0)
                .radius(radius).atmosphere(atmosphere).orbit(Math.round(au * 100) / 100.0).danger(danger);
        b.tier(tierFor(galaxySeed, star, danger));
        shape(b, rng, type);
        colours(b, rng, type);
        palette(b, rng, type);
        ores(b, rng, type);
        traits(b, new Hash.Rng(Hash.of(seed, 0x54524149L)), type, baseTemp);
        b.description(describe(type, baseTemp, gravity, atmosphere, b));
        return b.build();
    }

    private static PlanetType pickType(Hash.Rng rng, double t, int index) {
        if (index >= 4 && rng.chance(0.3)) {
            return PlanetType.GAS_GIANT;
        }
        if (rng.chance(0.04)) {
            return PlanetType.SHATTERED;
        }
        PlanetType[] options;
        if (t > 600) {
            options = new PlanetType[]{PlanetType.LAVA, PlanetType.LAVA, PlanetType.ASH, PlanetType.BARREN_ROCK};
        } else if (t > 200) {
            options = new PlanetType[]{PlanetType.ASH, PlanetType.DESERT, PlanetType.BARREN_ROCK, PlanetType.TOXIC,
                    PlanetType.LAVA, PlanetType.CRATERED, PlanetType.DUNE_SEA};
        } else if (t > 55) {
            options = new PlanetType[]{PlanetType.DESERT, PlanetType.DUNE_SEA, PlanetType.CANYON, PlanetType.TOXIC,
                    PlanetType.CRYSTAL, PlanetType.BARREN_ROCK, PlanetType.ASH};
        } else if (t > -10) {
            options = new PlanetType[]{PlanetType.JUNGLE, PlanetType.OCEAN, PlanetType.GRASSLAND, PlanetType.FUNGAL,
                    PlanetType.CRYSTAL, PlanetType.DESERT, PlanetType.TOXIC, PlanetType.CANYON, PlanetType.JUNGLE,
                    PlanetType.GRASSLAND};
        } else if (t > -90) {
            options = new PlanetType[]{PlanetType.TUNDRA, PlanetType.ICE, PlanetType.CRYSTAL, PlanetType.FUNGAL,
                    PlanetType.CANYON, PlanetType.CRATERED, PlanetType.BARREN_ROCK, PlanetType.TUNDRA};
        } else {
            options = new PlanetType[]{PlanetType.ICE, PlanetType.ICE, PlanetType.CRATERED, PlanetType.BARREN_ROCK,
                    PlanetType.CRYSTAL, PlanetType.SHATTERED};
        }
        return rng.pick(options);
    }

    private static void shape(PlanetProfile.Builder b, Hash.Rng rng, PlanetType type) {
        double f = rng.range(0.75, 1.35);
        switch (type) {
            case CRATERED -> b.terrain(76, rng.range(14, 26), f).caves(1.0, 2, false);
            case BARREN_ROCK -> b.terrain(80, rng.range(30, 60), f).caves(1.1, 3, rng.chance(0.3));
            case DESERT -> b.terrain(72, rng.range(14, 30), f).sea(rng.chance(0.3) ? 58 : -64).caves(0.9, 2, rng.chance(0.5));
            case DUNE_SEA -> b.terrain(74, rng.range(18, 30), f).sea(-64).caves(0.6, 1, true);
            case CANYON -> b.terrain(96, rng.range(36, 56), f).sea(rng.chance(0.4) ? 50 : -64).caves(1.0, 2, false);
            case OCEAN -> b.terrain(50, rng.range(26, 40), f).sea(63).caves(0.7, 1, false);
            case ICE -> b.terrain(84, rng.range(18, 40), f).sea(rng.chance(0.5) ? 62 : -64).snowLine(0).caves(0.9, 2, false);
            case TUNDRA -> b.terrain(72, rng.range(12, 26), f).sea(62).snowLine(rng.nextInt(66, 78)).caves(1.0, 2, false);
            case LAVA -> b.terrain(47, rng.range(20, 40), f).sea(56).caves(0.8, 2, false);
            case ASH -> b.terrain(74, rng.range(24, 44), f).sea(rng.chance(0.5) ? 48 : -64).caves(0.9, 2, false);
            case JUNGLE -> b.terrain(74, rng.range(28, 52), f).sea(62).caves(1.1, 3, rng.chance(0.3));
            case FUNGAL -> b.terrain(72, rng.range(20, 36), f).sea(62).caves(1.3, 3, false);
            case CRYSTAL -> b.terrain(78, rng.range(24, 46), f).sea(rng.chance(0.5) ? 60 : -64).caves(1.2, 3, false);
            case TOXIC -> b.terrain(68, rng.range(14, 30), f).sea(62).caves(1.0, 2, rng.chance(0.4));
            case GRASSLAND -> b.terrain(72, rng.range(10, 24), f).sea(62).caves(1.0, 2, false);
            case GAS_GIANT -> b.terrain(110, rng.range(30, 50), f).sea(-64).caves(0, 0, false);
            case SHATTERED -> b.terrain(100, rng.range(40, 70), f).sea(-64).caves(0, 0, false);
            case STELLAR -> b.terrain(58, 30, f).sea(64).caves(0.4, 1, false);
        }
    }

    private static final Map<PlanetType, String[]> SKY_OPTIONS = Map.ofEntries(
            Map.entry(PlanetType.JUNGLE, new String[]{"azure", "teal", "emerald", "violet", "rose"}),
            Map.entry(PlanetType.GRASSLAND, new String[]{"azure", "gold", "emerald", "teal"}),
            Map.entry(PlanetType.OCEAN, new String[]{"azure", "teal", "indigo"}),
            Map.entry(PlanetType.FUNGAL, new String[]{"violet", "teal", "rose", "aurora"}),
            Map.entry(PlanetType.CRYSTAL, new String[]{"violet", "indigo", "teal", "rose", "aurora"}),
            Map.entry(PlanetType.TOXIC, new String[]{"toxic", "emerald", "amber"}),
            Map.entry(PlanetType.DESERT, new String[]{"amber", "gold", "rose", "azure"}),
            Map.entry(PlanetType.DUNE_SEA, new String[]{"amber", "gold", "rose"}),
            Map.entry(PlanetType.CANYON, new String[]{"amber", "crimson", "rose"}),
            Map.entry(PlanetType.ICE, new String[]{"white", "snow", "indigo", "black"}),
            Map.entry(PlanetType.TUNDRA, new String[]{"snow", "white", "azure"}),
            Map.entry(PlanetType.LAVA, new String[]{"crimson", "inferno", "amber"}),
            Map.entry(PlanetType.ASH, new String[]{"ashen", "crimson"}),
            Map.entry(PlanetType.CRATERED, new String[]{"black"}),
            Map.entry(PlanetType.BARREN_ROCK, new String[]{"black", "ashen", "amber"}),
            Map.entry(PlanetType.GAS_GIANT, new String[]{"gold", "amber", "azure", "violet", "rose"}),
            Map.entry(PlanetType.SHATTERED, new String[]{"black", "indigo", "violet"}),
            Map.entry(PlanetType.STELLAR, new String[]{"inferno"}));

    public static int indexOf(String sky) {
        for (int i = 0; i < SKIES.length; i++) {
            if (SKIES[i].equals(sky)) {
                return i;
            }
        }
        return 0;
    }

    private static void colours(PlanetProfile.Builder b, Hash.Rng rng, PlanetType type) {
        String[] skies = SKY_OPTIONS.get(type);
        boolean airless = false;
        b.biome(indexOf(rng.pick(skies)));
        double hue = rng.nextDouble();
        double hueShift = rng.range(0.05, 0.2) * (rng.chance(0.5) ? 1 : -1);
        int grass;
        int foliage;
        switch (type) {
            case JUNGLE, GRASSLAND -> {
                // Mostly greens and teals, but one world in four goes somewhere strange.
                double h = rng.chance(0.75) ? rng.range(0.18, 0.45) : hue;
                grass = NameGenerator.hsv(h, rng.range(0.45, 0.8), rng.range(0.55, 0.85));
                foliage = NameGenerator.hsv(h + hueShift, rng.range(0.5, 0.85), rng.range(0.45, 0.8));
            }
            case FUNGAL -> {
                double h = rng.range(0.7, 1.05);
                grass = NameGenerator.hsv(h, rng.range(0.4, 0.75), rng.range(0.5, 0.8));
                foliage = NameGenerator.hsv(h + hueShift, rng.range(0.5, 0.9), rng.range(0.6, 0.9));
            }
            case TOXIC -> {
                double h = rng.range(0.14, 0.3);
                grass = NameGenerator.hsv(h, rng.range(0.6, 0.9), rng.range(0.55, 0.85));
                foliage = NameGenerator.hsv(h + 0.05, rng.range(0.6, 0.9), rng.range(0.6, 0.9));
            }
            case ICE, TUNDRA -> {
                grass = NameGenerator.hsv(rng.range(0.45, 0.65), rng.range(0.05, 0.3), rng.range(0.75, 0.95));
                foliage = NameGenerator.hsv(rng.range(0.45, 0.7), rng.range(0.2, 0.5), rng.range(0.7, 0.95));
            }
            default -> {
                grass = NameGenerator.hsv(hue, rng.range(0.2, 0.7), rng.range(0.45, 0.85));
                foliage = NameGenerator.hsv(hue + hueShift, rng.range(0.3, 0.8), rng.range(0.45, 0.85));
            }
        }
        int crystal = NameGenerator.hsv(rng.nextDouble(), rng.range(0.4, 0.8), rng.range(0.75, 1.0));
        int stone = NameGenerator.hsv(rng.nextDouble(), rng.range(0.0, 0.25), rng.range(0.8, 1.0));
        b.colors(grass, foliage, crystal, stone);
    }

    private static void palette(PlanetProfile.Builder b, Hash.Rng rng, PlanetType type) {
        BlockState stone = s(ModBlocks.ALIEN_STONE);
        BlockState deep = s(ModBlocks.ALIEN_DEEP_STONE);
        BlockState water = s(Blocks.WATER);
        BlockState lava = s(Blocks.LAVA);
        Palette p = switch (type) {
            case JUNGLE, GRASSLAND -> new Palette(s(ModBlocks.ALIEN_GRASS), s(ModBlocks.ALIEN_SOIL), stone, deep, water, null,
                    s(ModBlocks.ALIEN_SAND), null, 4);
            case FUNGAL -> new Palette(s(ModBlocks.FUNGAL_TURF), s(ModBlocks.ALIEN_SOIL), stone, deep, water, null,
                    s(ModBlocks.ALIEN_SOIL), null, 4);
            case TOXIC -> new Palette(s(ModBlocks.TOXIC_TURF), s(ModBlocks.ALIEN_SOIL), stone, deep, water, null,
                    s(Blocks.MUD), null, 3);
            case OCEAN -> new Palette(s(ModBlocks.ALIEN_SAND), s(ModBlocks.ALIEN_SAND), stone, deep, water, null,
                    s(ModBlocks.ALIEN_SAND), null, 5);
            case DESERT -> new Palette(s(ModBlocks.ALIEN_SAND), s(ModBlocks.ALIEN_SAND), rng.chance(0.5) ? s(Blocks.SANDSTONE) : stone,
                    deep, water, null, s(ModBlocks.ALIEN_SAND), null, 5);
            case DUNE_SEA -> new Palette(s(ModBlocks.ALIEN_SAND), s(ModBlocks.ALIEN_SAND), s(Blocks.SANDSTONE), deep, null, null,
                    s(ModBlocks.ALIEN_SAND), null, 8);
            case CANYON -> new Palette(s(Blocks.RED_SAND), s(Blocks.TERRACOTTA), s(Blocks.TERRACOTTA), deep, water, null,
                    s(Blocks.RED_SAND), null, 3);
            case ICE -> new Palette(s(Blocks.SNOW_BLOCK), s(Blocks.PACKED_ICE), stone, deep, water, s(Blocks.ICE),
                    s(Blocks.PACKED_ICE), s(Blocks.SNOW_BLOCK), 5);
            case TUNDRA -> new Palette(s(ModBlocks.ALIEN_GRASS), s(ModBlocks.ALIEN_SOIL), stone, deep, water, s(Blocks.ICE),
                    s(Blocks.GRAVEL), s(Blocks.SNOW_BLOCK), 3);
            case LAVA -> new Palette(s(ModBlocks.SCORCHED_ROCK), s(Blocks.BASALT), s(Blocks.BLACKSTONE), deep, lava, null,
                    s(Blocks.MAGMA_BLOCK), null, 3);
            case ASH -> new Palette(s(ModBlocks.ASH_BLOCK), s(ModBlocks.SCORCHED_ROCK), s(ModBlocks.SCORCHED_ROCK), deep, lava, null,
                    s(Blocks.BASALT), null, 3);
            case CRYSTAL -> new Palette(s(ModBlocks.ALIEN_REGOLITH), s(ModBlocks.ALIEN_STONE), stone, deep, water, null,
                    s(ModBlocks.CRYSTAL_BLOCK), null, 2);
            case CRATERED -> new Palette(s(ModBlocks.ALIEN_REGOLITH), s(ModBlocks.ALIEN_REGOLITH), stone, deep, null, null,
                    s(ModBlocks.ALIEN_REGOLITH), null, 4);
            case BARREN_ROCK -> new Palette(s(Blocks.GRAVEL), stone, stone, deep, null, null, s(Blocks.GRAVEL), null, 2);
            case GAS_GIANT -> new Palette(s(ModBlocks.JOVIAN_CLOUD), s(ModBlocks.JOVIAN_CLOUD), s(ModBlocks.JOVIAN_STONE),
                    s(ModBlocks.JOVIAN_STONE), null, null, s(ModBlocks.JOVIAN_CLOUD), null, 3);
            case SHATTERED -> new Palette(s(ModBlocks.ALIEN_REGOLITH), stone, stone, deep, null, null,
                    s(ModBlocks.CRYSTAL_BLOCK), null, 2);
            case STELLAR -> new Palette(s(ModBlocks.SOLAR_SLAG), s(ModBlocks.SOLAR_SLAG), s(ModBlocks.SOLAR_SLAG),
                    s(Blocks.MAGMA_BLOCK), lava, s(ModBlocks.SOLAR_PLASMA), s(ModBlocks.SOLAR_PLASMA), null, 3);
        };
        BlockState alt = switch (type) {
            case JUNGLE, GRASSLAND -> rng.chance(0.5) ? s(ModBlocks.ALIEN_SOIL) : null;
            case CRYSTAL -> s(ModBlocks.CRYSTAL_BLOCK);
            case BARREN_ROCK, CRATERED -> s(ModBlocks.ALIEN_STONE);
            case ASH -> s(ModBlocks.SCORCHED_ROCK);
            case LAVA -> s(Blocks.MAGMA_BLOCK);
            case TUNDRA -> s(Blocks.SNOW_BLOCK);
            case ICE -> s(Blocks.BLUE_ICE);
            case DESERT -> s(ModBlocks.ALIEN_REGOLITH);
            case CANYON -> s(Blocks.DYED_TERRACOTTA.orange());
            case FUNGAL -> s(Blocks.MYCELIUM);
            case TOXIC -> s(Blocks.MUD);
            case OCEAN -> s(Blocks.GRAVEL);
            default -> null;
        };
        b.palette(alt == null ? p : p.withAlt(alt));
    }

    private static void ores(PlanetProfile.Builder b, Hash.Rng rng, PlanetType type) {
        double richness = rng.range(0.6, 1.8);
        b.ore(new OreSpec(s(ModBlocks.ALIEN_COAL_ORE), null, 14 * richness, 12, 0, 160));
        b.ore(new OreSpec(s(ModBlocks.ALIEN_IRON_ORE), null, 10 * richness, 9, -60, 120));
        b.ore(new OreSpec(s(ModBlocks.ALIEN_COPPER_ORE), null, 8 * richness, 10, -20, 110));
        b.ore(new OreSpec(s(ModBlocks.ALIEN_GOLD_ORE), null, 3 * richness, 8, -60, 40));
        b.ore(new OreSpec(s(ModBlocks.ALIEN_REDSTONE_ORE), null, 4 * richness, 8, -60, 20));
        b.ore(new OreSpec(s(ModBlocks.ALIEN_LAPIS_ORE), null, 2 * richness, 7, -40, 40));
        b.ore(new OreSpec(s(ModBlocks.ALIEN_DIAMOND_ORE), null, rng.range(0.6, 2.2) * richness, 6, -60, 16));
        if (rng.chance(0.4)) {
            b.ore(new OreSpec(s(ModBlocks.ALIEN_EMERALD_ORE), null, rng.range(0.5, 2.0), 4, 0, 160));
        }
        b.ore(new OreSpec(s(ModBlocks.EXOTIC_ORE), s(ModBlocks.DEEP_EXOTIC_ORE), rng.range(2.5, 5.0), 6, -60, 130));
        if (type == PlanetType.ICE || type == PlanetType.TUNDRA) {
            b.ore(new OreSpec(s(ModBlocks.CRYONITE_ORE), null, rng.range(2, 6), 6, -60, 120));
        }
        if (type == PlanetType.LAVA || type == PlanetType.ASH) {
            b.ore(new OreSpec(s(ModBlocks.PYROCITE_ORE), null, rng.range(2, 6), 6, -60, 120));
        }
        if (type == PlanetType.GAS_GIANT) {
            b.ore(new OreSpec(s(ModBlocks.STORM_CRYSTAL_ORE), null, rng.range(3, 6), 6, 40, 200));
        }
        if (type == PlanetType.CRATERED || type == PlanetType.SHATTERED) {
            b.ore(new OreSpec(s(ModBlocks.LUNAR_TITANIUM_ORE), null, rng.range(2, 6), 6, -60, 200));
        }
        if (rng.chance(0.15)) {
            b.ore(new OreSpec(s(ModBlocks.PLUTONITE_ORE), null, rng.range(0.5, 2), 4, -60, 40));
        }
    }

    private static String describe(PlanetType type, double temp, double gravity, boolean atmosphere, PlanetProfile.Builder b) {
        String climate = temp > 400 ? "blistering" : temp > 120 ? "scorching" : temp > 45 ? "hot" : temp > 5 ? "mild"
                : temp > -40 ? "cold" : temp > -120 ? "frozen" : "deep-frozen";
        String weight = gravity > 1.6 ? "crushing" : gravity > 1.15 ? "heavy" : gravity < 0.45 ? "feather-light" : "familiar";
        StringBuilder sb = new StringBuilder("A ").append(climate).append(' ').append(type.displayName.toLowerCase())
                .append(" with ").append(weight).append(" gravity").append(atmosphere ? "." : " and no air to speak of.");
        if (b.describedMountains() != null) {
            sb.append(' ').append(b.describedMountains());
        }
        if (b.describedLakes() != null) {
            sb.append(' ').append(b.describedLakes());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ terrain traits

    /** Badlands-style strata for canyon worlds. */
    private static BlockState[] canyonBands() {
        return new BlockState[]{
                s(Blocks.TERRACOTTA), s(Blocks.DYED_TERRACOTTA.orange()), s(Blocks.DYED_TERRACOTTA.orange()),
                s(Blocks.DYED_TERRACOTTA.yellow()), s(Blocks.DYED_TERRACOTTA.brown()), s(Blocks.TERRACOTTA),
                s(Blocks.DYED_TERRACOTTA.red()), s(Blocks.DYED_TERRACOTTA.white()), s(Blocks.DYED_TERRACOTTA.lightGray()),
                s(Blocks.DYED_TERRACOTTA.orange()), s(Blocks.TERRACOTTA), s(Blocks.DYED_TERRACOTTA.red())
        };
    }

    private static final int CLIMATE_SCORCHING = 0;
    private static final int CLIMATE_HOT = 1;
    private static final int CLIMATE_TEMPERATE = 2;
    private static final int CLIMATE_COLD = 3;
    private static final int CLIMATE_FROZEN = 4;

    private static int climate(double t) {
        if (t > 300) {
            return CLIMATE_SCORCHING;
        }
        if (t > 75) {
            return CLIMATE_HOT;
        }
        if (t > -5) {
            return CLIMATE_TEMPERATE;
        }
        if (t > -80) {
            return CLIMATE_COLD;
        }
        return CLIMATE_FROZEN;
    }

    /**
     * Rolls the traits that make two worlds of the same archetype look nothing alike: how mountainous
     * they are, how dark or pale their rock is, whether their lakes hold water, ice, lava or nothing but
     * salt, and where snow starts on the peaks.
     */
    private static void traits(PlanetProfile.Builder b, Hash.Rng rng, PlanetType type, double temp) {
        if (type == PlanetType.GAS_GIANT || type == PlanetType.SHATTERED || type == PlanetType.STELLAR) {
            b.brightness(rng.range(-0.4, 0.4));
            return;
        }
        int climate = climate(temp);

        // ---- mountains: 30% flat, 25% hills, 25% ranges, 15% alpine, 5% titanic
        double roll = rng.nextDouble();
        double mountains;
        if (roll < 0.30) {
            mountains = 0.0;
        } else if (roll < 0.55) {
            mountains = rng.range(0.15, 0.35);
        } else if (roll < 0.80) {
            mountains = rng.range(0.45, 0.75);
        } else if (roll < 0.95) {
            mountains = rng.range(0.8, 1.05);
        } else {
            mountains = rng.range(1.1, 1.35);
        }
        mountains *= switch (type) {
            case DUNE_SEA, OCEAN, TOXIC, FUNGAL -> 0.45;
            case GRASSLAND, CRATERED, DESERT -> 0.7;
            case BARREN_ROCK, CRYSTAL, ICE, LAVA, CANYON, TUNDRA -> 1.2;
            default -> 1.0;
        };
        mountains = Math.min(1.4, mountains);
        b.mountains(Math.round(mountains * 100) / 100.0);
        b.describeMountains(mountains > 1.05 ? "Titanic peaks tear at the sky."
                : mountains > 0.75 ? "Alpine ranges cut the horizon."
                : mountains > 0.4 ? "Rugged ranges divide its lowlands." : null);

        // ---- brightness: dark basalt worlds to chalk-white ones
        double bias = switch (type) {
            case LAVA, ASH -> -0.55;
            case ICE, TUNDRA -> 0.4;
            case DESERT, DUNE_SEA -> 0.15;
            case CRYSTAL -> 0.2;
            default -> 0.0;
        };
        double brightness = Math.max(-1.0, Math.min(1.0, rng.range(-1.0, 1.0) * 0.8 + bias));
        b.brightness(Math.round(brightness * 100) / 100.0);
        double stoneHue = rng.nextDouble();
        double stoneSat = rng.chance(0.25) ? rng.range(0.2, 0.42) : rng.range(0.0, 0.16);
        b.stoneTint(NameGenerator.hsv(stoneHue, stoneSat, 0.6 + 0.36 * brightness));
        boolean sandy = type == PlanetType.DESERT || type == PlanetType.DUNE_SEA || type == PlanetType.CANYON;
        double dustHue = rng.chance(sandy ? 0.2 : 0.6) ? stoneHue + rng.range(-0.06, 0.06) : rng.range(0.04, 0.14);
        b.dust(NameGenerator.hsv(dustHue, rng.range(0.1, 0.45), Math.min(1.0, 0.66 + 0.32 * brightness)));

        PlanetProfile.Palette pal = b.palette();
        if (pal != null && pal.stone() != null && pal.stone().getBlock() == ModBlocks.ALIEN_STONE && rng.chance(0.3)) {
            // A few worlds are built from recognisable rock instead of tinted alien stone.
            BlockState[] rocks = brightness < -0.35
                    ? new BlockState[]{s(Blocks.BLACKSTONE), s(Blocks.DEEPSLATE), s(Blocks.TUFF), s(Blocks.SMOOTH_BASALT)}
                    : brightness > 0.35
                    ? new BlockState[]{s(Blocks.CALCITE), s(Blocks.DIORITE), s(Blocks.DRIPSTONE_BLOCK)}
                    : new BlockState[]{s(Blocks.ANDESITE), s(Blocks.GRANITE), s(Blocks.TUFF)};
            BlockState rock = rng.pick(rocks);
            BlockState deep = brightness < -0.35 ? s(Blocks.BASALT) : s(ModBlocks.ALIEN_DEEP_STONE);
            pal = pal.withStone(rock, deep);
        }

        // ---- cliffs & strata
        boolean rocky = switch (type) {
            case BARREN_ROCK, CANYON, CRATERED, DESERT, ASH, LAVA, CRYSTAL -> true;
            default -> false;
        };
        b.cliffs(rocky ? rng.range(1.05, 1.45) : rng.range(1.5, 2.3));
        if (type == PlanetType.CANYON) {
            b.strata(canyonBands());
        } else if ((rocky || mountains > 0.6) && rng.chance(0.4)) {
            BlockState[] pool = brightness < -0.2
                    ? new BlockState[]{s(Blocks.BLACKSTONE), s(Blocks.BASALT), s(Blocks.TUFF), s(ModBlocks.SCORCHED_ROCK), pal.stone()}
                    : brightness > 0.3
                    ? new BlockState[]{s(Blocks.CALCITE), s(Blocks.DIORITE), s(Blocks.SANDSTONE), s(Blocks.DRIPSTONE_BLOCK), pal.stone()}
                    : new BlockState[]{s(Blocks.ANDESITE), s(Blocks.TUFF), s(Blocks.GRANITE), s(Blocks.TERRACOTTA), pal.stone()};
            int n = rng.nextInt(3, 6);
            BlockState[] bands = new BlockState[n * 2];
            for (int i = 0; i < bands.length; i++) {
                bands[i] = i % 2 == 0 ? pal.stone() : rng.pick(pool);
            }
            b.strata(bands);
        }

        // ---- snow line from a lapse rate of roughly 0.35 degrees per block
        if (climate != CLIMATE_SCORCHING && climate != CLIMATE_HOT && type != PlanetType.ICE) {
            int line = (int) Math.round(b.baseHeight() + (temp + 3.0) / 0.35);
            if (type == PlanetType.TUNDRA) {
                line = Math.min(line, b.snowLine());
            }
            if (line < 300) {
                b.snowLine(Math.max(b.baseHeight() - 16, line));
                if (pal.snow() == null) {
                    pal = pal.withSnow(climate == CLIMATE_FROZEN && rng.chance(0.4) ? s(ModBlocks.NITROGEN_ICE) : s(Blocks.SNOW_BLOCK));
                }
            }
        }
        b.palette(pal);

        // ---- lakes
        BlockState water = s(Blocks.WATER);
        BlockState lava = s(Blocks.LAVA);
        PlanetProfile.Lakes lakes = null;
        String lakeText = null;
        double chance = rng.range(0.22, 0.62);
        switch (climate) {
            case CLIMATE_SCORCHING -> {
                if (rng.chance(0.85)) {
                    BlockState shore = rng.pick(new BlockState[]{s(Blocks.BASALT), s(Blocks.BLACKSTONE), s(Blocks.MAGMA_BLOCK)});
                    lakes = new PlanetProfile.Lakes(lava, s(Blocks.BASALT), shore, s(Blocks.MAGMA_BLOCK), chance,
                            null, null, null, 0.0);
                    lakeText = "Lava pools glow in its basins.";
                }
            }
            case CLIMATE_HOT -> {
                if (temp > 160 && rng.chance(0.45)) {
                    lakes = new PlanetProfile.Lakes(lava, s(Blocks.BASALT), s(Blocks.BLACKSTONE), s(Blocks.MAGMA_BLOCK), chance * 0.7,
                            null, null, null, 0.0);
                    lakeText = "Lava pools glow in its basins.";
                } else if (rng.chance(0.7)) {
                    lakes = PlanetProfile.Lakes.of(null, null, pal.shore(), chance).dry(s(Blocks.CALCITE));
                    lakeText = "Blinding salt flats mark where lakes once were.";
                }
            }
            case CLIMATE_TEMPERATE -> {
                if (rng.chance(0.85) || type == PlanetType.GRASSLAND || type == PlanetType.JUNGLE) {
                    BlockState shore = switch (type) {
                        case TOXIC, FUNGAL -> s(Blocks.MUD);
                        case CANYON -> s(Blocks.RED_SAND);
                        default -> rng.pick(new BlockState[]{s(ModBlocks.ALIEN_SAND), s(ModBlocks.ALIEN_SAND), s(Blocks.GRAVEL)});
                    };
                    BlockState bed = rng.pick(new BlockState[]{s(Blocks.CLAY), s(Blocks.GRAVEL), s(Blocks.MUD), shore});
                    lakes = new PlanetProfile.Lakes(water, null, shore, bed, chance, null, null, null, 0.0);
                    lakeText = "Its valleys are strung with lakes.";
                    if (rng.chance(0.18)) {
                        lakes = lakes.mixed(lava, s(Blocks.BASALT), s(Blocks.BLACKSTONE), rng.range(0.15, 0.35));
                        lakeText = "Water and lava lie side by side in its basins.";
                    }
                }
            }
            case CLIMATE_COLD -> {
                if (rng.chance(0.85)) {
                    lakes = new PlanetProfile.Lakes(water, s(Blocks.ICE), s(Blocks.GRAVEL), s(Blocks.GRAVEL), chance,
                            null, null, null, 0.0);
                    lakeText = "Frozen lakes crack and sing underfoot.";
                    if (rng.chance(0.15)) {
                        lakes = lakes.mixed(lava, s(Blocks.OBSIDIAN), s(Blocks.OBSIDIAN), rng.range(0.15, 0.3));
                        lakeText = "Frozen lakes and lava pools share its valleys.";
                    }
                }
            }
            default -> {
                if (rng.chance(0.8)) {
                    BlockState solid = rng.pick(new BlockState[]{s(Blocks.PACKED_ICE), s(Blocks.BLUE_ICE), s(ModBlocks.NITROGEN_ICE)});
                    lakes = PlanetProfile.Lakes.of(solid, null, s(Blocks.SNOW_BLOCK), chance);
                    lakeText = "Glaciers of frozen seas fill its basins.";
                    if (rng.chance(0.22)) {
                        lakes = lakes.mixed(lava, s(Blocks.OBSIDIAN), s(Blocks.OBSIDIAN), rng.range(0.15, 0.3));
                        lakeText = "Lava wells up through its frozen seas.";
                    }
                }
            }
        }
        if (lakes != null && rng.chance(0.3)) {
            lakes = lakes.scaled(rng.range(1.6, 2.2));
            lakeText = lakeText.replace("lakes", "great lakes").replace("pools", "seas");
        }
        if (type == PlanetType.OCEAN || type == PlanetType.DUNE_SEA && climate != CLIMATE_SCORCHING) {
            lakes = lakes == null ? null : lakes.withChance(lakes.chance() * 0.4);
        }
        b.lakes(lakes);
        b.describeLakes(lakes == null ? null : lakeText);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
