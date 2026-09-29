package com.galaxymc.world;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.util.Hash;
import com.galaxymc.util.Noise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perlin worms: tunnels traced by a "worm" whose heading is steered by smooth Perlin noise, carving a
 * sphere of varying radius at each step.
 *
 * <p>Worms are seeded per 64-block region and may wander several regions away, so a chunk collects the
 * worms of every region within reach and carves whatever parts of them overlap it. Paths are cached
 * per region because dozens of neighbouring chunks all ask for the same worms. Giant burrows are the
 * same algorithm scaled up - the tunnels the colossal sandworms leave behind.
 */
public final class PerlinWorms {
    private PerlinWorms() {}

    public static final int REGION = 64;
    private static final int NORMAL_STEPS = 110;
    private static final int GIANT_STEPS = 170;
    private static final int NORMAL_REACH = 3;
    private static final int GIANT_REACH = 7;

    /** Flattened path: x, y, z, radius per step. */
    public record Worm(float[] path, float minX, float minZ, float maxX, float maxZ) {}

    private static final Map<String, List<Worm>> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Worm>> eldest) {
            return size() > 2048;
        }
    });

    /** Every worm segment that could touch the chunk at block origin (x0, z0). */
    public static List<Worm> wormsNear(PlanetProfile profile, TerrainShaper shaper, int x0, int z0) {
        List<Worm> out = new ArrayList<>();
        int rx = Math.floorDiv(x0, REGION);
        int rz = Math.floorDiv(z0, REGION);
        if (profile.wormsPerRegion > 0) {
            for (int i = -NORMAL_REACH; i <= NORMAL_REACH; i++) {
                for (int j = -NORMAL_REACH; j <= NORMAL_REACH; j++) {
                    collect(out, regionWorms(profile, shaper, rx + i, rz + j, false), x0, z0);
                }
            }
        }
        if (profile.giantBurrows) {
            for (int i = -GIANT_REACH; i <= GIANT_REACH; i++) {
                for (int j = -GIANT_REACH; j <= GIANT_REACH; j++) {
                    collect(out, regionWorms(profile, shaper, rx + i, rz + j, true), x0, z0);
                }
            }
        }
        return out;
    }

    private static void collect(List<Worm> out, List<Worm> worms, int x0, int z0) {
        for (Worm w : worms) {
            if (w.maxX >= x0 - 12 && w.minX <= x0 + 28 && w.maxZ >= z0 - 12 && w.minZ <= z0 + 28) {
                out.add(w);
            }
        }
    }

    private static List<Worm> regionWorms(PlanetProfile profile, TerrainShaper shaper, int rx, int rz, boolean giant) {
        String key = profile.id + "/" + profile.seed + "/" + rx + "/" + rz + (giant ? "/g" : "");
        List<Worm> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<Worm> worms = new ArrayList<>();
        long h = Hash.of(profile.seed ^ (giant ? 0x4749414EL : 0x574F524DL), rx, rz);
        Hash.Rng rng = new Hash.Rng(h);
        int count;
        if (giant) {
            count = rng.chance(0.09) ? 1 : 0;
        } else {
            count = rng.nextInt(profile.wormsPerRegion + 1);
        }
        Noise steer = new Noise(Hash.of(profile.seed, 0x53544552L));
        for (int w = 0; w < count; w++) {
            worms.add(trace(profile, shaper, steer, rng, rx * REGION, rz * REGION, giant, h + w * 31L));
        }
        CACHE.put(key, worms);
        return worms;
    }

    private static Worm trace(PlanetProfile profile, TerrainShaper shaper, Noise steer, Hash.Rng rng, int ox, int oz,
                              boolean giant, long id) {
        double x = ox + rng.nextDouble() * REGION;
        double z = oz + rng.nextDouble() * REGION;
        double surface = shaper.isVolumetric() ? profile.baseHeight : shaper.height(x, z);
        double y;
        if (!giant && rng.chance(0.35)) {
            // Some worms break the surface, giving caves visible entrances.
            y = surface - rng.range(2, 8);
        } else {
            y = rng.range(-50, Math.max(-40, surface - 14));
        }
        double yaw = rng.nextDouble() * Math.PI * 2.0;
        double pitch = rng.range(-0.25, 0.25);
        double baseRadius = giant ? rng.range(4.5, 8.0) : rng.range(1.4, 3.2);
        int steps = giant ? GIANT_STEPS : NORMAL_STEPS - rng.nextInt(40);
        float[] path = new float[steps * 4];
        float minX = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        double t0 = (id % 10000) * 0.731;
        for (int i = 0; i < steps; i++) {
            double t = i * (giant ? 0.035 : 0.06);
            yaw += steer.sample(t, t0, 0.0) * (giant ? 0.18 : 0.32);
            pitch = pitch * 0.85 + steer.sample(t0, t, 5.3) * (giant ? 0.12 : 0.22);
            pitch = Math.max(-0.7, Math.min(0.7, pitch));
            double r = baseRadius * (0.65 + 0.7 * (steer.sample(t * 0.7, 11.1, t0) * 0.5 + 0.5));
            double stepLen = Math.max(1.0, r * 0.55);
            x += Math.cos(yaw) * Math.cos(pitch) * stepLen;
            z += Math.sin(yaw) * Math.cos(pitch) * stepLen;
            y += Math.sin(pitch) * stepLen;
            if (y < -58) {
                y = -58;
                pitch = Math.abs(pitch);
            }
            path[i * 4] = (float) x;
            path[i * 4 + 1] = (float) y;
            path[i * 4 + 2] = (float) z;
            path[i * 4 + 3] = (float) r;
            minX = Math.min(minX, (float) (x - r));
            maxX = Math.max(maxX, (float) (x + r));
            minZ = Math.min(minZ, (float) (z - r));
            maxZ = Math.max(maxZ, (float) (z + r));
        }
        return new Worm(path, minX, minZ, maxX, maxZ);
    }
}
