package com.galaxymc.hazard;

import com.galaxymc.GalaxyMC;
import com.galaxymc.entity.Voices;
import com.galaxymc.galaxy.Hazard;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.network.HazardPayload;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModGameRules;
import com.galaxymc.util.NameGenerator;
import com.galaxymc.world.PlanetColumns;
import com.galaxymc.world.TerrainShaper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runs planetary natural disasters around players.
 *
 * <p>Every five seconds each player on a world with hazards has a small chance per hazard of setting
 * one off nearby (on average one of each kind every seven or eight minutes, never within two minutes
 * of the last, and never in the first minute after landing). The server simulates each event -
 * moving it, throwing entities about and, if {@link ModGameRules#HAZARDS_CHANGE_TERRAIN} allows,
 * moving blocks - and streams it to nearby clients, which draw it (see HazardEffects).
 * <ul>
 *   <li><b>Tornado</b> - a wandering funnel that sucks in, spins and flings creatures, players and
 *   items, and strips plants, leaves and loose soil from the ground it crosses.</li>
 *   <li><b>Tsunami</b> - the sea draws back, then a wave wall rolls in from the nearest ocean, sweeping
 *   everything inland and flooding the shore with water that drains away behind it.</li>
 *   <li><b>Eruption</b> - the nearest volcano blows: magma bombs and fire rain on its flanks, lava
 *   pours over the crater rim, and anything near the summit burns.</li>
 *   <li><b>Meteor shower</b> - burning rocks streak down and explode, leaving craters with an
 *   ore-rich core.</li>
 *   <li><b>Lightning storm</b> - bolts strike all around, sometimes very close.</li>
 * </ul>
 */
public final class HazardManager {
    private HazardManager() {}

    /** Per-hazard chance of starting, per player, per five-second roll. */
    private static final double CHANCE = 1.0 / 90.0;
    private static final int COOLDOWN = 2400;
    private static final int ARRIVAL_GRACE = 1200;
    private static final double SYNC_RANGE = 640.0;

    private static final List<HazardEvent> EVENTS = new ArrayList<>();
    private static final Map<UUID, PlayerState> PLAYERS = new HashMap<>();
    private static int nextId = 1;

    private static final class PlayerState {
        String planet = "";
        long cooldownUntil;
    }

    public static List<HazardEvent> events() {
        return EVENTS;
    }

    public static void clear() {
        EVENTS.clear();
        PLAYERS.clear();
    }

    public static void remove(UUID player) {
        PLAYERS.remove(player);
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long time = overworld.getGameTime();
        boolean enabled = overworld.getGameRules().get(ModGameRules.PLANET_HAZARDS);
        boolean terrain = overworld.getGameRules().get(ModGameRules.HAZARDS_CHANGE_TERRAIN);
        if (time % 100 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                roll(player, time, enabled);
            }
        }
        Iterator<HazardEvent> it = EVENTS.iterator();
        while (it.hasNext()) {
            HazardEvent e = it.next();
            ServerLevel level = server.getLevel(e.dimension);
            boolean keep = false;
            if (level != null && enabled) {
                e.age++;
                try {
                    keep = step(level, e, terrain) && !e.finished() && watched(level, e);
                } catch (RuntimeException ex) {
                    GalaxyMC.LOG.warn("Hazard {} failed", e.type, ex);
                }
            }
            if (!keep) {
                finish(level, e);
                it.remove();
            }
        }
        if (time % 4 == 0) {
            sync(server);
        }
    }

    private static void roll(ServerPlayer player, long time, boolean enabled) {
        PlayerState s = PLAYERS.computeIfAbsent(player.getUUID(), k -> new PlayerState());
        ServerLevel level = player.level();
        PlanetProfile p = Planets.at(level.dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());
        String id = p == null ? "" : p.id;
        if (!id.equals(s.planet)) {
            s.planet = id;
            s.cooldownUntil = time + ARRIVAL_GRACE;
            return;
        }
        if (!enabled || p == null || p.hazards.isEmpty() || time < s.cooldownUntil || player.isSpectator()) {
            return;
        }
        for (Hazard h : p.hazards) {
            if (level.getRandom().nextDouble() >= CHANCE || runningNear(level, h, player.getX(), player.getZ())) {
                continue;
            }
            HazardEvent e = start(level, player, p, h);
            if (e != null) {
                s.cooldownUntil = time + COOLDOWN;
                return;
            }
        }
    }

    private static boolean runningNear(ServerLevel level, Hazard h, double x, double z) {
        for (HazardEvent e : EVENTS) {
            if (e.type == h && e.dimension.equals(level.dimension()) && Math.hypot(e.x - x, e.z - z) < 400) {
                return true;
            }
        }
        return false;
    }

    /** Starts a hazard of the given kind near a player, if the ground allows it; null otherwise. */
    public static HazardEvent start(ServerLevel level, ServerPlayer player, PlanetProfile p, Hazard h) {
        HazardEvent e = switch (h) {
            case TORNADOES -> startTornado(level, player, p);
            case TSUNAMIS -> startTsunami(level, player, p);
            case ERUPTIONS -> startEruption(level, player, p);
            case METEORS -> startShower(level, player, p, Hazard.METEORS);
            case LIGHTNING -> startShower(level, player, p, Hazard.LIGHTNING);
        };
        if (e != null) {
            EVENTS.add(e);
            warn(level, e, player);
        }
        return e;
    }

    private static boolean step(ServerLevel level, HazardEvent e, boolean terrain) {
        return switch (e.type) {
            case TORNADOES -> tornado(level, e, terrain);
            case TSUNAMIS -> tsunami(level, e, terrain);
            case ERUPTIONS -> eruption(level, e, terrain);
            case METEORS -> meteors(level, e, terrain);
            case LIGHTNING -> lightning(level, e);
        };
    }

    /** Events wind down once nobody has been within 500 blocks for ten seconds. */
    private static boolean watched(ServerLevel level, HazardEvent e) {
        if (nearestPlayer(level, e.x, e.z, 500) != null) {
            e.idleTicks = 0;
            return true;
        }
        return ++e.idleTicks < 200;
    }

    private static void finish(ServerLevel level, HazardEvent e) {
        if (level == null) {
            return;
        }
        // Never leave a poured lava source behind.
        for (HazardEvent.Pour pour : e.pours) {
            if (level.hasChunkAt(pour.pos()) && level.getFluidState(pour.pos()).isSource()) {
                level.setBlock(pour.pos(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        e.pours.clear();
    }

    private static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<HazardPayload.Entry> near = new ArrayList<>();
            for (HazardEvent e : EVENTS) {
                if (e.dimension.equals(player.level().dimension()) && Math.hypot(e.x - player.getX(), e.z - player.getZ()) < SYNC_RANGE) {
                    near.add(HazardPayload.Entry.of(e));
                }
            }
            if (!near.isEmpty()) {
                ServerPlayNetworking.send(player, new HazardPayload(near));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static HazardEvent create(ServerLevel level, PlanetProfile p, Hazard h, String name, int warmup, int lifetime) {
        return new HazardEvent(nextId++, h, level.dimension(), p.id, name, warmup, lifetime, level.getRandom().nextLong());
    }

    private static PlanetProfile planetAt(ServerLevel level, double x, double z) {
        return Planets.at(level.dimension(), GalaxyMC.galaxySeed(), x, z);
    }

    private static boolean onPlanet(ServerLevel level, HazardEvent e, double x, double z) {
        PlanetProfile p = planetAt(level, x, z);
        return p != null && p.id.equals(e.planetId);
    }

    /** First air block above the ground (ignoring leaves) at a column; falls back to terrain maths. */
    private static double groundY(ServerLevel level, PlanetProfile p, double x, double z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        if (level.hasChunkAt(new BlockPos(ix, 0, iz))) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
        }
        return p == null ? level.getSeaLevel() : PlanetColumns.surfaceY(p, TerrainShaper.of(p), ix, iz) + 1;
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, double x, double z, double range) {
        ServerPlayer best = null;
        double bestD = range;
        for (ServerPlayer pl : level.players()) {
            double d = Math.hypot(pl.getX() - x, pl.getZ() - z);
            if (d < bestD && !pl.isSpectator()) {
                bestD = d;
                best = pl;
            }
        }
        return best;
    }

    private static List<Entity> caught(ServerLevel level, AABB box) {
        return level.getEntities((Entity) null, box, ent -> (ent instanceof LivingEntity || ent instanceof ItemEntity)
                && !(ent instanceof Player pl && (pl.isSpectator() || pl.getAbilities().flying)));
    }

    /** How strongly an entity resists being thrown about (giants barely move). */
    private static double grip(Entity ent) {
        if (ent instanceof LivingEntity le && le.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            return 1.0 - Math.min(0.95, le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        }
        return 1.0;
    }

    private static String compass(double fromX, double fromZ, double toX, double toZ) {
        double a = Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX));
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return names[Math.floorMod((int) Math.round(a / 45.0), 8)];
    }

    private static void warn(ServerLevel level, HazardEvent e, ServerPlayer trigger) {
        for (ServerPlayer pl : level.players()) {
            if (Math.hypot(pl.getX() - e.x, pl.getZ() - e.z) > 420 && pl != trigger) {
                continue;
            }
            String dir = compass(pl.getX(), pl.getZ(), e.x, e.z);
            Component msg = switch (e.type) {
                case TORNADOES -> Component.translatable("message.galaxy_mc.hazard.tornado", dir);
                case TSUNAMIS -> Component.translatable("message.galaxy_mc.hazard.tsunami", dir);
                case ERUPTIONS -> Component.translatable("message.galaxy_mc.hazard.eruption", e.name, dir);
                case METEORS -> Component.translatable("message.galaxy_mc.hazard.meteors");
                case LIGHTNING -> Component.translatable("message.galaxy_mc.hazard.lightning");
            };
            Component styled = msg.copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            pl.sendOverlayMessage(styled);
            pl.sendSystemMessage(msg.copy().withStyle(ChatFormatting.GOLD));
            String siren = switch (e.type) {
                case TORNADOES, TSUNAMIS -> "event.raid.horn";
                case ERUPTIONS -> "entity.lightning_bolt.thunder";
                case METEORS -> "item.trident.thunder";
                case LIGHTNING -> "entity.lightning_bolt.thunder";
            };
            Voices.play(level, pl.blockPosition(), siren, SoundSource.WEATHER, e.type == Hazard.ERUPTIONS ? 2.0F : 1.2F,
                    e.type == Hazard.ERUPTIONS ? 0.5F : 1.0F);
        }
    }

    // ------------------------------------------------------------------ tornadoes

    private static HazardEvent startTornado(ServerLevel level, ServerPlayer player, PlanetProfile p) {
        if (!p.atmosphere || TerrainShaper.of(p).isVolumetric()) {
            return null;
        }
        RandomSource r = level.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = 60 + r.nextDouble() * 50;
            double x = player.getX() + Math.cos(a) * d;
            double z = player.getZ() + Math.sin(a) * d;
            PlanetProfile here = planetAt(level, x, z);
            if (here == null || !here.id.equals(p.id)) {
                continue;
            }
            double ground = groundY(level, p, x, z);
            if (p.palette.fluid() != null && ground <= p.seaLevel + 1) {
                continue;
            }
            HazardEvent e = create(level, p, Hazard.TORNADOES, "Tornado", 80, 1600 + r.nextInt(1600));
            e.x = x;
            e.z = z;
            e.y = ground;
            double toward = Math.atan2(player.getZ() - z, player.getX() - x) + (r.nextDouble() - 0.5) * 1.2;
            e.dx = Math.cos(toward);
            e.dz = Math.sin(toward);
            e.speed = 0.12 + r.nextDouble() * 0.1;
            e.radius = 3.5 + r.nextDouble() * 4.0;
            e.height = 45 + r.nextDouble() * 50;
            return e;
        }
        return null;
    }

    private static boolean tornado(ServerLevel level, HazardEvent e, boolean terrain) {
        RandomSource r = level.getRandom();
        double heading = Math.atan2(e.dz, e.dx) + (r.nextDouble() - 0.5) * 0.08;
        e.dx = Math.cos(heading);
        e.dz = Math.sin(heading);
        double speed = e.active() ? e.speed : e.speed * 0.3;
        e.x += e.dx * speed;
        e.z += e.dz * speed;
        if (!onPlanet(level, e, e.x, e.z)) {
            return false;
        }
        PlanetProfile p = planetAt(level, e.x, e.z);
        e.y += (groundY(level, p, e.x, e.z) - e.y) * 0.25;
        double in = e.intensity();

        double pull = e.radius * 2.5 + 8.0;
        AABB box = new AABB(e.x - pull, e.y - 6, e.z - pull, e.x + pull, e.y + e.height, e.z + pull);
        for (Entity ent : caught(level, box)) {
            double ox = ent.getX() - e.x;
            double oz = ent.getZ() - e.z;
            double d = Math.sqrt(ox * ox + oz * oz) + 0.01;
            if (d > pull) {
                continue;
            }
            double s = in * Math.pow(1.0 - d / pull, 0.7) * grip(ent);
            Vec3 v = ent.getDeltaMovement();
            double up = ent.getY() - e.y;
            double vx;
            double vy;
            double vz;
            if (up > e.height * 0.7) {
                // Spat out of the top of the funnel.
                vx = v.x + ox / d * 0.4 * s;
                vz = v.z + oz / d * 0.4 * s;
                vy = v.y;
            } else {
                double inward = -0.1 * s;
                double swirl = 0.3 * s;
                vx = v.x * 0.9 + ox / d * inward - oz / d * swirl;
                vz = v.z * 0.9 + oz / d * inward + ox / d * swirl;
                double lift = d < e.radius * 1.3 ? 0.17 * in * grip(ent) : 0.07 * s;
                vy = Math.min(0.9, v.y + lift);
            }
            ent.setDeltaMovement(vx, vy, vz);
            ent.hurtMarked = true;
            if (d < e.radius && ent instanceof LivingEntity le && e.age % 20 == 0) {
                le.hurtServer(level, level.damageSources().flyIntoWall(), 1.0F + (float) in);
            }
        }
        if (terrain && e.active() && e.age % 3 == 0) {
            scour(level, e, r);
        }
        return true;
    }

    private static boolean loose(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(Blocks.GRAVEL) || st.is(Blocks.SNOW_BLOCK)
                || st.is(ModBlocks.ALIEN_SAND) || st.is(ModBlocks.ALIEN_SOIL) || st.is(ModBlocks.ALIEN_GRASS)
                || st.is(ModBlocks.ALIEN_REGOLITH) || st.is(ModBlocks.ASH_BLOCK) || st.is(ModBlocks.FUNGAL_TURF)
                || st.is(ModBlocks.TOXIC_TURF) || st.is(ModBlocks.MARS_SAND);
    }

    /** Rips up the ground under the funnel: plants and leaves are shredded, loose soil is flung. */
    private static void scour(ServerLevel level, HazardEvent e, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2.0;
        double d = r.nextDouble() * e.radius * 1.3;
        int x = Mth.floor(e.x + Math.cos(a) * d);
        int z = Mth.floor(e.z + Math.sin(a) * d);
        BlockPos probe = new BlockPos(x, 0, z);
        if (!level.hasChunkAt(probe)) {
            return;
        }
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        BlockPos above = top.above();
        BlockState plant = level.getBlockState(above);
        if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty()) {
            level.destroyBlock(above, false);
        }
        BlockState st = level.getBlockState(top);
        if (st.isAir() || !st.getFluidState().isEmpty()) {
            return;
        }
        if (st.is(BlockTags.LEAVES)) {
            level.destroyBlock(top, false);
            return;
        }
        if (loose(st) && r.nextInt(2) == 0) {
            FallingBlockEntity fb = FallingBlockEntity.fall(level, top, st);
            fb.disableDrop();
            double swirl = 0.5 + r.nextDouble() * 0.4;
            fb.setDeltaMovement(-Math.sin(a) * swirl, 0.8 + r.nextDouble() * 0.7, Math.cos(a) * swirl);
            fb.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ tsunamis

    private static HazardEvent startTsunami(ServerLevel level, ServerPlayer player, PlanetProfile p) {
        if (p.palette.fluid() == null || p.palette.fluid().getBlock() != Blocks.WATER || p.subsurfaceOcean
                || p.seaLevel <= PlanetColumns.MIN_Y + 30) {
            return null;
        }
        TerrainShaper shaper = TerrainShaper.of(p);
        double bestDist = Double.MAX_VALUE;
        double bestAngle = 0;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2.0 / 24.0;
            for (int d = 16; d <= 220; d += 8) {
                double x = player.getX() + Math.cos(a) * d;
                double z = player.getZ() + Math.sin(a) * d;
                PlanetProfile here = planetAt(level, x, z);
                if (here == null || !here.id.equals(p.id)) {
                    break;
                }
                double h = PlanetColumns.height(p, shaper, x, z, null);
                if (Double.isNaN(h)) {
                    break;
                }
                if (h < p.seaLevel - 5) {
                    if (d < bestDist) {
                        bestDist = d;
                        bestAngle = a;
                    }
                    break;
                }
            }
        }
        if (bestDist > 220) {
            return null;
        }
        RandomSource r = level.getRandom();
        HazardEvent e = create(level, p, Hazard.TSUNAMIS, "Tsunami", 200, 1400);
        double start = bestDist + 70;
        e.x = player.getX() + Math.cos(bestAngle) * start;
        e.z = player.getZ() + Math.sin(bestAngle) * start;
        e.dx = -Math.cos(bestAngle);
        e.dz = -Math.sin(bestAngle);
        e.seaLevel = p.seaLevel;
        e.y = p.seaLevel;
        e.radius = 45 + r.nextDouble() * 25;
        e.height = 7 + r.nextDouble() * 6;
        e.speed = 0.38 + r.nextDouble() * 0.08;
        e.coastDistance = start - bestDist + 6;
        e.landRun = 50 + e.height * 6;
        return e;
    }

    private static boolean tsunami(ServerLevel level, HazardEvent e, boolean terrain) {
        if (!e.active()) {
            return true;
        }
        e.travelled += e.speed;
        e.x += e.dx * e.speed;
        e.z += e.dz * e.speed;
        double surge = e.surge();
        if (surge <= 0.2) {
            return false;
        }
        double surgeY = e.seaLevel + surge;
        double px = -e.dz;
        double pz = e.dx;
        double w = e.radius;
        double in = e.intensity();
        AABB box = new AABB(e.x - w - 10, e.seaLevel - 6, e.z - w - 10, e.x + w + 10, surgeY + 3, e.z + w + 10);
        for (Entity ent : caught(level, box)) {
            double rx = ent.getX() - e.x;
            double rz = ent.getZ() - e.z;
            double along = rx * e.dx + rz * e.dz;
            double across = rx * px + rz * pz;
            if (Math.abs(across) > w || along < -8 || along > 2 || ent.getY() > surgeY + 1.5) {
                continue;
            }
            double s = in * grip(ent);
            Vec3 v = ent.getDeltaMovement();
            ent.setDeltaMovement(v.x * 0.5 + e.dx * 0.9 * s, Math.max(v.y, 0.22 * s), v.z * 0.5 + e.dz * 0.9 * s);
            ent.hurtMarked = true;
            ent.clearFire();
            if (ent instanceof LivingEntity le && e.age % 10 == 0) {
                le.hurtServer(level, level.damageSources().drown(), 2.0F);
            }
        }
        if (terrain && e.age % 2 == 0) {
            // The surge: flowing water poured over the land at the wave front. It has no source, so it
            // runs down to the sea and drains away once the wave has passed.
            BlockState water = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
            for (double s = -w; s <= w; s += 3.0) {
                int fx = Mth.floor(e.x + px * s + e.dx * 1.5);
                int fz = Mth.floor(e.z + pz * s + e.dz * 1.5);
                if (!level.hasChunkAt(new BlockPos(fx, 0, fz))) {
                    continue;
                }
                int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz);
                if (g <= e.seaLevel + 1 || g >= surgeY) {
                    continue;
                }
                int topY = Math.min(Mth.floor(surgeY), g + 2);
                for (int y = g; y <= topY; y++) {
                    BlockPos bp = new BlockPos(fx, y, fz);
                    BlockState st = level.getBlockState(bp);
                    if (st.isAir()) {
                        level.setBlock(bp, water, 3);
                    } else if (st.canBeReplaced() && st.getFluidState().isEmpty()) {
                        level.destroyBlock(bp, true);
                        level.setBlock(bp, water, 3);
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ eruptions

    private static HazardEvent startEruption(ServerLevel level, ServerPlayer player, PlanetProfile p) {
        TerrainShaper shaper = TerrainShaper.of(p);
        TerrainShaper.Volcano best = null;
        double bestD = Double.MAX_VALUE;
        for (TerrainShaper.Volcano v : shaper.volcanoesNear(player.getX(), player.getZ(), 520)) {
            double d = Math.hypot(v.x() - player.getX(), v.z() - player.getZ());
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        if (best == null) {
            return null;
        }
        RandomSource r = level.getRandom();
        String name = "Mount " + NameGenerator.word(best.seed(), 2);
        HazardEvent e = create(level, p, Hazard.ERUPTIONS, name, 140, 900 + r.nextInt(700));
        e.x = best.x();
        e.z = best.z();
        e.y = best.lavaLevel() != TerrainShaper.NO_LAKE ? best.lavaLevel() + 1
                : PlanetColumns.surfaceY(p, shaper, Mth.floor(best.x()), Mth.floor(best.z())) + 1;
        e.radius = best.craterRadius();
        e.height = 120 + r.nextDouble() * 80;
        return e;
    }

    private static boolean eruption(ServerLevel level, HazardEvent e, boolean terrain) {
        if (!e.active()) {
            return true;
        }
        RandomSource r = level.getRandom();
        long time = level.getGameTime();
        // Magma bombs arc out of the crater.
        if (terrain && e.age % 5 == 0) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double off = r.nextDouble() * e.radius * 0.5;
            BlockPos bp = BlockPos.containing(e.x + Math.cos(a) * off, e.y + 3, e.z + Math.sin(a) * off);
            if (level.hasChunkAt(bp) && level.getBlockState(bp).isAir()) {
                double hs = 0.35 + r.nextDouble() * 1.1;
                FallingBlockEntity bomb = FallingBlockEntity.fall(level, bp, Blocks.MAGMA_BLOCK.defaultBlockState());
                bomb.setDeltaMovement(Math.cos(a) * hs, 1.0 + r.nextDouble() * 0.9, Math.sin(a) * hs);
                bomb.setHurtsEntities(2.0F, 30);
                bomb.disableDrop();
            }
        }
        // Fire rains out of the plume onto the flanks.
        if (terrain && e.age % 9 == 0) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double reach = e.radius * 2 + r.nextDouble() * 60;
            Vec3 from = new Vec3(e.x, e.y + 40 + r.nextDouble() * 30, e.z);
            Vec3 to = new Vec3(e.x + Math.cos(a) * reach, e.y - 20, e.z + Math.sin(a) * reach);
            if (level.hasChunkAt(BlockPos.containing(from))) {
                SmallFireball fireball = new SmallFireball(level, from.x, from.y, from.z, to.subtract(from).normalize());
                level.addFreshEntity(fireball);
            }
        }
        // Lava spills over the rim and runs down the mountain, then drains once the source is withdrawn.
        if (terrain && e.age % 80 == 0 && e.pours.size() < 3) {
            double a = r.nextDouble() * Math.PI * 2.0;
            int x = Mth.floor(e.x + Math.cos(a) * (e.radius + 1.5));
            int z = Mth.floor(e.z + Math.sin(a) * (e.radius + 1.5));
            if (level.hasChunkAt(new BlockPos(x, 0, z))) {
                BlockPos bp = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (level.getBlockState(bp).isAir()) {
                    level.setBlock(bp, Blocks.LAVA.defaultBlockState(), 3);
                    e.pours.add(new HazardEvent.Pour(bp, time + 240));
                }
            }
        }
        e.pours.removeIf(pour -> {
            if (pour.removeAt() > time) {
                return false;
            }
            if (level.hasChunkAt(pour.pos()) && level.getFluidState(pour.pos()).isSource()) {
                level.setBlock(pour.pos(), Blocks.AIR.defaultBlockState(), 3);
            }
            return true;
        });
        // Anything near the summit burns; embers reach further out.
        if (e.age % 10 == 0) {
            double burn = e.radius * 2.0 + 6;
            double ember = e.radius * 4.0 + 40;
            AABB box = new AABB(e.x - ember, e.y - 60, e.z - ember, e.x + ember, e.y + 40, e.z + ember);
            for (Entity ent : caught(level, box)) {
                if (!(ent instanceof LivingEntity le)) {
                    continue;
                }
                double d = Math.hypot(ent.getX() - e.x, ent.getZ() - e.z);
                if (d < burn && ent.getY() > e.y - 10) {
                    le.igniteForSeconds(4.0F);
                    le.hurtServer(level, level.damageSources().hotFloor(), 2.0F);
                } else if (d < ember && r.nextInt(6) == 0 && level.canSeeSky(ent.blockPosition())) {
                    le.igniteForSeconds(2.0F);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ meteor showers and lightning storms

    private static HazardEvent startShower(ServerLevel level, ServerPlayer player, PlanetProfile p, Hazard h) {
        if (h == Hazard.LIGHTNING && !p.atmosphere) {
            return null;
        }
        RandomSource r = level.getRandom();
        String name = h == Hazard.METEORS ? "Meteor shower" : "Lightning storm";
        HazardEvent e = create(level, p, h, name, 60, (h == Hazard.METEORS ? 700 : 600) + r.nextInt(600));
        e.x = player.getX();
        e.y = player.getY();
        e.z = player.getZ();
        e.radius = h == Hazard.METEORS ? 100 : 80;
        e.height = 120;
        return e;
    }

    /** Showers and storms drift after the nearest player so they stay overhead. */
    private static ServerPlayer follow(ServerLevel level, HazardEvent e) {
        ServerPlayer target = nearestPlayer(level, e.x, e.z, 300);
        if (target != null) {
            e.x += (target.getX() - e.x) * 0.01;
            e.z += (target.getZ() - e.z) * 0.01;
            e.y = target.getY();
        }
        return target;
    }

    private static boolean meteors(ServerLevel level, HazardEvent e, boolean terrain) {
        RandomSource r = level.getRandom();
        ServerPlayer target = follow(level, e);
        if (e.active() && target != null && e.meteors.size() < 12 && r.nextInt(22) == 0) {
            double tx = target.getX() + (r.nextDouble() * 2 - 1) * 70;
            double tz = target.getZ() + (r.nextDouble() * 2 - 1) * 70;
            if (onPlanet(level, e, tx, tz)) {
                double ty = groundY(level, planetAt(level, tx, tz), tx, tz);
                double a = r.nextDouble() * Math.PI * 2.0;
                double sx = tx - Math.cos(a) * 110;
                double sz = tz - Math.sin(a) * 110;
                double sy = ty + 150;
                double speed = 1.9 + r.nextDouble() * 0.8;
                Vec3 v = new Vec3(tx - sx, ty - sy, tz - sz).normalize().scale(speed);
                float power = r.nextInt(16) == 0 ? 4.5F : 1.5F + r.nextFloat() * 1.5F;
                e.meteors.add(new HazardEvent.Meteor(sx, sy, sz, v.x, v.y, v.z, power));
            }
        }
        Iterator<HazardEvent.Meteor> it = e.meteors.iterator();
        while (it.hasNext()) {
            HazardEvent.Meteor m = it.next();
            m.x += m.vx;
            m.y += m.vy;
            m.z += m.vz;
            BlockPos bp = BlockPos.containing(m.x, m.y, m.z);
            if (m.y < level.getMinY() || !level.hasChunkAt(bp)) {
                it.remove();
                continue;
            }
            BlockState st = level.getBlockState(bp);
            if (!st.isAir() || level.getBlockState(bp.below()).isFaceSturdy(level, bp.below(), net.minecraft.core.Direction.UP)) {
                impact(level, e, m, terrain);
                it.remove();
            }
        }
        return !e.finished() || !e.meteors.isEmpty();
    }

    private static void impact(ServerLevel level, HazardEvent e, HazardEvent.Meteor m, boolean terrain) {
        level.explode(null, m.x, m.y, m.z, m.power, terrain && m.power > 3.0F,
                terrain ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        if (!terrain) {
            return;
        }
        // The rock itself survives: a small, hot, ore-rich core in the new crater.
        PlanetProfile p = planetAt(level, m.x, m.z);
        RandomSource r = level.getRandom();
        BlockState[] core = {ModBlocks.EXOTIC_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(),
                Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                p != null && p.tier >= 3 ? ModBlocks.PLUTONITE_ORE.defaultBlockState() : ModBlocks.LUNAR_TITANIUM_ORE.defaultBlockState()};
        int cx = Mth.floor(m.x);
        int cz = Mth.floor(m.z);
        int cy = Mth.floor(groundY(level, p, m.x, m.z)) - 1;
        int size = m.power > 3.0F ? 2 : 1;
        for (int dx = -size; dx <= size; dx++) {
            for (int dy = -size; dy <= 0; dy++) {
                for (int dz = -size; dz <= size; dz++) {
                    BlockPos bp = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState st = level.getBlockState(bp);
                    if (!st.isAir() && st.getFluidState().isEmpty() && !st.is(Blocks.BEDROCK) && r.nextInt(3) != 0) {
                        level.setBlock(bp, core[r.nextInt(core.length)], 3);
                    }
                }
            }
        }
    }

    private static boolean lightning(ServerLevel level, HazardEvent e) {
        RandomSource r = level.getRandom();
        ServerPlayer target = follow(level, e);
        if (e.active() && target != null && r.nextInt(16) == 0) {
            double dist = r.nextInt(12) == 0 ? 3 + r.nextDouble() * 5 : 10 + r.nextDouble() * 60;
            double a = r.nextDouble() * Math.PI * 2.0;
            double x = target.getX() + Math.cos(a) * dist;
            double z = target.getZ() + Math.sin(a) * dist;
            BlockPos probe = BlockPos.containing(x, 0, z);
            if (level.hasChunkAt(probe) && onPlanet(level, e, x, z)) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, probe.getX(), probe.getZ());
                LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
                bolt.setPos(probe.getX() + 0.5, y, probe.getZ() + 0.5);
                level.addFreshEntity(bolt);
            }
        }
        return true;
    }
}
