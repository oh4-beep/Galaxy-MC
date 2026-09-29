package com.galaxymc.client.color;

import com.galaxymc.block.ExoticOreBlock;
import com.galaxymc.client.ClientState;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.mineral.Minerals;
import com.galaxymc.registry.ModBlocks;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Re-colours Galaxy MC's shared "alien" blocks for the planet they sit on. The frontier's millions of
 * worlds share one block palette; each planet's grass, foliage, stone, dust and crystal colours (and the
 * colour of each of its four exotic minerals) are applied here as block tints.
 */
public final class PlanetTints {
    private PlanetTints() {}

    private static final Map<Long, Optional<PlanetProfile>> CACHE = new ConcurrentHashMap<>();
    private static volatile ResourceKey<Level> cachedDim;

    /** The planet a block belongs to (cached per chunk column), or null on Earth and in deep space. */
    private static PlanetProfile planet(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || pos == null) {
            return null;
        }
        ResourceKey<Level> dim = level.dimension();
        if (dim != cachedDim) {
            CACHE.clear();
            cachedDim = dim;
        }
        long key = ((long) (pos.getX() >> 4) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
        Optional<PlanetProfile> p = CACHE.get(key);
        if (p == null) {
            p = Optional.ofNullable(Planets.at(dim, ClientState.galaxySeed, pos.getX(), pos.getZ()));
            if (CACHE.size() > 8192) {
                CACHE.clear();
            }
            CACHE.put(key, p);
        }
        return p.orElse(null);
    }

    private static int darker(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f);
        int g = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    public static void init() {
        BlockColorRegistry.register((state, level, pos, tints) -> tints.add(grass(pos)), ModBlocks.ALIEN_GRASS, ModBlocks.XENO_GRASS);
        BlockColorRegistry.register((state, level, pos, tints) -> {
            PlanetProfile p = planet(pos);
            tints.add(p == null ? 0x7a4ab0 : p.foliageColor);
        }, ModBlocks.XENO_LEAVES);
        BlockColorRegistry.register((state, level, pos, tints) -> tints.add(stone(pos, 1.0)),
                ModBlocks.ALIEN_STONE, ModBlocks.ALIEN_STONE_BRICKS, ModBlocks.ALIEN_COAL_ORE, ModBlocks.ALIEN_IRON_ORE,
                ModBlocks.ALIEN_COPPER_ORE, ModBlocks.ALIEN_GOLD_ORE, ModBlocks.ALIEN_REDSTONE_ORE, ModBlocks.ALIEN_LAPIS_ORE,
                ModBlocks.ALIEN_DIAMOND_ORE, ModBlocks.ALIEN_EMERALD_ORE);
        BlockColorRegistry.register((state, level, pos, tints) -> tints.add(stone(pos, 0.78)), ModBlocks.ALIEN_DEEP_STONE);
        BlockColorRegistry.register((state, level, pos, tints) -> {
            PlanetProfile p = planet(pos);
            tints.add(p == null ? 0xd8c8a8 : p.dustTint);
        }, ModBlocks.ALIEN_SAND, ModBlocks.ALIEN_REGOLITH, ModBlocks.ALIEN_SOIL);
        BlockColorRegistry.register((state, level, pos, tints) -> {
            PlanetProfile p = planet(pos);
            tints.add(p == null ? 0x7ad0e8 : p.crystalColor);
        }, ModBlocks.CRYSTAL_BLOCK, ModBlocks.GLOWING_CRYSTAL_BLOCK, ModBlocks.CRYSTAL_SHARD);
        BlockColorRegistry.register((state, level, pos, tints) -> {
            PlanetProfile p = planet(pos);
            boolean deep = state.getBlock() == ModBlocks.DEEP_EXOTIC_ORE;
            tints.add(p == null ? 0xffffff : darker(p.stoneTint, deep ? 0.78 : 1.0));
            int slot = state.getValue(ExoticOreBlock.SLOT);
            tints.add(p == null ? 0xc080ff : Minerals.forPlanet(p, slot).color());
        }, ModBlocks.EXOTIC_ORE, ModBlocks.DEEP_EXOTIC_ORE);
    }

    private static int grass(BlockPos pos) {
        PlanetProfile p = planet(pos);
        return p == null ? 0x6aa84f : p.grassColor;
    }

    private static int stone(BlockPos pos, double f) {
        PlanetProfile p = planet(pos);
        return p == null ? darker(0xffffff, f) : darker(p.stoneTint, f);
    }
}
