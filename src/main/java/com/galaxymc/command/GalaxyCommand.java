package com.galaxymc.command;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.FrontierPlanets;
import com.galaxymc.galaxy.Galaxy;
import com.galaxymc.galaxy.Hazard;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.galaxy.SolarSystem;
import com.galaxymc.galaxy.Star;
import com.galaxymc.hazard.HazardManager;
import com.galaxymc.ship.Landing;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /galaxy} - operator tools for exploring and debugging the universe.
 * <ul>
 *   <li>{@code /galaxy tp <body>} - jump to a Sol body.</li>
 *   <li>{@code /galaxy tp frontier <sx> <sz> <slot> <orbit>} - jump to any procedural world.</li>
 *   <li>{@code /galaxy random} - jump to a random world near Sol.</li>
 *   <li>{@code /galaxy info} - describe the current world.</li>
 *   <li>{@code /galaxy nearby} - list star systems around the current one.</li>
 *   <li>{@code /galaxy hazard <type>} - set off a tornado, tsunami, eruption, meteor shower or lightning
 *   storm near you (it still needs the right ground: a sea for tsunamis, a volcano for eruptions).</li>
 * </ul>
 */
public final class GalaxyCommand {
    private GalaxyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("galaxy")
                .then(Commands.literal("info").executes(GalaxyCommand::info))
                .then(Commands.literal("nearby").executes(GalaxyCommand::nearby))
                .then(Commands.literal("tp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("frontier")
                                .then(Commands.argument("sx", IntegerArgumentType.integer(0, Galaxy.SECTORS - 1))
                                        .then(Commands.argument("sz", IntegerArgumentType.integer(0, Galaxy.SECTORS - 1))
                                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, Galaxy.SLOTS_PER_SECTOR - 1))
                                                        .then(Commands.argument("orbit", IntegerArgumentType.integer(0, 7))
                                                                .executes(ctx -> tp(ctx, "f:" + IntegerArgumentType.getInteger(ctx, "sx") + ":"
                                                                        + IntegerArgumentType.getInteger(ctx, "sz") + ":"
                                                                        + IntegerArgumentType.getInteger(ctx, "slot") + ":"
                                                                        + IntegerArgumentType.getInteger(ctx, "orbit"))))))))
                        .then(Commands.argument("body", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(SolarSystem.ids(), b))
                                .executes(ctx -> tp(ctx, StringArgumentType.getString(ctx, "body")))))
                .then(Commands.literal("fauna").executes(GalaxyCommand::fauna))
                .then(Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        com.galaxymc.entity.SpeciesRegistry.all().stream().map(sp -> sp.id).toList(), b))
                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "species")))))
                .then(Commands.literal("random").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(GalaxyCommand::random))
                .then(Commands.literal("hazard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Hazard.values())
                                        .map(h -> h.name().toLowerCase(java.util.Locale.ROOT)).toList(), b))
                                .executes(ctx -> hazard(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("previewall").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(GalaxyCommand::previewAll))
                .then(Commands.literal("preview").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 4096))
                                .executes(ctx -> preview(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int tp(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!id.startsWith("f:") && SolarSystem.body(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown destination: " + id));
            return 0;
        }
        if (id.startsWith("f:") && Planets.byId(GalaxyMC.galaxySeed(), id) == null) {
            ctx.getSource().sendFailure(Component.literal("Nothing orbits there: " + id));
            return 0;
        }
        Landing.Target target = Landing.target(ctx.getSource().getServer(), id);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("That dimension is not loaded."));
            return 0;
        }
        BlockPos spot = Landing.findSpot(target.level(), target.x(), target.z(), 3);
        player.teleport(new TeleportTransition(target.level(), Vec3.atBottomCenterOf(spot), Vec3.ZERO, player.getYRot(), player.getXRot(),
                TeleportTransition.DO_NOTHING));
        PlanetProfile p = Planets.byId(GalaxyMC.galaxySeed(), id);
        String name = p != null ? p.name : SolarSystem.body(id).name();
        ctx.getSource().sendSuccess(() -> Component.literal("Arrived at " + name).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, int radius) {
        Vec3 at = ctx.getSource().getPosition();
        try {
            java.nio.file.Path out = TerrainPreview.render(ctx.getSource().getLevel(), (int) at.x, (int) at.z, radius, 512);
            ctx.getSource().sendSuccess(() -> Component.literal("Preview written to " + out), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Preview failed: " + e.getMessage()));
            return 0;
        }
    }

    /** Renders every Sol body plus one frontier world of each archetype found near Sol. */
    private static int previewAll(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        long seed = GalaxyMC.galaxySeed();
        int done = 0;
        try {
            for (SolarSystem.Body b : SolarSystem.BODIES.values()) {
                if (b.id().equals(SolarSystem.EARTH)) {
                    continue;
                }
                net.minecraft.server.level.ServerLevel level = server.getLevel(b.dimension());
                if (level != null) {
                    TerrainPreview.render(level, 0, 0, 384, 384);
                    done++;
                }
            }
            net.minecraft.server.level.ServerLevel frontier = server.getLevel(com.galaxymc.world.ModDimensions.FRONTIER);
            java.util.EnumSet<com.galaxymc.galaxy.PlanetType> seen = java.util.EnumSet.noneOf(com.galaxymc.galaxy.PlanetType.class);
            for (Star s : Galaxy.starsAround(seed, Galaxy.SOL_SX, Galaxy.SOL_SZ, 12)) {
                for (int orbit = 0; orbit <= s.planetCount(); orbit++) {
                    PlanetProfile p = FrontierPlanets.planet(seed, s, orbit);
                    if (p == null || !seen.add(p.type)) {
                        continue;
                    }
                    int cx = com.galaxymc.galaxy.FrontierMap.centerX(s.sx(), s.slot());
                    int cz = com.galaxymc.galaxy.FrontierMap.centerZ(s.sz(), orbit);
                    java.nio.file.Path out = TerrainPreview.render(frontier, cx, cz, p.radius + 64, 512);
                    GalaxyMC.LOG.info("Preview {} ({}, {}) -> {}", p.name, p.type, p.id, out.getFileName());
                    done++;
                }
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Preview failed: " + e));
            return 0;
        }
        int total = done;
        ctx.getSource().sendSuccess(() -> Component.literal("Rendered " + total + " previews"), false);
        return total;
    }

    private static int random(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long seed = GalaxyMC.galaxySeed();
        java.util.Random r = new java.util.Random();
        for (int tries = 0; tries < 500; tries++) {
            int sx = Galaxy.SOL_SX + r.nextInt(41) - 20;
            int sz = Galaxy.SOL_SZ + r.nextInt(41) - 20;
            List<Star> stars = Galaxy.starsInSector(seed, sx, sz);
            if (stars.isEmpty()) {
                continue;
            }
            Star s = stars.get(r.nextInt(stars.size()));
            if (s.sol() || s.planetCount() == 0) {
                continue;
            }
            int orbit = 1 + r.nextInt(s.planetCount());
            if (FrontierPlanets.planet(seed, s, orbit) != null) {
                return tp(ctx, FrontierPlanets.id(s, orbit));
            }
        }
        ctx.getSource().sendFailure(Component.literal("No world found."));
        return 0;
    }

    private static int hazard(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Hazard h;
        try {
            h = Hazard.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Unknown hazard: " + name));
            return 0;
        }
        PlanetProfile p = Planets.at(player.level().dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal("Hazards only happen on other worlds."));
            return 0;
        }
        if (HazardManager.start(player.level(), player, p, h) == null) {
            ctx.getSource().sendFailure(Component.literal(switch (h) {
                case TSUNAMIS -> "No sea near enough to raise a tsunami.";
                case ERUPTIONS -> "No volcano within 500 blocks.";
                case TORNADOES -> "No open ground here for a tornado (or no air).";
                default -> "This world cannot have that.";
            }));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Started: " + h.displayName).withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int fauna(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlanetProfile p = Planets.at(player.level().dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal("Earth's wildlife is on its own."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Life on " + p.name + ":").withStyle(ChatFormatting.GOLD), false);
        for (com.galaxymc.entity.FaunaSpawner.Entry e : com.galaxymc.entity.FaunaSpawner.fauna(p)) {
            com.galaxymc.entity.Species sp = e.species();
            com.galaxymc.entity.Strain strain = com.galaxymc.entity.Strain.of(p, sp);
            ChatFormatting colour = sp.hostile() ? ChatFormatting.RED : sp.passive() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(" %s %s%s  (size x%.2f, might x%.2f)", strain.name(), sp.name,
                    sp.giant ? " [GIANT]" : "", strain.size() * sp.scale, strain.might())).withStyle(colour), false);
        }
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.galaxymc.entity.Species sp = com.galaxymc.entity.SpeciesRegistry.byId(id);
        if (sp == null) {
            ctx.getSource().sendFailure(Component.literal("No such species: " + id));
            return 0;
        }
        PlanetProfile p = Planets.at(player.level().dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());
        Vec3 look = player.getLookAngle();
        int x = (int) Math.floor(player.getX() + look.x * (4 + sp.width * sp.scale));
        int z = (int) Math.floor(player.getZ() + look.z * (4 + sp.width * sp.scale));
        var mob = com.galaxymc.entity.FaunaSpawner.spawnOne(player.level(), sp, p, x, z);
        if (mob == null) {
            ctx.getSource().sendFailure(Component.literal(sp.name + " cannot live there."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Summoned ").append(mob.getDisplayName()), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long seed = GalaxyMC.galaxySeed();
        PlanetProfile p = Planets.at(player.level().dimension(), seed, player.getX(), player.getZ());
        Star star = Planets.systemAt(player.level().dimension(), seed, player.getX(), player.getZ());
        if (p == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("You are on Earth, in the " + star.name() + " system ("
                    + star.designation() + ").").withStyle(ChatFormatting.AQUA), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(p.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("  " + p.type.displayName + " - " + star.name() + " (" + star.starClass().displayName + ")")
                        .withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(p.description).withStyle(ChatFormatting.ITALIC), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Temp %d°C (±%d)  Gravity %.2fg  Tier %d  Danger %d/10  ID %s",
                Math.round(p.baseTemp), Math.round(p.tempSwing), p.gravity, p.tier, p.danger, p.id)).withStyle(ChatFormatting.AQUA), false);
        if (!p.hazards.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Hazards: " + String.join(", ",
                    p.hazards.stream().map(h -> h.displayName).toList())).withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int nearby(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long seed = GalaxyMC.galaxySeed();
        Star here = Planets.systemAt(player.level().dimension(), seed, player.getX(), player.getZ());
        List<Star> stars = Galaxy.starsAround(seed, here.sx(), here.sz(), 3);
        stars.sort((a, b) -> Double.compare(a.distanceTo(here), b.distanceTo(here)));
        int shown = 0;
        for (Star s : stars) {
            if (shown++ >= 12) {
                break;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(String.format("%-22s %-22s %5.1f ly  %d planets  %s", s.name(),
                    s.starClass().displayName, s.distanceTo(here), s.planetCount(), s.designation())), false);
        }
        return 1;
    }
}
