package com.galaxymc.world;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.util.Hash;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.AquaticFeatures;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Surface features: flora, trees, spires, boulders and the occasional ruin. Runs in the decoration
 * step with a 3x3-chunk writable region, so features may overhang the centre chunk by up to a chunk.
 * Everything is seeded from the planet seed and chunk position, so decoration is reproducible.
 *
 * <p>Terran worlds are dressed with vanilla's own features, picked per local biome: oak and birch
 * woods, dark forests, taiga, cherry groves, jungles, savanna acacias, cacti, swamps with lily pads,
 * kelp and seagrass under the sea, snow on the cold side, plus geodes, dungeons and springs below.
 */
public final class SurfaceDecorator {
    private SurfaceDecorator() {}

    private static final int FLAGS = 2;

    public static void decorate(WorldGenLevel level, ChunkAccess chunk, PlanetProfile p, TerrainShaper shaper, PlanetChunkGenerator gen) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        Hash.Rng rng = new Hash.Rng(Hash.of(p.seed ^ 0x44454352L, chunk.getPos().x(), chunk.getPos().z()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        switch (p.type) {
            case JUNGLE -> {
                trees(level, rng, x0, z0, pos, p, rng.nextInt(2, 5), true);
                flora(level, rng, x0, z0, pos, p, 22, ModBlocks.XENO_GRASS.defaultBlockState(), ModBlocks.GLOW_SHROOM.defaultBlockState());
            }
            case GRASSLAND -> {
                if (rng.chance(0.35)) {
                    trees(level, rng, x0, z0, pos, p, 1, false);
                }
                flora(level, rng, x0, z0, pos, p, 30, ModBlocks.XENO_GRASS.defaultBlockState(), ModBlocks.FROST_BLOOM.defaultBlockState());
            }
            case FUNGAL -> {
                if (rng.chance(0.55)) {
                    mushroom(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, p);
                }
                flora(level, rng, x0, z0, pos, p, 18, ModBlocks.GLOW_SHROOM.defaultBlockState(), ModBlocks.XENO_GRASS.defaultBlockState());
            }
            case TOXIC -> {
                if (rng.chance(0.2)) {
                    mushroom(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, p);
                }
                flora(level, rng, x0, z0, pos, p, 14, ModBlocks.SPINE_PLANT.defaultBlockState(), ModBlocks.GLOW_SHROOM.defaultBlockState());
            }
            case CRYSTAL -> {
                if (rng.chance(0.35)) {
                    spire(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, rng.nextInt(6, 22));
                }
                clusters(level, rng, x0, z0, pos, 8);
            }
            case ICE -> {
                if (rng.chance(0.18)) {
                    iceSpike(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos);
                }
                if (p.isFrontier()) {
                    flora(level, rng, x0, z0, pos, p, 3, ModBlocks.FROST_BLOOM.defaultBlockState(), ModBlocks.FROST_BLOOM.defaultBlockState());
                }
            }
            case TUNDRA -> {
                if (p.isFrontier()) {
                    if (rng.chance(0.2)) {
                        trees(level, rng, x0, z0, pos, p, 1, false);
                    }
                    flora(level, rng, x0, z0, pos, p, 10, ModBlocks.FROST_BLOOM.defaultBlockState(), ModBlocks.XENO_GRASS.defaultBlockState());
                } else {
                    boulders(level, rng, x0, z0, pos, p, 0.25);
                }
            }
            case DESERT, DUNE_SEA -> {
                flora(level, rng, x0, z0, pos, p, 3, ModBlocks.SPINE_PLANT.defaultBlockState(), ModBlocks.EMBER_BLOOM.defaultBlockState());
                if (rng.chance(0.02)) {
                    fossil(level, rng, x0 + 8, z0 + 8, pos);
                }
            }
            case LAVA, ASH -> {
                if (rng.chance(0.3)) {
                    pillar(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, Blocks.BASALT.defaultBlockState(), rng.nextInt(4, 14));
                }
                if (p.isFrontier()) {
                    flora(level, rng, x0, z0, pos, p, 4, ModBlocks.EMBER_BLOOM.defaultBlockState(), ModBlocks.EMBER_BLOOM.defaultBlockState());
                }
            }
            case CRATERED, BARREN_ROCK, CANYON -> boulders(level, rng, x0, z0, pos, p, p.type == PlanetType.CANYON ? 0.3 : 0.45);
            case STELLAR -> {
                if (rng.chance(0.25)) {
                    pillar(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, ModBlocks.SOLAR_PLASMA.defaultBlockState(), rng.nextInt(2, 6));
                }
            }
            case GAS_GIANT, SHATTERED -> clusters(level, rng, x0, z0, pos, 2);
            case OCEAN -> flora(level, rng, x0, z0, pos, p, 6, ModBlocks.XENO_GRASS.defaultBlockState(), ModBlocks.GLOW_SHROOM.defaultBlockState());
            case TERRAN -> terran(level, rng, p, shaper, gen, x0, z0);
            case STORM -> storm(level, rng, p, gen, x0, z0, pos);
            case VOLCANIC -> volcanic(level, rng, x0, z0, pos, p);
        }
        underground(level, rng, p, gen, x0, z0);
        Structures.maybePlace(level, rng, chunk, p, shaper, gen);
    }

    // ------------------------------------------------------------------ vanilla features

    private static boolean warned;

    /** Places one of vanilla's configured features; false if it does not exist or refused the spot. */
    static boolean feature(WorldGenLevel level, PlanetChunkGenerator gen, RandomSource random, ResourceKey<ConfiguredFeature<?, ?>> key,
                           BlockPos pos) {
        Optional<Holder.Reference<ConfiguredFeature<?, ?>>> f = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(key);
        if (f.isEmpty()) {
            return false;
        }
        try {
            return f.get().value().place(level, gen, random, pos.immutable());
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                GalaxyMC.LOG.warn("Feature {} failed at {}", key.identifier(), pos, e);
            }
            return false;
        }
    }

    /** {@code attempts} tries of a feature at random ground positions in the chunk. */
    private static void scatter(WorldGenLevel level, PlanetChunkGenerator gen, RandomSource random, int x0, int z0,
                                ResourceKey<ConfiguredFeature<?, ?>> key, int attempts, Heightmap.Types map) {
        for (int i = 0; i < attempts; i++) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            feature(level, gen, random, key, new BlockPos(x, level.getHeight(map, x, z), z));
        }
    }

    private static void scatter(WorldGenLevel level, PlanetChunkGenerator gen, RandomSource random, int x0, int z0,
                                ResourceKey<ConfiguredFeature<?, ?>> key, int attempts) {
        scatter(level, gen, random, x0, z0, key, attempts, Heightmap.Types.WORLD_SURFACE_WG);
    }

    static TerranBiome terranBiome(PlanetProfile p, TerrainShaper shaper, int x, int z) {
        return shaper.terranBiome(x, z, PlanetColumns.surfaceY(p, shaper, x, z));
    }

    // ------------------------------------------------------------------ terran

    private static void terran(WorldGenLevel level, Hash.Rng rng, PlanetProfile p, TerrainShaper shaper, PlanetChunkGenerator gen,
                               int x0, int z0) {
        RandomSource random = RandomSource.create(rng.nextLong());
        TerranBiome centre = terranBiome(p, shaper, x0 + 8, z0 + 8);

        // Trees first, each sampling the biome under it so forest edges blend instead of stopping at chunk lines.
        int trees = switch (centre) {
            case FOREST, BIRCH_FOREST, TAIGA -> 7 + random.nextInt(3);
            case DARK_FOREST -> 14;
            case OLD_GROWTH_TAIGA -> 10;
            case JUNGLE -> 16;
            case FLOWER_FOREST, CHERRY_GROVE, SNOWY_TAIGA -> 4 + random.nextInt(2);
            case SAVANNA, SWAMP -> 1 + random.nextInt(2);
            case PLAINS, SUNFLOWER_PLAINS -> random.nextInt(10) == 0 ? 1 : 0;
            case MEADOW, SNOWY_PLAINS -> random.nextInt(6) == 0 ? 1 : 0;
            default -> 0;
        };
        for (int i = 0; i < trees; i++) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            ResourceKey<ConfiguredFeature<?, ?>> tree = tree(terranBiome(p, shaper, x, z));
            if (tree != null) {
                feature(level, gen, random, tree, new BlockPos(x, groundY(level, x, z), z));
            }
        }

        switch (centre) {
            case PLAINS -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 24);
                scatter(level, gen, random, x0, z0, VegetationFeatures.TALL_GRASS, 4);
                scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_PLAIN, 5);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BUSH, 1);
                if (random.nextInt(40) == 0) {
                    scatter(level, gen, random, x0, z0, VegetationFeatures.PUMPKIN, 6);
                }
            }
            case SUNFLOWER_PLAINS -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 20);
                scatter(level, gen, random, x0, z0, VegetationFeatures.SUNFLOWER, 10);
                scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_PLAIN, 4);
            }
            case FOREST -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 12);
                scatter(level, gen, random, x0, z0, VegetationFeatures.FOREST_FLOWERS, 3);
                scatter(level, gen, random, x0, z0, VegetationFeatures.LEAF_LITTER, 6);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BROWN_MUSHROOM, random.nextInt(4) == 0 ? 2 : 0);
                scatter(level, gen, random, x0, z0, TreeFeatures.FALLEN_OAK_TREE, random.nextInt(12) == 0 ? 1 : 0);
            }
            case BIRCH_FOREST -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 12);
                scatter(level, gen, random, x0, z0, VegetationFeatures.WILDFLOWER, 6);
                scatter(level, gen, random, x0, z0, VegetationFeatures.FOREST_FLOWERS, 2);
                scatter(level, gen, random, x0, z0, TreeFeatures.FALLEN_BIRCH_TREE, random.nextInt(12) == 0 ? 1 : 0);
            }
            case DARK_FOREST -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 8);
                scatter(level, gen, random, x0, z0, VegetationFeatures.RED_MUSHROOM, 2);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BROWN_MUSHROOM, 2);
                scatter(level, gen, random, x0, z0, VegetationFeatures.FIREFLY_BUSH, 1);
            }
            case FLOWER_FOREST -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_FLOWER_FOREST, 24);
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 8);
            }
            case MEADOW -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_MEADOW, 30);
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 10);
            }
            case CHERRY_GROVE -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_CHERRY, 16);
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 10);
            }
            case TAIGA, OLD_GROWTH_TAIGA -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.TAIGA_GRASS, 14);
                scatter(level, gen, random, x0, z0, VegetationFeatures.LARGE_FERN, 3);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BERRY_BUSH, random.nextInt(6) == 0 ? 3 : 0);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BROWN_MUSHROOM, 1);
                scatter(level, gen, random, x0, z0, TreeFeatures.FALLEN_SPRUCE_TREE, random.nextInt(10) == 0 ? 1 : 0);
                scatter(level, gen, random, x0, z0, MiscOverworldFeatures.FOREST_ROCK, centre == TerranBiome.OLD_GROWTH_TAIGA
                        && random.nextInt(5) == 0 ? 1 : 0);
            }
            case SNOWY_TAIGA -> scatter(level, gen, random, x0, z0, VegetationFeatures.TAIGA_GRASS, 4);
            case SNOWY_PLAINS -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 3);
                scatter(level, gen, random, x0, z0, MiscOverworldFeatures.ICE_SPIKE, random.nextInt(30) == 0 ? 1 : 0);
            }
            case DESERT -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.CACTUS, 3);
                scatter(level, gen, random, x0, z0, VegetationFeatures.DEAD_BUSH, 3);
                scatter(level, gen, random, x0, z0, VegetationFeatures.DRY_GRASS, 4);
                scatter(level, gen, random, x0, z0, MiscOverworldFeatures.DESERT_WELL, random.nextInt(300) == 0 ? 1 : 0);
            }
            case BADLANDS -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.DEAD_BUSH, 4);
                scatter(level, gen, random, x0, z0, VegetationFeatures.CACTUS, 1);
                scatter(level, gen, random, x0, z0, VegetationFeatures.DRY_GRASS, 2);
            }
            case SAVANNA -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 20);
                scatter(level, gen, random, x0, z0, VegetationFeatures.TALL_GRASS, 8);
                scatter(level, gen, random, x0, z0, VegetationFeatures.DRY_GRASS, 4);
            }
            case JUNGLE -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS_JUNGLE, 24);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BAMBOO_VEGETATION, random.nextInt(3) == 0 ? 2 : 0);
                scatter(level, gen, random, x0, z0, VegetationFeatures.MELON, random.nextInt(6) == 0 ? 4 : 0);
            }
            case SWAMP -> {
                scatter(level, gen, random, x0, z0, VegetationFeatures.WATERLILY, 6);
                scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 6);
                scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_SWAMP, 2);
                scatter(level, gen, random, x0, z0, VegetationFeatures.SUGAR_CANE, 8);
                scatter(level, gen, random, x0, z0, VegetationFeatures.BROWN_MUSHROOM, 1);
                scatter(level, gen, random, x0, z0, VegetationFeatures.RED_MUSHROOM, 1);
            }
            case BEACH, RIVER -> scatter(level, gen, random, x0, z0, VegetationFeatures.SUGAR_CANE, 10);
            case OCEAN -> {
                scatter(level, gen, random, x0, z0, AquaticFeatures.SEAGRASS_MID, 12, Heightmap.Types.OCEAN_FLOOR_WG);
                scatter(level, gen, random, x0, z0, AquaticFeatures.KELP, 6, Heightmap.Types.OCEAN_FLOOR_WG);
                if (p.baseTemp > 24) {
                    scatter(level, gen, random, x0, z0, AquaticFeatures.WARM_OCEAN_VEGETATION, 4, Heightmap.Types.OCEAN_FLOOR_WG);
                    scatter(level, gen, random, x0, z0, AquaticFeatures.SEA_PICKLE, random.nextInt(8) == 0 ? 1 : 0,
                            Heightmap.Types.OCEAN_FLOOR_WG);
                }
            }
            case FROZEN_OCEAN -> {
                if (random.nextInt(24) == 0) {
                    int x = x0 + random.nextInt(16);
                    int z = z0 + random.nextInt(16);
                    feature(level, gen, random, MiscOverworldFeatures.ICEBERG_PACKED, new BlockPos(x, p.seaLevel, z));
                }
            }
            default -> {
            }
        }
        // Rivers and lakes get a fringe of reeds wherever they touch the forest.
        if (centre != TerranBiome.DESERT && centre != TerranBiome.BADLANDS) {
            scatter(level, gen, random, x0, z0, VegetationFeatures.SUGAR_CANE, 2);
        }
        // Waterfalls spill from cliffs and mountainsides.
        for (int i = 0; i < 6; i++) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (top > p.seaLevel + 20) {
                feature(level, gen, random, MiscOverworldFeatures.SPRING_WATER, new BlockPos(x, p.seaLevel + 8 + random.nextInt(top - p.seaLevel - 8), z));
            }
        }
        // Snow and ice settle last, on the cold side of the world only (the feature reads each column's biome).
        feature(level, gen, random, MiscOverworldFeatures.FREEZE_TOP_LAYER, new BlockPos(x0, 0, z0));
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> tree(TerranBiome b) {
        return switch (b) {
            case FOREST -> VegetationFeatures.TREES_BIRCH_AND_OAK_LEAF_LITTER;
            case BIRCH_FOREST -> VegetationFeatures.TREES_BIRCH;
            case DARK_FOREST -> VegetationFeatures.DARK_FOREST_VEGETATION;
            case FLOWER_FOREST -> VegetationFeatures.TREES_FLOWER_FOREST;
            case PLAINS, SUNFLOWER_PLAINS -> VegetationFeatures.TREES_PLAINS;
            case MEADOW -> VegetationFeatures.MEADOW_TREES;
            case CHERRY_GROVE -> TreeFeatures.CHERRY_BEES_005;
            case TAIGA, SNOWY_TAIGA -> VegetationFeatures.TREES_TAIGA;
            case OLD_GROWTH_TAIGA -> VegetationFeatures.TREES_OLD_GROWTH_SPRUCE_TAIGA;
            case SNOWY_PLAINS -> VegetationFeatures.TREES_SNOWY;
            case SAVANNA -> VegetationFeatures.TREES_SAVANNA;
            case JUNGLE -> VegetationFeatures.TREES_JUNGLE;
            case SWAMP -> TreeFeatures.SWAMP_OAK;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ storm and volcanic worlds

    /** Tornado alley: long grass bent flat, lone windswept trees, and trees the wind has already felled. */
    private static void storm(WorldGenLevel level, Hash.Rng rng, PlanetProfile p, PlanetChunkGenerator gen, int x0, int z0,
                              BlockPos.MutableBlockPos pos) {
        RandomSource random = RandomSource.create(rng.nextLong());
        scatter(level, gen, random, x0, z0, VegetationFeatures.GRASS, 20);
        scatter(level, gen, random, x0, z0, VegetationFeatures.TALL_GRASS, 6);
        scatter(level, gen, random, x0, z0, VegetationFeatures.DRY_GRASS, 4);
        scatter(level, gen, random, x0, z0, VegetationFeatures.FLOWER_PLAIN, 2);
        scatter(level, gen, random, x0, z0, VegetationFeatures.BUSH, 2);
        scatter(level, gen, random, x0, z0, VegetationFeatures.SUGAR_CANE, 3);
        scatter(level, gen, random, x0, z0, VegetationFeatures.TREES_WINDSWEPT_HILLS, random.nextInt(8) == 0 ? 1 : 0);
        scatter(level, gen, random, x0, z0, TreeFeatures.FALLEN_OAK_TREE, random.nextInt(10) == 0 ? 1 : 0);
        scatter(level, gen, random, x0, z0, MiscOverworldFeatures.FOREST_ROCK, random.nextInt(20) == 0 ? 1 : 0);
        boulders(level, rng, x0, z0, pos, p, 0.08);
    }

    /** Basalt columns, glowing magma vents and the charred stumps of whatever grew here before. */
    private static void volcanic(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p) {
        if (rng.chance(0.35)) {
            pillar(level, rng, x0 + rng.nextInt(16), z0 + rng.nextInt(16), pos, Blocks.BASALT.defaultBlockState(), rng.nextInt(4, 16));
        }
        if (rng.chance(0.14)) {
            vent(level, rng, x0 + 3 + rng.nextInt(10), z0 + 3 + rng.nextInt(10), pos);
        }
        if (rng.chance(0.12)) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (solidTop(level, pos)) {
                BlockState stump = rng.chance(0.5) ? Blocks.COAL_BLOCK.defaultBlockState()
                        : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
                int height = rng.nextInt(1, 4);
                for (int h = 0; h < height; h++) {
                    setIfAir(level, pos.set(x, y + h, z), stump);
                }
            }
        }
        boulders(level, rng, x0, z0, pos, p, 0.2);
        flora(level, rng, x0, z0, pos, p, 5, ModBlocks.EMBER_BLOOM.defaultBlockState(), ModBlocks.SPINE_PLANT.defaultBlockState());
    }

    /** A magma vent: a pit of lava ringed with glowing magma and basalt, sealed so it never spills. */
    private static void vent(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = groundY(level, x, z) - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.getBlockState(pos.set(x + dx, y, z + dz)).isAir() || level.getBlockState(pos.set(x + dx, y - 1, z + dz)).isAir()) {
                    return;
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                BlockState st = d == 0 ? Blocks.LAVA.defaultBlockState() : d == 1 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : rng.chance(0.6) ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                level.setBlock(pos.set(x + dx, y, z + dz), st, FLAGS);
                if (d == 0) {
                    level.setBlock(pos.set(x, y - 1, z), Blocks.MAGMA_BLOCK.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ underground

    /**
     * Things to find below ground: amethyst geodes, dungeons (on Earth-like worlds), dripstone caverns on
     * rocky ones, and fossils in the deep.
     */
    private static void underground(WorldGenLevel level, Hash.Rng rng, PlanetProfile p, PlanetChunkGenerator gen, int x0, int z0) {
        if (p.type == PlanetType.GAS_GIANT || p.type == PlanetType.SHATTERED || p.type == PlanetType.STELLAR || p.subsurfaceOcean) {
            return;
        }
        RandomSource random = RandomSource.create(rng.nextLong());
        boolean terran = p.type == PlanetType.TERRAN || p.type == PlanetType.STORM;
        int geodeOdds = p.type == PlanetType.CRYSTAL ? 8 : terran ? 24 : 40;
        if (random.nextInt(geodeOdds) == 0) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            feature(level, gen, random, CaveFeatures.AMETHYST_GEODE, new BlockPos(x, -50 + random.nextInt(80), z));
        }
        if (terran) {
            for (int i = 0; i < 4; i++) {
                int x = x0 + random.nextInt(16);
                int z = z0 + random.nextInt(16);
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 12;
                if (top > PlanetColumns.MIN_Y + 12) {
                    feature(level, gen, random, CaveFeatures.MONSTER_ROOM, new BlockPos(x, PlanetColumns.MIN_Y + 8 + random.nextInt(top - PlanetColumns.MIN_Y - 8), z));
                }
            }
        }
        boolean rocky = switch (p.type) {
            case BARREN_ROCK, CANYON, DESERT, TERRAN, STORM, CRATERED, ICE -> true;
            default -> false;
        };
        // Dripstone needs water seeping through the rock, so only worlds with air get it.
        if (rocky && p.atmosphere && random.nextInt(6) == 0) {
            for (int i = 0; i < 8; i++) {
                int x = x0 + random.nextInt(16);
                int z = z0 + random.nextInt(16);
                feature(level, gen, random, CaveFeatures.DRIPSTONE_CLUSTER, new BlockPos(x, -40 + random.nextInt(90), z));
            }
        }
        if (random.nextInt(64) == 0) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            feature(level, gen, random, CaveFeatures.FOSSIL_COAL, new BlockPos(x, -30 + random.nextInt(40), z));
        }
    }

    // ------------------------------------------------------------------ helpers

    static int groundY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private static boolean solidTop(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP) && below.getFluidState().isEmpty()
                && !below.is(BlockTags.ICE) && !below.is(Blocks.MAGMA_BLOCK) && level.getBlockState(pos).isAir();
    }

    private static void flora(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p,
                              int count, BlockState common, BlockState rare) {
        for (int i = 0; i < count; i++) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (!solidTop(level, pos)) {
                continue;
            }
            BlockState below = level.getBlockState(pos.below());
            if (below.is(Blocks.SNOW_BLOCK) && common.getBlock() != ModBlocks.FROST_BLOOM) {
                continue;
            }
            level.setBlock(pos, rng.chance(0.15) ? rare : common, FLAGS);
        }
    }

    private static void trees(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p,
                              int count, boolean tall) {
        BlockState log = ModBlocks.XENO_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaves = ModBlocks.XENO_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int i = 0; i < count; i++) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (!solidTop(level, pos) || y < p.seaLevel - 1) {
                continue;
            }
            int height = tall ? rng.nextInt(9, 18) : rng.nextInt(5, 8);
            // A curving trunk that leans as it grows, topped with a flat umbrella crown.
            double lean = rng.nextDouble() * Math.PI * 2;
            double tx = x;
            double tz = z;
            for (int h = 0; h < height; h++) {
                tx += Math.cos(lean) * (h > height / 2 ? 0.35 : 0.1);
                tz += Math.sin(lean) * (h > height / 2 ? 0.35 : 0.1);
                setIfAir(level, pos.set((int) Math.round(tx), y + h, (int) Math.round(tz)), log);
            }
            int cx = (int) Math.round(tx);
            int cz = (int) Math.round(tz);
            int cy = y + height;
            int radius = tall ? rng.nextInt(3, 5) : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius + 0.3) {
                        continue;
                    }
                    setIfAir(level, pos.set(cx + dx, cy, cz + dz), leaves);
                    if (d < radius - 1) {
                        setIfAir(level, pos.set(cx + dx, cy + 1, cz + dz), leaves);
                    }
                    if (d > radius - 0.8 && rng.chance(0.4)) {
                        // Drooping tendrils at the crown's edge.
                        int len = rng.nextInt(1, 3);
                        for (int k = 1; k <= len; k++) {
                            setIfAir(level, pos.set(cx + dx, cy - k, cz + dz), leaves);
                        }
                    }
                }
            }
        }
    }

    private static void mushroom(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos, PlanetProfile p) {
        int y = groundY(level, x, z);
        pos.set(x, y, z);
        if (!solidTop(level, pos)) {
            return;
        }
        int height = rng.nextInt(6, 14);
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        BlockState cap = ModBlocks.XENO_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int h = 0; h < height; h++) {
            setIfAir(level, pos.set(x, y + h, z), stem);
        }
        int r = rng.nextInt(3, 6);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.4) {
                    continue;
                }
                int drop = (int) Math.max(0, (d - r * 0.55) * 1.2);
                setIfAir(level, pos.set(x + dx, y + height - drop, z + dz), cap);
                if (d > r - 1 && rng.chance(0.3)) {
                    setIfAir(level, pos.set(x + dx, y + height - drop - 1, z + dz), ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState());
                }
            }
        }
    }

    private static void spire(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos, int height) {
        int y = groundY(level, x, z);
        pos.set(x, y, z);
        if (!solidTop(level, pos)) {
            return;
        }
        BlockState crystal = ModBlocks.CRYSTAL_BLOCK.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        double tiltX = rng.range(-0.25, 0.25);
        double tiltZ = rng.range(-0.25, 0.25);
        for (int h = 0; h < height; h++) {
            double t = 1.0 - (double) h / height;
            int r = (int) Math.round(t * 2.2);
            int cx = x + (int) Math.round(tiltX * h);
            int cz = z + (int) Math.round(tiltZ * h);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r + 1) {
                        setIfAir(level, pos.set(cx + dx, y + h, cz + dz), h > height - 3 ? glow : crystal);
                    }
                }
            }
        }
    }

    private static void clusters(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, int count) {
        BlockState shard = ModBlocks.CRYSTAL_SHARD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        for (int i = 0; i < count; i++) {
            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = groundY(level, x, z);
            pos.set(x, y, z);
            if (solidTop(level, pos)) {
                level.setBlock(pos, shard, FLAGS);
            }
        }
    }

    private static void iceSpike(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = groundY(level, x, z);
        int height = rng.nextInt(8, 28);
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        for (int h = 0; h < height; h++) {
            double t = 1.0 - (double) h / height;
            int r = (int) Math.round(t * 2.6);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        setIfAir(level, pos.set(x + dx, y + h, z + dz), ice);
                    }
                }
            }
        }
    }

    private static void pillar(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos, BlockState state, int height) {
        int y = groundY(level, x, z);
        pos.set(x, y, z);
        if (!solidTop(level, pos)) {
            return;
        }
        for (int h = 0; h < height; h++) {
            setIfAir(level, pos.set(x, y + h, z), state);
            if (h < height / 3) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (rng.chance(0.5)) {
                        setIfAir(level, pos.set(x + d.getStepX(), y + h, z + d.getStepZ()), state);
                    }
                }
            }
        }
    }

    private static void boulders(WorldGenLevel level, Hash.Rng rng, int x0, int z0, BlockPos.MutableBlockPos pos, PlanetProfile p, double chance) {
        if (!rng.chance(chance)) {
            return;
        }
        int x = x0 + rng.nextInt(16);
        int z = z0 + rng.nextInt(16);
        int y = groundY(level, x, z) - 1;
        double r = rng.range(1.2, 3.2);
        BlockState rock = p.palette.stone();
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dy * dy * 1.6 + dz * dz <= r * r) {
                        setIfAir(level, pos.set(x + dx, y + dy, z + dz), rock);
                    }
                }
            }
        }
    }

    private static void fossil(WorldGenLevel level, Hash.Rng rng, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = groundY(level, x, z);
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        int length = rng.nextInt(10, 20);
        for (int i = 0; i < length; i++) {
            setIfAir(level, pos.set(x + i - length / 2, y, z), bone);
            if (i % 3 == 0) {
                for (int k = 1; k <= 4; k++) {
                    int arc = k < 3 ? k : 2;
                    setIfAir(level, pos.set(x + i - length / 2, y + arc, z + k), bone);
                    setIfAir(level, pos.set(x + i - length / 2, y + arc, z - k), bone);
                }
            }
        }
    }

    static void setIfAir(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, FLAGS);
        }
    }
}
