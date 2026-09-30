package com.galaxymc;

import com.galaxymc.command.GalaxyCommand;
import com.galaxymc.event.ServerEvents;
import com.galaxymc.network.ModNetwork;
import com.galaxymc.registry.ModBlockEntities;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModComponents;
import com.galaxymc.registry.ModCreativeTab;
import com.galaxymc.registry.ModEntities;
import com.galaxymc.registry.ModGameRules;
import com.galaxymc.registry.ModItems;
import com.galaxymc.registry.ModMenus;
import com.galaxymc.registry.ModWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Galaxy MC: build a rocket, leave Earth, and keep going. Entry point that wires every subsystem.
 */
public class GalaxyMC implements ModInitializer {
    public static final String MOD_ID = "galaxy_mc";
    public static final Logger LOG = LoggerFactory.getLogger("Galaxy MC");

    /** Server-side galaxy seed (the world seed). Set before any level loads. */
    private static volatile long galaxySeed = 0x6A1AC7L;

    public static long galaxySeed() {
        return galaxySeed;
    }

    public static void setGalaxySeed(long seed) {
        galaxySeed = seed;
    }

    @Override
    public void onInitialize() {
        // Order matters: components before items that use them, blocks before block entities and items.
        ModComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModEntities.init();
        ModWorldgen.init();
        ModGameRules.init();
        ModCreativeTab.init();
        ModNetwork.init();
        ServerEvents.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            setGalaxySeed(server.getWorldGenSettings().options().seed());
            LOG.info("Galaxy seed set to {}", galaxySeed);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GalaxyCommand.register(dispatcher));
        LOG.info("Galaxy MC initialised - the universe is open.");
    }
}
