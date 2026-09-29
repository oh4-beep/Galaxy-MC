package com.galaxymc.registry;

import com.galaxymc.block.AlienPlantBlock;
import com.galaxymc.block.CloudBlock;
import com.galaxymc.block.ExoticOreBlock;
import com.galaxymc.block.LifeSupportBlock;
import com.galaxymc.block.NavigationConsoleBlock;
import com.galaxymc.block.PlasmaBlock;
import com.galaxymc.block.StellarForgeBlock;
import com.galaxymc.block.ThermalArmorTableBlock;
import com.galaxymc.block.ThrusterBlock;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.util.valueproviders.UniformInt;

/**
 * Every block in Galaxy MC.
 *
 * <p>Sol bodies each get a small hand-picked palette (regolith, bedrock-like rock, a signature ore);
 * the procedural frontier worlds share one "alien" palette whose colours are re-tinted per planet on
 * the client, which is how millions of worlds can look different with a finite block list.
 */
public final class ModBlocks {
    private ModBlocks() {}

    // ------------------------------------------------------------------ Moon
    public static final Block MOON_REGOLITH = falling("moon_regolith", 0xFF9A9A9A, MapColor.COLOR_LIGHT_GRAY, 0.6F);
    public static final Block MOON_ROCK = stone("moon_rock", MapColor.COLOR_GRAY, 1.8F);
    public static final Block MOON_DEEP_ROCK = stone("moon_deep_rock", MapColor.DEEPSLATE, 3.0F);
    public static final Block POLISHED_MOON_ROCK = stone("polished_moon_rock", MapColor.COLOR_GRAY, 1.8F);
    public static final Block MOON_ROCK_BRICKS = stone("moon_rock_bricks", MapColor.COLOR_GRAY, 2.0F);
    public static final Block LUNAR_TITANIUM_ORE = ore("lunar_titanium_ore", MapColor.COLOR_GRAY, 3.5F, 0, 0);
    public static final Block MOON_CHEESE_BLOCK = Reg.block("moon_cheese_block",
            Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6F).sound(SoundType.WART_BLOCK));

    // ------------------------------------------------------------------ Mars
    public static final Block MARS_SAND = falling("mars_sand", 0xFFB5552C, MapColor.COLOR_ORANGE, 0.6F);
    public static final Block MARS_ROCK = stone("mars_rock", MapColor.TERRACOTTA_ORANGE, 1.8F);
    public static final Block MARS_DEEP_ROCK = stone("mars_deep_rock", MapColor.TERRACOTTA_RED, 3.0F);
    public static final Block MARS_ROCK_BRICKS = stone("mars_rock_bricks", MapColor.TERRACOTTA_ORANGE, 2.0F);
    public static final Block MARS_COBALT_ORE = ore("mars_cobalt_ore", MapColor.TERRACOTTA_ORANGE, 3.5F, 0, 0);
    public static final Block MARS_POLAR_ICE = Reg.block("mars_polar_ice",
            Properties.of().mapColor(MapColor.ICE).strength(0.8F).friction(0.98F).sound(SoundType.GLASS));

    // ------------------------------------------------------------------ Venus
    public static final Block VENUS_BASALT = stone("venus_basalt", MapColor.COLOR_BROWN, 2.2F);
    public static final Block VENUS_REGOLITH = falling("venus_regolith", 0xFFB39A5E, MapColor.COLOR_YELLOW, 0.6F);
    public static final Block VENUS_SULFUR_ORE = ore("venus_sulfur_ore", MapColor.COLOR_YELLOW, 3.0F, 1, 3);
    public static final Block SULFUR_BLOCK = Reg.block("sulfur_block",
            Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5F).sound(SoundType.CALCITE).requiresCorrectToolForDrops());

    // ------------------------------------------------------------------ Mercury
    public static final Block MERCURY_ROCK = stone("mercury_rock", MapColor.TERRACOTTA_GRAY, 2.2F);
    public static final Block MERCURY_DUST = falling("mercury_dust", 0xFF6E6760, MapColor.TERRACOTTA_GRAY, 0.6F);
    public static final Block MERCURY_IRIDIUM_ORE = ore("mercury_iridium_ore", MapColor.TERRACOTTA_GRAY, 4.5F, 0, 0);

    // ------------------------------------------------------------------ The Sun
    public static final Block SOLAR_SLAG = Reg.block("solar_slag",
            Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5F, 12.0F).sound(SoundType.BASALT).requiresCorrectToolForDrops()
                    .lightLevel(s -> 3));
    public static final Block SOLAR_PLASMA = Reg.block("solar_plasma", PlasmaBlock::new,
            Properties.of().mapColor(MapColor.FIRE).strength(1.0F).sound(SoundType.SHROOMLIGHT).lightLevel(s -> 15)
                    .emissiveRendering(s -> true).isValidSpawn((s, l, p, t) -> false));
    public static final Block HELIONITE_ORE = Reg.block("helionite_ore", p -> new DropExperienceBlock(UniformInt.of(5, 9), p),
            Properties.of().mapColor(MapColor.GOLD).strength(6.0F, 20.0F).sound(SoundType.BASALT).requiresCorrectToolForDrops()
                    .lightLevel(s -> 11));

    // ------------------------------------------------------------------ Jupiter
    public static final Block JOVIAN_CLOUD = Reg.block("jovian_cloud", CloudBlock::new,
            Properties.of().mapColor(MapColor.WOOL).strength(0.3F).sound(SoundType.WOOL).noOcclusion());
    public static final Block JOVIAN_STONE = stone("jovian_stone", MapColor.TERRACOTTA_WHITE, 1.8F);
    public static final Block STORM_CRYSTAL_ORE = ore("storm_crystal_ore", MapColor.TERRACOTTA_WHITE, 3.5F, 3, 6);

    // ------------------------------------------------------------------ Europa
    public static final Block EUROPA_ICE = Reg.block("europa_ice",
            Properties.of().mapColor(MapColor.ICE).strength(1.0F).friction(0.98F).sound(SoundType.GLASS));
    public static final Block EUROPA_FROST = Reg.block("europa_frost",
            Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW));
    public static final Block CRYONITE_ORE = Reg.block("cryonite_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p),
            Properties.of().mapColor(MapColor.ICE).strength(3.0F).friction(0.98F).sound(SoundType.GLASS).requiresCorrectToolForDrops()
                    .lightLevel(s -> 4));

    // ------------------------------------------------------------------ Io
    public static final Block IO_SULFUR_ROCK = stone("io_sulfur_rock", MapColor.COLOR_YELLOW, 1.8F);
    public static final Block IO_ASH = falling("io_ash", 0xFF4A4238, MapColor.COLOR_BLACK, 0.5F);
    public static final Block PYROCITE_ORE = Reg.block("pyrocite_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p),
            Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F).sound(SoundType.NETHER_ORE).requiresCorrectToolForDrops()
                    .lightLevel(s -> 6));

    // ------------------------------------------------------------------ Titan
    public static final Block TITAN_SEDIMENT = falling("titan_sediment", 0xFF8A5A2E, MapColor.TERRACOTTA_BROWN, 0.6F);
    public static final Block TITAN_ROCK = stone("titan_rock", MapColor.TERRACOTTA_BROWN, 1.8F);
    public static final Block METHANE_CLATHRATE_ORE = ore("methane_clathrate_ore", MapColor.TERRACOTTA_BROWN, 2.5F, 1, 3);

    // ------------------------------------------------------------------ Saturn's rings
    public static final Block RING_ICE = Reg.block("ring_ice",
            Properties.of().mapColor(MapColor.ICE).strength(1.0F).friction(0.98F).sound(SoundType.GLASS));
    public static final Block RING_ROCK = stone("ring_rock", MapColor.COLOR_LIGHT_GRAY, 2.0F);

    // ------------------------------------------------------------------ Pluto
    public static final Block NITROGEN_ICE = Reg.block("nitrogen_ice",
            Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.9F).friction(0.98F).sound(SoundType.GLASS));
    public static final Block PLUTO_ROCK = stone("pluto_rock", MapColor.TERRACOTTA_LIGHT_GRAY, 2.0F);
    public static final Block THOLIN_DUST = falling("tholin_dust", 0xFF7A3A2A, MapColor.TERRACOTTA_RED, 0.5F);
    public static final Block PLUTONITE_ORE = Reg.block("plutonite_ore", p -> new DropExperienceBlock(UniformInt.of(4, 8), p),
            Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(5.0F, 12.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()
                    .lightLevel(s -> 9));

    // ------------------------------------------------------------------ Frontier (tinted per planet)
    public static final Block ALIEN_GRASS = Reg.block("alien_grass",
            Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.NYLIUM));
    public static final Block ALIEN_SOIL = Reg.block("alien_soil",
            Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.ROOTED_DIRT));
    public static final Block ALIEN_STONE = stone("alien_stone", MapColor.STONE, 1.5F);
    public static final Block ALIEN_DEEP_STONE = stone("alien_deep_stone", MapColor.DEEPSLATE, 3.0F);
    public static final Block ALIEN_STONE_BRICKS = stone("alien_stone_bricks", MapColor.STONE, 1.8F);
    public static final Block ALIEN_SAND = falling("alien_sand", 0xFFD8C08A, MapColor.SAND, 0.5F);
    public static final Block ALIEN_REGOLITH = falling("alien_regolith", 0xFF8C8C8C, MapColor.COLOR_LIGHT_GRAY, 0.6F);
    public static final Block FUNGAL_TURF = Reg.block("fungal_turf",
            Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.FUNGUS));
    public static final Block TOXIC_TURF = Reg.block("toxic_turf",
            Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.6F).sound(SoundType.SLIME_BLOCK));
    public static final Block ASH_BLOCK = falling("ash_block", 0xFF55504B, MapColor.COLOR_GRAY, 0.5F);
    public static final Block SCORCHED_ROCK = stone("scorched_rock", MapColor.COLOR_BLACK, 2.0F);
    public static final Block CRYSTAL_BLOCK = Reg.block("crystal_block",
            Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops());
    public static final Block GLOWING_CRYSTAL_BLOCK = Reg.block("glowing_crystal_block",
            Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F).sound(SoundType.AMETHYST).lightLevel(s -> 12));
    public static final Block XENO_LOG = Reg.block("xeno_log", RotatedPillarBlock::new,
            Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.STEM).ignitedByLava());
    public static final Block XENO_PLANKS = Reg.block("xeno_planks",
            Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD).ignitedByLava());
    public static final Block XENO_LEAVES = Reg.block("xeno_leaves", p -> new UntintedParticleLeavesBlock(0.02F, ParticleTypes.GLOW, p),
            Blocks.leavesProperties(SoundType.AZALEA_LEAVES).lightLevel(s -> 4));
    public static final Block XENO_GRASS = plant("xeno_grass", 10, 0.0F, 0, SoundType.GRASS);
    public static final Block GLOW_SHROOM = plant("glow_shroom", 9, 0.0F, 11, SoundType.FUNGUS);
    public static final Block SPINE_PLANT = plant("spine_plant", 14, 1.5F, 0, SoundType.CACTUS_FLOWER);
    public static final Block FROST_BLOOM = plant("frost_bloom", 11, 0.0F, 6, SoundType.GRASS);
    public static final Block EMBER_BLOOM = plant("ember_bloom", 11, 0.0F, 9, SoundType.GRASS);
    public static final Block CRYSTAL_SHARD = Reg.block("crystal_shard", p -> new AmethystClusterBlock(7.0F, 10.0F, p),
            Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
                    .strength(1.2F).lightLevel(s -> 7).pushReaction(PushReaction.DESTROY));

    public static final Block ALIEN_COAL_ORE = alienOre("alien_coal_ore", 3.0F, 0, 2);
    public static final Block ALIEN_IRON_ORE = alienOre("alien_iron_ore", 3.0F, 0, 0);
    public static final Block ALIEN_COPPER_ORE = alienOre("alien_copper_ore", 3.0F, 0, 0);
    public static final Block ALIEN_GOLD_ORE = alienOre("alien_gold_ore", 3.0F, 0, 0);
    public static final Block ALIEN_REDSTONE_ORE = alienOre("alien_redstone_ore", 3.0F, 1, 5);
    public static final Block ALIEN_LAPIS_ORE = alienOre("alien_lapis_ore", 3.0F, 2, 5);
    public static final Block ALIEN_DIAMOND_ORE = alienOre("alien_diamond_ore", 3.0F, 3, 7);
    public static final Block ALIEN_EMERALD_ORE = alienOre("alien_emerald_ore", 3.0F, 3, 7);

    /** Procedural mineral ore; the planet decides what it actually is. */
    public static final Block EXOTIC_ORE = Reg.block("exotic_ore", ExoticOreBlock::new,
            Properties.of().mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().lightLevel(s -> 5));
    public static final Block DEEP_EXOTIC_ORE = Reg.block("deep_exotic_ore", ExoticOreBlock::new,
            Properties.of().mapColor(MapColor.DEEPSLATE).strength(5.0F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().lightLevel(s -> 7));

    // ------------------------------------------------------------------ Ships & machines
    public static final Block HULL_PLATING = metal("hull_plating", MapColor.SNOW);
    public static final Block HULL_PLATING_DARK = metal("hull_plating_dark", MapColor.COLOR_GRAY);
    public static final Block HULL_STRIPE = metal("hull_stripe", MapColor.COLOR_ORANGE);
    public static final Block SHIP_FLOOR = metal("ship_floor", MapColor.METAL);
    public static final Block SHIP_LIGHT = Reg.block("ship_light",
            Properties.of().mapColor(MapColor.SNOW).strength(1.5F).sound(SoundType.GLASS).lightLevel(s -> 15));
    public static final Block REINFORCED_GLASS = Reg.block("reinforced_glass", TransparentBlock::new,
            Properties.of().instrument(NoteBlockInstrument.HAT).strength(2.5F, 12.0F).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final Block THRUSTER = Reg.block("thruster", ThrusterBlock::new,
            Properties.of().mapColor(MapColor.COLOR_GRAY).strength(4.0F, 12.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());
    public static final Block AIRLOCK_DOOR = Reg.block("airlock_door", p -> new DoorBlock(BlockSetType.COPPER, p),
            Properties.of().mapColor(MapColor.METAL).strength(3.0F, 12.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block NAVIGATION_CONSOLE = Reg.block("navigation_console", NavigationConsoleBlock::new,
            Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3.5F, 1200.0F).sound(SoundType.METAL).lightLevel(s -> 9).noOcclusion());
    public static final Block LIFE_SUPPORT = Reg.block("life_support", LifeSupportBlock::new,
            Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.0F, 12.0F).sound(SoundType.METAL).lightLevel(s -> 10));
    public static final Block THERMAL_ARMOR_TABLE = Reg.block("thermal_armor_table", ThermalArmorTableBlock::new,
            Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
    public static final Block STELLAR_FORGE = Reg.block("stellar_forge", StellarForgeBlock::new,
            Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).sound(SoundType.ANVIL).lightLevel(s -> 12)
                    .requiresCorrectToolForDrops());
    public static final Block FUEL_BLOCK = Reg.block("fuel_block",
            Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final Block TITANIUM_BLOCK = metal("titanium_block", MapColor.COLOR_LIGHT_GRAY);
    public static final Block COBALT_BLOCK = metal("cobalt_block", MapColor.COLOR_BLUE);
    public static final Block IRIDIUM_BLOCK = metal("iridium_block", MapColor.TERRACOTTA_WHITE);

    // ------------------------------------------------------------------ helpers
    private static Block stone(String name, MapColor color, float hardness) {
        return Reg.block(name, Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(hardness, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());
    }

    private static Block falling(String name, int dust, MapColor color, float hardness) {
        return Reg.block(name, p -> new ColoredFallingBlock(new ColorRGBA(dust), p),
                Properties.of().mapColor(color).instrument(NoteBlockInstrument.SNARE).strength(hardness).sound(SoundType.SAND));
    }

    private static Block ore(String name, MapColor color, float hardness, int xpMin, int xpMax) {
        return Reg.block(name, p -> new DropExperienceBlock(UniformInt.of(xpMin, xpMax), p),
                Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(hardness, 6.0F)
                        .sound(SoundType.STONE).requiresCorrectToolForDrops());
    }

    private static Block alienOre(String name, float hardness, int xpMin, int xpMax) {
        return ore(name, MapColor.STONE, hardness, xpMin, xpMax);
    }

    private static Block metal(String name, MapColor color) {
        return Reg.block(name, Properties.of().mapColor(color).strength(4.0F, 12.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    private static Block plant(String name, int height, float sting, int light, SoundType sound) {
        Function<BlockBehaviour.Properties, Block> factory = p -> new AlienPlantBlock(p, height, sting);
        return Reg.block(name, factory, Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak()
                .sound(sound).offsetType(BlockBehaviour.OffsetType.XZ).lightLevel(s -> light).pushReaction(PushReaction.DESTROY));
    }

    public static void init() {
    }
}
