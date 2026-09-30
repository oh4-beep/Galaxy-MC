package com.galaxymc.entity;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.util.Hash;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Populates planets with life.
 *
 * <p>Every world gets its own fauna: a deterministic pick, seeded by the planet, from the species whose
 * climate, world type and tier fit - always a few grazing herds, a handful of predators, sometimes
 * something colossal. Around each player on a planet the spawner keeps the local population near a
 * cap that rises with the world's danger, spawning groups at a distance on the kind of ground each
 * creature needs (burrowers under it, swimmers in its lakes, flyers above it).
 */
public final class FaunaSpawner {
    private FaunaSpawner() {}

    public record Entry(Species species, int weight) {}

    private static final Map<String, List<Entry>> FAUNA = new ConcurrentHashMap<>();

    public static String climate(double temp) {
        if (temp > 300) {
            return "scorching";
        }
        if (temp > 75) {
            return "hot";
        }
        if (temp > -5) {
            return "temperate";
        }
        if (temp > -80) {
            return "cold";
        }
        return "frozen";
    }

    private static boolean hasWater(PlanetProfile p) {
        return p.palette.fluid() != null && p.palette.fluid().getBlock() == Blocks.WATER && p.seaLevel > -40
                || p.lakes != null && p.lakes.fluid() != null && p.lakes.fluid().getBlock() == Blocks.WATER;
    }

    private static boolean hasLava(PlanetProfile p) {
        return p.palette.fluid() != null && p.palette.fluid().getBlock() == Blocks.LAVA && p.seaLevel > -40
                || p.lakes != null && (p.lakes.fluid() != null && p.lakes.fluid().getBlock() == Blocks.LAVA
                || p.lakes.fluid2() != null && p.lakes.fluid2().getBlock() == Blocks.LAVA);
    }

    private static boolean fits(Species s, PlanetProfile p, String climate) {
        if (s.minTier > p.tier + (p.isFrontier() ? 0 : 1)) {
            return false;
        }
        if (!s.types.isEmpty() && !s.types.contains(p.type.name())) {
            return false;
        }
        if (s.flies() && !p.atmosphere && !s.element.equals("void") && !s.element.equals("crystal")) {
            return false;
        }
        if (s.wormMode == Species.WormMode.SWIM) {
            return s.fireImmune ? hasLava(p) : hasWater(p);
        }
        if (p.type == PlanetType.GAS_GIANT || p.type == PlanetType.SHATTERED) {
            return s.flies() || s.kind == Species.Kind.STATIC;
        }
        if (p.type == PlanetType.OCEAN && s.kind == Species.Kind.WORM && s.wormMode == Species.WormMode.BURROW && s.giant) {
            return false;
        }
        return s.fitsClimate(climate) || s.climates.size() >= 5;
    }

    /** The species that live on a planet, with spawn weights. Cached per planet. */
    public static List<Entry> fauna(PlanetProfile p) {
        return FAUNA.computeIfAbsent(p.id + "/" + p.seed, k -> pick(p));
    }

    private static List<Entry> pick(PlanetProfile p) {
        String climate = climate(p.baseTemp);
        Hash.Rng rng = new Hash.Rng(Hash.of(p.seed, 0x46415541L));
        List<Species> candidates = new ArrayList<>();
        List<Entry> out = new ArrayList<>();
        for (Species s : SpeciesRegistry.all()) {
            if (s.sol.contains(p.id)) {
                out.add(new Entry(s, s.weight * 2));
            } else if (fits(s, p, climate)) {
                candidates.add(s);
            }
        }
        int wanted = Math.min(24, 9 + p.tier + rng.nextInt(6));
        int passive = 0;
        int giants = 0;
        int guard = 0;
        while (out.size() < wanted && !candidates.isEmpty() && guard++ < 400) {
            Species s = weighted(candidates, rng);
            candidates.remove(s);
            if (s.giant && giants >= (rng.chance(0.5) ? 2 : 1)) {
                continue;
            }
            if (s.giant) {
                giants++;
            }
            if (s.passive()) {
                passive++;
            }
            out.add(new Entry(s, s.weight));
        }
        // Every world needs something to hunt.
        for (int i = 0; passive < 2 && i < 200 && !candidates.isEmpty(); i++) {
            Species s = candidates.get(rng.nextInt(candidates.size()));
            if (s.passive()) {
                candidates.remove(s);
                out.add(new Entry(s, s.weight + 4));
                passive++;
            }
        }
        GalaxyMC.LOG.debug("Fauna of {}: {}", p.name, out.stream().map(e -> e.species().id).toList());
        return out;
    }

    private static Species weighted(List<Species> list, Hash.Rng rng) {
        int total = 0;
        for (Species s : list) {
            total += s.weight;
        }
        int r = rng.nextInt(Math.max(1, total));
        for (Species s : list) {
            r -= s.weight;
            if (r < 0) {
                return s;
            }
        }
        return list.get(list.size() - 1);
    }

    // ------------------------------------------------------------------ spawning

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        if (time % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            ServerLevel level = player.level();
            if (!level.getGameRules().get(GameRules.SPAWN_MOBS)) {
                continue;
            }
            PlanetProfile p = Planets.at(level.dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());
            if (p == null) {
                continue;
            }
            try {
                populate(level, player, p);
            } catch (Exception e) {
                GalaxyMC.LOG.warn("Fauna spawn failed on {}", p.name, e);
            }
        }
    }

    private static void populate(ServerLevel level, ServerPlayer player, PlanetProfile p) {
        List<Entry> fauna = fauna(p);
        if (fauna.isEmpty()) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(80, 64, 80);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, area, e -> e instanceof GalaxyCreature c && c.countsTowardsCap());
        int cap = 28 + p.danger * 3;
        if (nearby.size() >= cap || level.getRandom().nextFloat() > 0.7F) {
            return;
        }
        long giantsNear = nearby.stream().filter(e -> ((GalaxyCreature) e).species() != null && ((GalaxyCreature) e).species().giant).count();
        Entry entry = pickEntry(fauna, level);
        Species s = entry.species();
        if (s.giant && giantsNear > 0) {
            return;
        }
        if (s.hostile() && level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return;
        }
        double a = level.getRandom().nextDouble() * Math.PI * 2;
        double r = (s.giant ? 48 : 24) + level.getRandom().nextDouble() * 36;
        int x = Mth.floor(player.getX() + Math.cos(a) * r);
        int z = Mth.floor(player.getZ() + Math.sin(a) * r);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return;
        }
        if (Planets.at(level.dimension(), GalaxyMC.galaxySeed(), x, z) != p) {
            return;
        }
        int count = s.groupMin + level.getRandom().nextInt(Math.max(1, s.groupMax - s.groupMin + 1));
        for (int i = 0; i < count; i++) {
            int gx = x + level.getRandom().nextInt(9) - 4;
            int gz = z + level.getRandom().nextInt(9) - 4;
            spawnOne(level, s, p, gx, gz);
        }
    }

    private static Entry pickEntry(List<Entry> fauna, ServerLevel level) {
        int total = 0;
        for (Entry e : fauna) {
            total += e.weight();
        }
        int r = level.getRandom().nextInt(Math.max(1, total));
        for (Entry e : fauna) {
            r -= e.weight();
            if (r < 0) {
                return e;
            }
        }
        return fauna.get(0);
    }

    /** Spawns one creature of a species at a column, adapted to the planet. Returns null if it cannot live there. */
    public static Mob spawnOne(ServerLevel level, Species s, PlanetProfile p, int x, int z) {
        EntityType<?> type = SpeciesRegistry.typeOf(s);
        if (type == null) {
            return null;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (ground <= level.getMinY() + 2) {
            return null;
        }
        BlockPos surface = new BlockPos(x, ground, z);
        FluidState fluid = level.getFluidState(surface.below());
        double y = ground;
        if (s.wormMode == Species.WormMode.SWIM) {
            if (fluid.isEmpty() || s.fireImmune != level.getBlockState(surface.below()).is(Blocks.LAVA)) {
                return null;
            }
            y = ground - 2.5;
        } else if (s.wormMode == Species.WormMode.BURROW) {
            y = ground - 6 - s.width * s.scale;
        } else if (s.flies()) {
            y = ground + 4 + level.getRandom().nextInt(10);
        } else if (!fluid.isEmpty() && !s.swim) {
            return null;
        }
        Mob mob = (Mob) type.create(level, EntitySpawnReason.NATURAL);
        if (mob == null) {
            return null;
        }
        mob.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (s.kind != Species.Kind.WORM && !s.flies() && !level.noCollision(mob)) {
            mob.discard();
            return null;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(surface), EntitySpawnReason.NATURAL, null);
        if (mob instanceof AlienMob alien) {
            alien.applyStrain(Strain.of(p, s));
        }
        level.addFreshEntity(mob);
        return mob;
    }
}
