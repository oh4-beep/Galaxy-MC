package com.galaxymc.galaxy;

import com.galaxymc.world.ModDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Single lookup point for "which world am I on?", used identically by server logic, world-gen threads
 * and the client (block tints, HUD, navigation). The galaxy seed is the world seed on the server and
 * the synced copy on the client.
 */
public final class Planets {
    private Planets() {}

    /** Profile for a planet id (Sol body id or frontier id). Null for Earth or unknown ids. */
    public static PlanetProfile byId(long galaxySeed, String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("f:")) {
            FrontierMap.Cell c = FrontierMap.parse(id);
            if (c == null) {
                return null;
            }
            return FrontierPlanets.planet(galaxySeed, Galaxy.star(galaxySeed, c.sx(), c.sz(), c.slot()), c.planet());
        }
        return SolarSystem.profile(id);
    }

    /** Profile of the world at a position in a dimension. Null in vanilla dimensions or deep space. */
    public static PlanetProfile at(ResourceKey<Level> dim, long galaxySeed, double x, double z) {
        if (dim.equals(ModDimensions.FRONTIER)) {
            return FrontierMap.planetAt(galaxySeed, x, z);
        }
        SolarSystem.Body body = SolarSystem.byDimension(dim);
        if (body == null || body.id().equals(SolarSystem.EARTH)) {
            return null;
        }
        return SolarSystem.profile(body.id());
    }

    /** The star system a position belongs to (Sol for every Sol body and for vanilla dimensions). */
    public static Star systemAt(ResourceKey<Level> dim, long galaxySeed, double x, double z) {
        if (dim.equals(ModDimensions.FRONTIER)) {
            FrontierMap.Cell c = FrontierMap.cellAt(x, z);
            if (c != null) {
                Star s = Galaxy.star(galaxySeed, c.sx(), c.sz(), c.slot());
                if (s != null) {
                    return s;
                }
            }
        }
        return Galaxy.sol(galaxySeed);
    }

    /** Stable id of the planet at a position, or "earth" for vanilla worlds. */
    public static String idAt(ResourceKey<Level> dim, long galaxySeed, double x, double z) {
        PlanetProfile p = at(dim, galaxySeed, x, z);
        if (p != null) {
            return p.id;
        }
        return SolarSystem.EARTH;
    }
}
