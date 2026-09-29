package com.galaxymc.network;

import com.galaxymc.GalaxyMC;
import com.galaxymc.ship.LaunchControl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Payload registration and server-side receivers. */
public final class ModNetwork {
    private ModNetwork() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(GalaxySeedPayload.TYPE, GalaxySeedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClimatePayload.TYPE, ClimatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WarpPayload.TYPE, WarpPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LaunchPayload.TYPE, LaunchPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(LaunchPayload.TYPE,
                (payload, ctx) -> LaunchControl.requestLaunch(ctx.player(), payload.console(), payload.destination()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(new GalaxySeedPayload(GalaxyMC.galaxySeed())));
    }
}
