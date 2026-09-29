package com.galaxymc.galaxy;

import com.galaxymc.galaxy.PlanetProfile.OreSpec;
import com.galaxymc.galaxy.PlanetProfile.Palette;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.util.Hash;
import com.galaxymc.world.ModDimensions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The home system. Each body is its own dimension with hand-tuned terrain, climate and resources, laid
 * out so the natural route outward is also a difficulty curve: Moon and Mars teach cold, Venus and
 * Mercury teach heat, the Jovian moons need both, and the Sun is the final exam.
 */
public final class SolarSystem {
    private SolarSystem() {}

    /** A visitable Sol body. {@code wellCost} is extra fuel for descending into a deep gravity well. */
    public record Body(String id, String name, ResourceKey<Level> dimension, double orbitAu, double wellCost,
                       String parent, String blurb) {}

    public static final String EARTH = "earth";
    public static final Map<String, Body> BODIES = new LinkedHashMap<>();
    private static final Map<String, PlanetProfile> PROFILES = new LinkedHashMap<>();

    static {
        body(new Body(EARTH, "Earth", Level.OVERWORLD, 1.0, 0, null, "Home. Mild, blue and utterly ordinary."));
        body(new Body("moon", "The Moon", ModDimensions.MOON, 1.0, 0, EARTH, "Airless grey craters under a black sky. Titanium lies beneath the dust."));
        body(new Body("mars", "Mars", ModDimensions.MARS, 1.52, 8, null, "A frozen red desert of canyons and dust. Cobalt veins run through its rock."));
        body(new Body("venus", "Venus", ModDimensions.VENUS, 0.72, 16, null, "Crushing heat and sulfur haze over rivers of lava."));
        body(new Body("mercury", "Mercury", ModDimensions.MERCURY, 0.39, 24, null, "Scorching days, killing nights. Iridium hides in the craters."));
        body(new Body("sun", "The Sun", ModDimensions.SUN, 0.02, 420, null, "Slag islands floating on an ocean of plasma. Helionite forms nowhere else."));
        body(new Body("jupiter", "Jupiter", ModDimensions.JUPITER, 5.2, 60, null, "Endless cloud-islands above a bottomless storm. Heavy gravity."));
        body(new Body("europa", "Europa", ModDimensions.EUROPA, 5.2, 36, "jupiter", "An ice shell over a hidden ocean. Cryonite glitters in the crust."));
        body(new Body("io", "Io", ModDimensions.IO, 5.2, 36, "jupiter", "Sulfur plains and lava lakes. Pyrocite burns in its rock."));
        body(new Body("titan", "Titan", ModDimensions.TITAN, 9.5, 40, "saturn", "Methane seas under an orange sky. Clathrate ore fuels starships."));
        body(new Body("saturn", "Saturn's Rings", ModDimensions.SATURN, 9.5, 20, null, "A field of drifting ice and rock asteroids."));
        body(new Body("pluto", "Pluto", ModDimensions.PLUTO, 39.5, 12, null, "The frozen edge of Sol. Plutonite glows in the dark."));
    }

    private static void body(Body b) {
        BODIES.put(b.id(), b);
    }

    public static Body body(String id) {
        return BODIES.get(id);
    }

    public static Body byDimension(ResourceKey<Level> dim) {
        for (Body b : BODIES.values()) {
            if (b.dimension().equals(dim)) {
                return b;
            }
        }
        return null;
    }

    /** Planet profile for a Sol body, or null for Earth (vanilla terrain, no climate system). */
    public static synchronized PlanetProfile profile(String id) {
        if (PROFILES.isEmpty()) {
            buildProfiles();
        }
        return PROFILES.get(id);
    }

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    private static OreSpec ore(Block b, double attempts, int size, int minY, int maxY) {
        return new OreSpec(s(b), null, attempts, size, minY, maxY);
    }

    private static long seed(String id) {
        return Hash.stringSeed("galaxy_mc/sol/" + id);
    }

    private static void buildProfiles() {
        BlockState exotic = s(ModBlocks.EXOTIC_ORE);
        BlockState deepExotic = s(ModBlocks.DEEP_EXOTIC_ORE);

        PROFILES.put("moon", PlanetProfile.builder("moon", "The Moon", PlanetType.CRATERED, seed("moon"))
                .temp(-35, 45).gravity(0.165).terrain(78, 20, 1.0).atmosphere(false).orbit(1.0).tier(1).danger(2)
                .palette(new Palette(s(ModBlocks.MOON_REGOLITH), s(ModBlocks.MOON_REGOLITH), s(ModBlocks.MOON_ROCK),
                        s(ModBlocks.MOON_DEEP_ROCK), null, null, s(ModBlocks.MOON_REGOLITH), null, 4).withAlt(s(ModBlocks.MOON_ROCK)))
                .ore(ore(ModBlocks.LUNAR_TITANIUM_ORE, 9, 8, -60, 110))
                .ore(ore(ModBlocks.MOON_CHEESE_BLOCK, 0.04, 6, 0, 60))
                .ore(new OreSpec(exotic, deepExotic, 2, 5, -60, 90))
                .caves(1.1, 2, false).biome(0).colors(0x9a9a9a, 0x9a9a9a, 0xb0c8ff, 0xffffff)
                .description("Airless grey craters under a black sky.").build());

        PROFILES.put("mars", PlanetProfile.builder("mars", "Mars", PlanetType.CANYON, seed("mars"))
                .temp(-55, 30).gravity(0.38).terrain(92, 46, 1.0).snowLine(168).atmosphere(true).orbit(1.52).tier(1).danger(3)
                .palette(new Palette(s(ModBlocks.MARS_SAND), s(ModBlocks.MARS_SAND), s(ModBlocks.MARS_ROCK),
                        s(ModBlocks.MARS_DEEP_ROCK), null, null, s(ModBlocks.MARS_SAND), s(ModBlocks.MARS_POLAR_ICE), 3).withAlt(s(ModBlocks.MARS_ROCK)))
                .ore(ore(ModBlocks.MARS_COBALT_ORE, 8, 8, -60, 120))
                .ore(new OreSpec(exotic, deepExotic, 2, 5, -60, 90))
                .caves(1.0, 3, true).biome(1).colors(0xb5552c, 0x8a3a1a, 0xff8060, 0xffffff)
                .description("A frozen red desert of canyons and dust.").build());

        PROFILES.put("venus", PlanetProfile.builder("venus", "Venus", PlanetType.ASH, seed("venus"))
                .temp(440, 6).gravity(0.9).terrain(58, 34, 1.0).sea(50).atmosphere(true).orbit(0.72).tier(2).danger(5)
                .palette(new Palette(s(ModBlocks.VENUS_REGOLITH), s(ModBlocks.VENUS_REGOLITH), s(ModBlocks.VENUS_BASALT),
                        s(Blocks.BLACKSTONE), s(Blocks.LAVA), null, s(ModBlocks.VENUS_BASALT), null, 2).withAlt(s(ModBlocks.VENUS_BASALT)))
                .ore(ore(ModBlocks.VENUS_SULFUR_ORE, 12, 9, -60, 140))
                .ore(ore(ModBlocks.SULFUR_BLOCK, 1.5, 12, 30, 120))
                .ore(new OreSpec(exotic, deepExotic, 2.5, 5, -60, 90))
                .caves(0.8, 2, false).biome(2).colors(0xc8a050, 0x9a7a3a, 0xffd060, 0xffffff)
                .description("Crushing heat and sulfur haze over rivers of lava.").build());

        PROFILES.put("mercury", PlanetProfile.builder("mercury", "Mercury", PlanetType.CRATERED, seed("mercury"))
                .temp(80, 250).gravity(0.38).terrain(74, 26, 1.2).atmosphere(false).orbit(0.39).tier(2).danger(5)
                .palette(new Palette(s(ModBlocks.MERCURY_DUST), s(ModBlocks.MERCURY_DUST), s(ModBlocks.MERCURY_ROCK),
                        s(Blocks.SMOOTH_BASALT), null, null, s(ModBlocks.MERCURY_DUST), null, 3).withAlt(s(ModBlocks.MERCURY_ROCK)))
                .ore(ore(ModBlocks.MERCURY_IRIDIUM_ORE, 6, 6, -60, 100))
                .ore(new OreSpec(exotic, deepExotic, 2.5, 5, -60, 90))
                .caves(1.0, 2, false).biome(3).colors(0x6e6760, 0x6e6760, 0xffc080, 0xffffff)
                .description("Scorching days, killing nights.").build());

        PROFILES.put("sun", PlanetProfile.builder("sun", "The Sun", PlanetType.STELLAR, seed("sun"))
                .temp(1800, 0).gravity(2.4).terrain(58, 30, 1.0).sea(64).atmosphere(true).orbit(0.0).tier(4).danger(9)
                .palette(new Palette(s(ModBlocks.SOLAR_SLAG), s(ModBlocks.SOLAR_SLAG), s(ModBlocks.SOLAR_SLAG),
                        s(Blocks.MAGMA_BLOCK), s(Blocks.LAVA), s(ModBlocks.SOLAR_PLASMA), s(ModBlocks.SOLAR_PLASMA), null, 3))
                .ore(ore(ModBlocks.HELIONITE_ORE, 3, 5, 0, 140))
                .ore(new OreSpec(exotic, deepExotic, 3, 6, -60, 120))
                .caves(0.4, 1, false).biome(4).colors(0xff8020, 0xff5010, 0xffe060, 0xffffff)
                .description("Slag islands on an ocean of plasma.").build());

        PROFILES.put("jupiter", PlanetProfile.builder("jupiter", "Jupiter", PlanetType.GAS_GIANT, seed("jupiter"))
                .temp(-110, 0).gravity(2.3).terrain(110, 40, 1.0).atmosphere(true).orbit(5.2).tier(3).danger(6)
                .palette(new Palette(s(ModBlocks.JOVIAN_CLOUD), s(ModBlocks.JOVIAN_CLOUD), s(ModBlocks.JOVIAN_STONE),
                        s(ModBlocks.JOVIAN_STONE), null, null, s(ModBlocks.JOVIAN_CLOUD), null, 3))
                .ore(ore(ModBlocks.STORM_CRYSTAL_ORE, 5, 6, 40, 200))
                .ore(new OreSpec(exotic, deepExotic, 3, 5, 40, 200))
                .caves(0.0, 0, false).biome(5).colors(0xd8b890, 0xb08860, 0xfff0c0, 0xffffff)
                .description("Cloud-islands above a bottomless storm.").build());

        PROFILES.put("europa", PlanetProfile.builder("europa", "Europa", PlanetType.ICE, seed("europa"))
                .temp(-160, 10).gravity(0.13).terrain(96, 14, 0.8).sea(70).atmosphere(false).subsurfaceOcean(true).orbit(5.2)
                .tier(2).danger(4)
                .palette(new Palette(s(ModBlocks.EUROPA_FROST), s(ModBlocks.EUROPA_ICE), s(ModBlocks.EUROPA_ICE),
                        s(Blocks.DEEPSLATE), s(Blocks.WATER), s(ModBlocks.EUROPA_ICE), s(ModBlocks.EUROPA_ICE), null, 2).withAlt(s(ModBlocks.EUROPA_ICE)))
                .ore(ore(ModBlocks.CRYONITE_ORE, 8, 7, 60, 140))
                .ore(new OreSpec(exotic, deepExotic, 2.5, 5, -60, 40))
                .caves(0.6, 2, false).biome(6).colors(0xd0e8ff, 0xa0c8e8, 0x80e0ff, 0xffffff)
                .description("An ice shell over a hidden ocean.").build());

        PROFILES.put("io", PlanetProfile.builder("io", "Io", PlanetType.LAVA, seed("io"))
                .temp(20, 25).gravity(0.18).terrain(60, 30, 1.1).sea(54).atmosphere(false).orbit(5.2).tier(2).danger(5)
                .palette(new Palette(s(ModBlocks.IO_SULFUR_ROCK), s(ModBlocks.IO_SULFUR_ROCK), s(ModBlocks.IO_SULFUR_ROCK),
                        s(Blocks.BASALT), s(Blocks.LAVA), null, s(ModBlocks.IO_ASH), null, 2).withAlt(s(ModBlocks.IO_ASH)))
                .ore(ore(ModBlocks.PYROCITE_ORE, 8, 7, -60, 130))
                .ore(ore(ModBlocks.VENUS_SULFUR_ORE, 6, 8, -60, 130))
                .ore(new OreSpec(exotic, deepExotic, 2.5, 5, -60, 90))
                .caves(0.9, 2, false).biome(7).colors(0xe0d040, 0xb0a020, 0xff6020, 0xffffff)
                .description("Sulfur plains and lava lakes.").build());

        PROFILES.put("titan", PlanetProfile.builder("titan", "Titan", PlanetType.TUNDRA, seed("titan"))
                .temp(-180, 0).gravity(0.14).terrain(59, 18, 0.9).sea(62).atmosphere(true).orbit(9.5).tier(2).danger(4)
                .palette(new Palette(s(ModBlocks.TITAN_SEDIMENT), s(ModBlocks.TITAN_SEDIMENT), s(ModBlocks.TITAN_ROCK),
                        s(Blocks.DEEPSLATE), s(Blocks.WATER), null, s(ModBlocks.TITAN_SEDIMENT), null, 4).withAlt(s(ModBlocks.TITAN_ROCK)))
                .ore(ore(ModBlocks.METHANE_CLATHRATE_ORE, 10, 8, -60, 120))
                .ore(new OreSpec(exotic, deepExotic, 2.5, 5, -60, 90))
                .caves(1.0, 2, true).biome(8).colors(0x8a5a2e, 0x6a4020, 0xffa040, 0xffffff)
                .description("Methane seas under an orange sky.").build());

        PROFILES.put("saturn", PlanetProfile.builder("saturn", "Saturn's Rings", PlanetType.SHATTERED, seed("saturn"))
                .temp(-180, 0).gravity(0.2).terrain(100, 60, 1.0).atmosphere(false).orbit(9.5).tier(3).danger(5)
                .palette(new Palette(s(ModBlocks.RING_ICE), s(ModBlocks.RING_ICE), s(ModBlocks.RING_ROCK),
                        s(ModBlocks.RING_ROCK), null, null, s(ModBlocks.RING_ICE), null, 3))
                .ore(ore(ModBlocks.STORM_CRYSTAL_ORE, 3, 5, -60, 250))
                .ore(ore(ModBlocks.LUNAR_TITANIUM_ORE, 3, 5, -60, 250))
                .ore(new OreSpec(exotic, deepExotic, 4, 6, -60, 250))
                .caves(0.0, 0, false).biome(9).colors(0xe8e0d0, 0xc8c0b0, 0xfff8e0, 0xffffff)
                .description("A field of drifting ice and rock asteroids.").build());

        PROFILES.put("pluto", PlanetProfile.builder("pluto", "Pluto", PlanetType.ICE, seed("pluto"))
                .temp(-230, 4).gravity(0.06).terrain(80, 30, 0.9).snowLine(118).atmosphere(false).orbit(39.5).tier(3).danger(6)
                .palette(new Palette(s(ModBlocks.NITROGEN_ICE), s(ModBlocks.NITROGEN_ICE), s(ModBlocks.PLUTO_ROCK),
                        s(ModBlocks.PLUTO_ROCK), null, null, s(ModBlocks.THOLIN_DUST), s(ModBlocks.PLUTO_ROCK), 3)
                        .withAlt(s(ModBlocks.THOLIN_DUST)))
                .ore(ore(ModBlocks.PLUTONITE_ORE, 3, 5, -60, 90))
                .ore(new OreSpec(exotic, deepExotic, 3, 6, -60, 90))
                .caves(1.0, 2, false).biome(10).colors(0xe0d8d0, 0x9a6050, 0xa0ffa0, 0xffffff)
                .description("The frozen edge of Sol.").build());
    }

    public static List<String> ids() {
        return List.copyOf(BODIES.keySet());
    }
}
