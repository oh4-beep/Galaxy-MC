package com.galaxymc.ship;

import com.galaxymc.galaxy.FrontierMap;
import com.galaxymc.galaxy.FrontierPlanets;
import com.galaxymc.galaxy.Galaxy;
import com.galaxymc.galaxy.Hazard;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.galaxy.SolarSystem;
import com.galaxymc.galaxy.Star;
import com.galaxymc.galaxy.StarClass;
import com.galaxymc.world.ModDimensions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Route planning shared by the navigation screen (client) and the launch check (server), so the fuel
 * figure a player sees is exactly what the server will charge.
 *
 * <p>Costs, in coal: hopping between bodies of one system is 16 + 24 * |delta AU|^0.9 plus the
 * destination's gravity well; moons of the same planet are a flat 16. Leaving the system costs 256 plus
 * 48 per light year, plus the approach to the destination orbit.
 */
public final class Travel {
    private Travel() {}

    public static final double JUMP_BASE = 256.0;
    public static final double PER_LIGHT_YEAR = 48.0;

    /** Where a ship is: its star, which orbit (Sol body id or frontier orbit index) and that orbit's AU. */
    public record Location(Star star, String bodyId, double au, String family) {}

    public record Destination(String id, String name, Star star, PlanetType type, double temp, double swing, double gravity,
                              int danger, int tier, double au, double distanceLy, double fuel, boolean sameSystem,
                              String description, Set<Hazard> hazards) {
        public boolean interstellar() {
            return !sameSystem;
        }

        public String typeName() {
            return type == null ? "Home World" : type.displayName;
        }
    }

    public static Location locate(long seed, ResourceKey<Level> dim, double x, double z) {
        if (dim.equals(ModDimensions.FRONTIER)) {
            FrontierMap.Cell c = FrontierMap.cellAt(x, z);
            if (c != null) {
                Star s = Galaxy.star(seed, c.sx(), c.sz(), c.slot());
                if (s != null) {
                    PlanetProfile p = FrontierPlanets.planet(seed, s, c.planet());
                    double au = p == null ? 0 : p.orbitAu;
                    String id = FrontierPlanets.id(s, c.planet());
                    return new Location(s, id, au, id);
                }
            }
            Star sol = Galaxy.sol(seed);
            return new Location(sol, SolarSystem.EARTH, 1.0, SolarSystem.EARTH);
        }
        SolarSystem.Body body = SolarSystem.byDimension(dim);
        if (body == null) {
            body = SolarSystem.body(SolarSystem.EARTH);
        }
        return new Location(Galaxy.sol(seed), body.id(), body.orbitAu(), family(body));
    }

    private static String family(SolarSystem.Body b) {
        if (b.parent() != null) {
            return b.parent();
        }
        return b.id();
    }

    private static double well(PlanetProfile p) {
        if (p == null) {
            return 0;
        }
        return switch (p.type) {
            case STELLAR -> 400 + p.baseTemp / 20.0;
            case GAS_GIANT -> 60;
            default -> Math.max(0, (p.gravity - 0.5) * 16.0);
        };
    }

    private static double hop(double fromAu, double toAu) {
        return 16.0 + 24.0 * Math.pow(Math.abs(fromAu - toAu), 0.9);
    }

    /** All destinations in the current system plus every system within {@code sectorRadius} sectors. */
    public static List<Destination> destinations(long seed, Location from, int sectorRadius, String query) {
        List<Destination> out = new ArrayList<>();
        Star here = from.star();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Star> stars = new ArrayList<>();
        stars.add(here);
        Star byCode = q.isEmpty() ? null : Galaxy.byDesignation(seed, q);
        if (byCode != null && !byCode.equals(here)) {
            stars.add(byCode);
        }
        for (Star s : Galaxy.starsAround(seed, here.sx(), here.sz(), sectorRadius)) {
            if (!s.equals(here) && !s.equals(byCode)) {
                stars.add(s);
            }
        }
        for (Star s : stars) {
            addSystem(out, seed, from, s);
        }
        if (!q.isEmpty()) {
            out.removeIf(d -> !matches(d, q) && (byCode == null || !d.star().equals(byCode)));
        }
        out.removeIf(d -> d.id().equals(from.bodyId()));
        out.sort(Comparator.comparingDouble(Destination::fuel));
        return out;
    }

    private static boolean matches(Destination d, String q) {
        return d.name().toLowerCase(Locale.ROOT).contains(q) || d.star().name().toLowerCase(Locale.ROOT).contains(q)
                || d.star().designation().toLowerCase(Locale.ROOT).contains(q) || d.typeName().toLowerCase(Locale.ROOT).contains(q);
    }

    private static void addSystem(List<Destination> out, long seed, Location from, Star s) {
        boolean same = s.equals(from.star());
        double ly = same ? 0 : s.distanceTo(from.star());
        if (s.sol()) {
            for (SolarSystem.Body b : SolarSystem.BODIES.values()) {
                PlanetProfile p = SolarSystem.profile(b.id());
                double fuel;
                if (same) {
                    fuel = (family(b).equals(from.family()) ? 16.0 : hop(from.au(), b.orbitAu())) + b.wellCost();
                } else {
                    fuel = JUMP_BASE + ly * PER_LIGHT_YEAR + hop(0, b.orbitAu()) + b.wellCost();
                }
                out.add(new Destination(b.id(), b.name(), s, p == null ? null : p.type, p == null ? 15 : p.baseTemp,
                        p == null ? 10 : p.tempSwing, p == null ? 1.0 : p.gravity, p == null ? 0 : p.danger, p == null ? 0 : p.tier,
                        b.orbitAu(), ly, Math.ceil(fuel), same, b.blurb(), p == null ? Set.of() : p.hazards));
            }
            return;
        }
        for (int orbit = 0; orbit <= s.planetCount(); orbit++) {
            PlanetProfile p = FrontierPlanets.planet(seed, s, orbit);
            if (p == null) {
                continue;
            }
            double fuel;
            if (same) {
                fuel = (p.id.equals(from.family()) ? 0 : hop(from.au(), p.orbitAu)) + well(p);
            } else {
                fuel = JUMP_BASE + ly * PER_LIGHT_YEAR + hop(0, p.orbitAu) + well(p);
            }
            out.add(new Destination(p.id, p.name, s, p.type, p.baseTemp, p.tempSwing, p.gravity, p.danger, p.tier, p.orbitAu, ly,
                    Math.ceil(fuel), same, p.description, p.hazards));
        }
    }

    /** Fuel for one specific destination id, or -1 if it does not exist. */
    public static double costTo(long seed, Location from, String destId) {
        Star target;
        if (destId.startsWith("f:")) {
            FrontierMap.Cell c = FrontierMap.parse(destId);
            if (c == null) {
                return -1;
            }
            target = Galaxy.star(seed, c.sx(), c.sz(), c.slot());
        } else if (SolarSystem.body(destId) != null) {
            target = Galaxy.sol(seed);
        } else {
            return -1;
        }
        if (target == null) {
            return -1;
        }
        List<Destination> list = new ArrayList<>();
        addSystem(list, seed, from, target);
        for (Destination d : list) {
            if (d.id().equals(destId)) {
                return d.fuel();
            }
        }
        return -1;
    }

    public static boolean isStar(StarClass c) {
        return c.hasCorona();
    }
}
