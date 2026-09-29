package com.galaxymc.ship;

import com.galaxymc.GalaxyMC;
import com.galaxymc.block.entity.NavigationConsoleBlockEntity;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.galaxy.SolarSystem;
import com.galaxymc.network.WarpPayload;
import com.galaxymc.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flies ships. A launch is a short countdown (engines spool up, the hull shakes, everyone aboard sees
 * the jump coming) followed by the jump itself: every block of the ship's volume - hull, interior,
 * whatever the crew built or stored inside - is lifted out of the world together with its block
 * entities and everyone standing in it, and set down on the destination at a flat, dry landing site.
 *
 * <p>Nothing about the ship is special-cased: chests keep their contents, furnaces keep burning, the
 * console keeps its fuel and its memory of every landing site it has used.
 */
public final class LaunchControl {
    private LaunchControl() {}

    public static final int COUNTDOWN = 100;
    public static final int WARP_TICKS = 50;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final class Pending {
        final ServerLevel level;
        final BlockPos console;
        final String destination;
        final double cost;
        final UUID pilot;
        int ticks;

        Pending(ServerLevel level, BlockPos console, String destination, double cost, UUID pilot) {
            this.level = level;
            this.console = console;
            this.destination = destination;
            this.cost = cost;
            this.pilot = pilot;
        }
    }

    private static final Map<Long, Pending> PENDING = new HashMap<>();

    /** Called from the launch packet. Validates everything the client showed and starts the countdown. */
    public static void requestLaunch(ServerPlayer player, BlockPos consolePos, String destination) {
        ServerLevel level = player.level();
        if (player.distanceToSqr(Vec3.atCenterOf(consolePos)) > 64.0) {
            return;
        }
        if (!(level.getBlockEntity(consolePos) instanceof NavigationConsoleBlockEntity console) || !console.isShip()) {
            return;
        }
        if (console.launching() || PENDING.containsKey(consolePos.asLong())) {
            return;
        }
        long seed = GalaxyMC.galaxySeed();
        Travel.Location from = Travel.locate(seed, level.dimension(), player.getX(), player.getZ());
        if (destination.equals(from.bodyId())) {
            fail(player, "message.galaxy_mc.launch.already_here");
            return;
        }
        double cost = Travel.costTo(seed, from, destination);
        if (cost < 0) {
            fail(player, "message.galaxy_mc.launch.unknown");
            return;
        }
        boolean interstellar = !isSameSystem(seed, from, destination);
        if (interstellar && !console.shipType().interstellar) {
            fail(player, "message.galaxy_mc.launch.rocket_range");
            return;
        }
        if (!player.hasInfiniteMaterials() && console.fuel() < cost) {
            player.sendOverlayMessage(Component.translatable("message.galaxy_mc.launch.no_fuel", ShipFuel.format(cost),
                    ShipFuel.format(console.fuel())).withStyle(ChatFormatting.RED));
            return;
        }
        if (Landing.target(level.getServer(), destination) == null) {
            fail(player, "message.galaxy_mc.launch.unknown");
            return;
        }
        console.setLaunching(true);
        PENDING.put(consolePos.asLong(), new Pending(level, consolePos.immutable(), destination,
                player.hasInfiniteMaterials() ? 0 : cost, player.getUUID()));
        level.playSound(null, consolePos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5F, 0.6F);
    }

    private static boolean isSameSystem(long seed, Travel.Location from, String destination) {
        if (destination.startsWith("f:")) {
            return from.bodyId().startsWith("f:") && destination.substring(0, destination.lastIndexOf(':'))
                    .equals(from.bodyId().substring(0, from.bodyId().lastIndexOf(':')));
        }
        return from.star().sol() && SolarSystem.body(destination) != null;
    }

    private static void fail(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    public static void clear() {
        PENDING.clear();
    }

    // ------------------------------------------------------------------ countdown

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.values().iterator();
        List<Pending> ready = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (!(p.level.getBlockEntity(p.console) instanceof NavigationConsoleBlockEntity console) || !console.isShip()) {
                it.remove();
                continue;
            }
            p.ticks++;
            ShipBlueprint blueprint = ShipBlueprint.get(console.shipType());
            countdownEffects(p, console, blueprint);
            if (p.ticks >= COUNTDOWN) {
                it.remove();
                ready.add(p);
            }
        }
        for (Pending p : ready) {
            try {
                jump(server, p);
            } catch (Exception e) {
                GalaxyMC.LOG.error("Ship jump failed", e);
                if (p.level.getBlockEntity(p.console) instanceof NavigationConsoleBlockEntity console) {
                    console.setLaunching(false);
                }
            }
        }
    }

    private static void countdownEffects(Pending p, NavigationConsoleBlockEntity console, ShipBlueprint blueprint) {
        ServerLevel level = p.level;
        BlockPos anchor = console.anchor();
        int remaining = (COUNTDOWN - p.ticks) / 20;
        List<ServerPlayer> crew = crew(level, blueprint, anchor, console);
        if (p.ticks % 20 == 0) {
            for (ServerPlayer player : crew) {
                player.sendOverlayMessage(Component.translatable("message.galaxy_mc.launch.countdown", remaining + 1)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            level.playSound(null, p.console, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.0F, 0.5F + p.ticks / 200.0F);
        }
        if (p.ticks == COUNTDOWN - WARP_TICKS) {
            String name = destinationName(p.destination);
            boolean interstellar = p.destination.startsWith("f:");
            for (ServerPlayer player : crew) {
                ServerPlayNetworking.send(player, new WarpPayload(WARP_TICKS + 30, name, interstellar));
            }
        }
        // Thrusters roar harder as the countdown runs down.
        double intensity = p.ticks / (double) COUNTDOWN;
        for (ShipBlueprint.Cell cell : blueprint.blocks) {
            if (cell.state().getBlock() != ModBlocks.THRUSTER) {
                continue;
            }
            BlockPos pos = ShipBlueprint.place(anchor, cell.pos(), console.rotation());
            if (level.random.nextDouble() < 0.35 + intensity) {
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() - 0.2, pos.getZ() + 0.5,
                        2 + (int) (intensity * 6), 0.25, 0.1, 0.25, 0.02 + intensity * 0.08);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5,
                        1, 0.6, 0.1, 0.6, 0.01);
            }
        }
        if (p.ticks % 10 == 0) {
            level.playSound(null, p.console, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F + (float) intensity, 0.5F);
        }
    }

    private static String destinationName(String id) {
        var profile = Planets.byId(GalaxyMC.galaxySeed(), id);
        if (profile != null) {
            return profile.name;
        }
        SolarSystem.Body body = SolarSystem.body(id);
        return body != null ? body.name() : id;
    }

    /** Players standing inside the ship's volume. */
    private static List<ServerPlayer> crew(ServerLevel level, ShipBlueprint blueprint, BlockPos anchor, NavigationConsoleBlockEntity console) {
        Set<Long> cells = volumeSet(blueprint, anchor, console);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (cells.contains(player.blockPosition().asLong()) || cells.contains(player.blockPosition().above().asLong())) {
                out.add(player);
            }
        }
        return out;
    }

    private static Set<Long> volumeSet(ShipBlueprint blueprint, BlockPos anchor, NavigationConsoleBlockEntity console) {
        Set<Long> cells = new HashSet<>();
        for (BlockPos pos : blueprint.volume(anchor, console.rotation())) {
            cells.add(pos.asLong());
        }
        return cells;
    }

    // ------------------------------------------------------------------ the jump

    private record Snapshot(BlockPos offset, BlockState state, CompoundTag blockEntity) {}

    private static void jump(MinecraftServer server, Pending p) {
        ServerLevel from = p.level;
        NavigationConsoleBlockEntity console = (NavigationConsoleBlockEntity) from.getBlockEntity(p.console);
        ShipBlueprint blueprint = ShipBlueprint.get(console.shipType());
        BlockPos oldAnchor = console.anchor();
        String originId = Planets.idAt(from.dimension(), GalaxyMC.galaxySeed(), oldAnchor.getX(), oldAnchor.getZ());

        Landing.Target target = Landing.target(server, p.destination);
        if (target == null) {
            console.setLaunching(false);
            return;
        }
        ServerLevel to = target.level();
        BlockPos remembered = console.landing(p.destination);
        BlockPos newAnchor = remembered != null ? Landing.findSpot(to, remembered.getX(), remembered.getZ(), blueprint.radius + 1)
                : Landing.findSpot(to, target.x(), target.z(), blueprint.radius + 1);
        if (from == to && newAnchor.closerThan(oldAnchor, blueprint.radius * 2.0 + 4)) {
            newAnchor = newAnchor.offset(blueprint.radius * 3 + 8, 0, 0);
            newAnchor = Landing.findSpot(to, newAnchor.getX(), newAnchor.getZ(), blueprint.radius + 1);
        }

        // 1. Snapshot every block of the ship's volume, top to bottom so attached blocks go last.
        List<BlockPos> volume = blueprint.volume(oldAnchor, console.rotation());
        volume.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        List<Snapshot> snapshot = new ArrayList<>(volume.size());
        for (BlockPos pos : volume) {
            BlockState state = from.getBlockState(pos);
            CompoundTag tag = null;
            BlockEntity be = from.getBlockEntity(pos);
            if (be != null) {
                tag = be.saveWithFullMetadata(from.registryAccess());
            }
            snapshot.add(new Snapshot(pos.subtract(oldAnchor), state, tag));
        }

        // 2. Passengers: anything whose feet are inside the volume.
        Set<Long> cells = volumeSet(blueprint, oldAnchor, console);
        AABB box = new AABB(oldAnchor).inflate(blueprint.radius + 1, 0, blueprint.radius + 1).expandTowards(0, blueprint.height + 2, 0);
        List<Entity> passengers = new ArrayList<>();
        for (Entity e : from.getEntities((Entity) null, box, e -> !e.isPassenger())) {
            if (cells.contains(e.blockPosition().asLong())) {
                passengers.add(e);
            }
        }

        // 3. Make room at the destination and load its chunks.
        int r = blueprint.radius + 2;
        for (int cx = (newAnchor.getX() - r) >> 4; cx <= (newAnchor.getX() + r) >> 4; cx++) {
            for (int cz = (newAnchor.getZ() - r) >> 4; cz <= (newAnchor.getZ() + r) >> 4; cz++) {
                to.getChunk(cx, cz);
            }
        }

        // 4. Lift the ship out of the source world. Containers are emptied first so nothing spills.
        for (Snapshot s : snapshot) {
            BlockPos pos = oldAnchor.offset(s.offset());
            BlockEntity be = from.getBlockEntity(pos);
            if (be != null) {
                Clearable.tryClear(be);
            }
            from.setBlock(pos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
        }

        // 5. Set it down, bottom to top, then restore block entities.
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            Snapshot s = snapshot.get(i);
            to.setBlock(newAnchor.offset(s.offset()), s.state(), PLACE_FLAGS);
        }
        for (Snapshot s : snapshot) {
            if (s.blockEntity() == null) {
                continue;
            }
            BlockPos pos = newAnchor.offset(s.offset());
            BlockEntity be = to.getBlockEntity(pos);
            if (be != null) {
                be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, to.registryAccess(), s.blockEntity()));
                be.setChanged();
            }
        }

        // 6. Move the crew and cargo, keeping everyone exactly where they stood inside the hull.
        for (Entity e : passengers) {
            Vec3 rel = e.position().subtract(Vec3.atLowerCornerOf(oldAnchor));
            Vec3 dest = Vec3.atLowerCornerOf(newAnchor).add(rel).add(0, 0.05, 0);
            e.teleport(new TeleportTransition(to, dest, Vec3.ZERO, e.getYRot(), e.getXRot(), TeleportTransition.DO_NOTHING));
        }

        // 7. Update the console at its new home.
        BlockPos newConsole = newAnchor.offset(p.console.subtract(oldAnchor));
        if (to.getBlockEntity(newConsole) instanceof NavigationConsoleBlockEntity moved) {
            moved.moved(newAnchor);
            moved.spendFuel(p.cost);
            moved.rememberLanding(p.destination, newAnchor);
            if (!originId.equals(SolarSystem.EARTH) || from.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                moved.rememberLanding(originId, oldAnchor);
            }
        }
        to.playSound(null, newAnchor, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.6F);
        to.sendParticles(ParticleTypes.CLOUD, newAnchor.getX() + 0.5, newAnchor.getY(), newAnchor.getZ() + 0.5, 60,
                blueprint.radius, 0.5, blueprint.radius, 0.05);
        String name = destinationName(p.destination);
        for (Entity e : passengers) {
            if (e instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("message.galaxy_mc.launch.arrived", name).withStyle(ChatFormatting.AQUA));
            }
        }
        GalaxyMC.LOG.info("Ship jumped {} -> {} ({} blocks, {} passengers)", originId, p.destination, snapshot.size(), passengers.size());
    }
}
