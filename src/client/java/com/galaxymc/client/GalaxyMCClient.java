package com.galaxymc.client;

import com.galaxymc.GalaxyMC;
import com.galaxymc.client.color.PlanetTints;
import com.galaxymc.client.hud.GalaxyHud;
import com.galaxymc.client.render.CreatureModelData;
import com.galaxymc.client.render.CreatureRenderer;
import com.galaxymc.client.render.WormSegmentRenderer;
import com.galaxymc.client.screen.NavigationScreen;
import com.galaxymc.client.screen.StellarForgeScreen;
import com.galaxymc.client.screen.ThermalArmorScreen;
import com.galaxymc.entity.AlienMob;
import com.galaxymc.entity.Species;
import com.galaxymc.entity.SpeciesRegistry;
import com.galaxymc.network.ClimatePayload;
import com.galaxymc.network.GalaxySeedPayload;
import com.galaxymc.network.WarpPayload;
import com.galaxymc.registry.ModEntities;
import com.galaxymc.registry.ModMenus;
import com.galaxymc.registry.Reg;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.EntityType;

/** Client entry point: networking receivers, screens, HUD, block tints and the creature renderers. */
public class GalaxyMCClient implements ClientModInitializer {
    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(GalaxySeedPayload.TYPE, (payload, context) -> {
            ClientState.galaxySeed = payload.seed();
            ClientState.seedKnown = true;
            GalaxyMC.setGalaxySeed(payload.seed());
        });
        ClientPlayNetworking.registerGlobalReceiver(ClimatePayload.TYPE, (payload, context) -> {
            ClientState.climate = payload;
            ClientState.climateTime = System.currentTimeMillis();
        });
        ClientPlayNetworking.registerGlobalReceiver(WarpPayload.TYPE, (payload, context) ->
                ClientState.startWarp(payload.ticks(), payload.destination(), payload.interstellar()));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            ClientState.climate = ClimatePayload.INACTIVE;
            ClientState.warpTicks = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientState.tick());

        MenuScreens.register(ModMenus.NAVIGATION, NavigationScreen::new);
        MenuScreens.register(ModMenus.THERMAL_ARMOR, ThermalArmorScreen::new);
        MenuScreens.register(ModMenus.STELLAR_FORGE, StellarForgeScreen::new);

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Reg.id("climate_vignette"), GalaxyHud::vignette);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Reg.id("climate_panel"), GalaxyHud::climate);
        HudElementRegistry.addLast(Reg.id("warp"), GalaxyHud::warp);

        PlanetTints.init();

        for (Species s : SpeciesRegistry.all()) {
            ModelLayerRegistry.registerModelLayer(CreatureRenderer.layer(s.id), () -> CreatureModelData.load(s.id).layer());
            if (s.kind == Species.Kind.WORM) {
                String seg = s.id + "_segment";
                ModelLayerRegistry.registerModelLayer(CreatureRenderer.layer(seg), () -> CreatureModelData.load(seg).layer());
            }
            EntityType<? extends AlienMob> type = (EntityType<? extends AlienMob>) SpeciesRegistry.typeOf(s);
            if (type != null) {
                EntityRendererRegistry.register(type, ctx -> new CreatureRenderer(ctx, s));
            }
        }
        EntityRendererRegistry.register(ModEntities.WORM_SEGMENT, WormSegmentRenderer::new);
        GalaxyMC.LOG.info("Galaxy MC client ready: {} creature renderers", SpeciesRegistry.all().size());
    }
}
