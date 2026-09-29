package com.galaxymc.event;

import com.galaxymc.GalaxyMC;
import com.galaxymc.block.ExoticOreBlock;
import com.galaxymc.climate.ClimateTicker;
import com.galaxymc.climate.LifeSupportRegistry;
import com.galaxymc.entity.FaunaSpawner;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.mineral.MineralData;
import com.galaxymc.mineral.Minerals;
import com.galaxymc.ship.LaunchControl;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Server-side event wiring: the per-tick systems and the few vanilla hooks Galaxy MC needs. */
public final class ServerEvents {
    private ServerEvents() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ClimateTicker.tick(server);
            LaunchControl.tick(server);
            FaunaSpawner.tick(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> ClimateTicker.remove(listener.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LifeSupportRegistry.clear();
            LaunchControl.clear();
        });
        PlayerBlockBreakEvents.AFTER.register(ServerEvents::afterBreak);
    }

    /** Exotic ore yields whatever mineral the planet it sits on makes in that slot. */
    private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel server) || !(state.getBlock() instanceof ExoticOreBlock) || player.isCreative()) {
            return;
        }
        if (!player.hasCorrectToolForDrops(state)) {
            return;
        }
        PlanetProfile p = Planets.at(server.dimension(), GalaxyMC.galaxySeed(), pos.getX(), pos.getZ());
        if (p == null) {
            return;
        }
        MineralData mineral = Minerals.forPlanet(p, state.getValue(ExoticOreBlock.SLOT));
        int count = 1 + server.getRandom().nextInt(1 + Math.min(4, p.tier / 2));
        Block.popResource(server, pos, Minerals.stack(mineral, count));
        ExperienceOrb.award(server, Vec3.atCenterOf(pos), 2 + p.tier + server.getRandom().nextInt(3));
    }
}
