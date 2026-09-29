package com.galaxymc.client;

import com.galaxymc.network.ClimatePayload;

/**
 * What the client knows about the universe: the galaxy seed (so it can name stars, tint alien terrain
 * and plan routes locally), the latest climate reading for the HUD, and the state of any warp effect.
 */
public final class ClientState {
    private ClientState() {}

    public static volatile long galaxySeed = 0x6A1AC7L;
    public static volatile boolean seedKnown;
    public static volatile ClimatePayload climate = ClimatePayload.INACTIVE;
    public static volatile long climateTime;

    public static int warpTicks;
    public static int warpTotal;
    public static String warpDestination = "";
    public static boolean warpInterstellar;

    public static void startWarp(int ticks, String destination, boolean interstellar) {
        warpTicks = ticks;
        warpTotal = ticks;
        warpDestination = destination;
        warpInterstellar = interstellar;
    }

    public static void tick() {
        if (warpTicks > 0) {
            warpTicks--;
        }
    }
}
