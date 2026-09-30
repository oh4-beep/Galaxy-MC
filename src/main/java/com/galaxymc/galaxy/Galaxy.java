package com.galaxymc.galaxy;

import com.galaxymc.util.Hash;
import com.galaxymc.util.NameGenerator;
import com.galaxymc.util.Noise;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The procedural galaxy.
 *
 * <p>The disc is a 1536 x 1536 grid of 8-light-year sectors. Star density follows a four-armed
 * logarithmic spiral with a bright central bulge, perturbed by fractal Perlin noise so the arms fray
 * into clusters, voids and filaments instead of looking drawn with a compass. Each sector holds up to
 * eight star slots, and a thin sprinkling of field stars fills even the gaps between the arms; each
 * star has up to seven planets plus its own corona.
 *
 * <p>The layout is purely a function of the galaxy seed, so nothing about it is ever saved: a star
 * visited once regenerates identically forever, and there is always another sector further out.
 */
public final class Galaxy {
    private Galaxy() {}

    public static final int SECTORS = 1536;
    public static final int SLOTS_PER_SECTOR = 8;
    public static final int PLANET_SLOTS = 8;
    public static final double SECTOR_LY = 8.0;
    public static final int CENTER = SECTORS / 2;

    /** Sol is pinned to a quiet stretch of the outer Orion-like arm. */
    public static final int SOL_SX = 1112;
    public static final int SOL_SZ = 842;
    public static final int SOL_SLOT = 0;

    private static final Map<Long, Noise> DENSITY_NOISE = new ConcurrentHashMap<>();
    private static final Map<String, Star> STAR_CACHE = new ConcurrentHashMap<>();

    private static Noise densityNoise(long seed) {
        return DENSITY_NOISE.computeIfAbsent(seed, s -> new Noise(Hash.of(s, 0x47414C41L)));
    }

    /** Stellar density in [0, 1] at a sector. */
    public static double density(long seed, double sx, double sz) {
        double dx = (sx - CENTER) / (double) CENTER;
        double dz = (sz - CENTER) / (double) CENTER;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > 1.0) {
            return 0.0;
        }
        double theta = Math.atan2(dz, dx);
        Noise n = densityNoise(seed);
        double warp = n.fbm(sx / 90.0, sz / 90.0, 3, 2.0, 0.5) * 0.45;

        // Four logarithmic spiral arms: theta = ln(r / r0) / tan(pitch).
        double pitch = Math.toRadians(21.0);
        double armPhase = Math.log(Math.max(r, 0.02) / 0.08) / Math.tan(pitch);
        double best = Double.MAX_VALUE;
        for (int arm = 0; arm < 4; arm++) {
            double a = theta + warp - armPhase - arm * (Math.PI / 2.0);
            a = Math.atan2(Math.sin(a), Math.cos(a));
            best = Math.min(best, Math.abs(a));
        }
        double armWidth = 0.35 + 0.25 * r;
        double arms = Math.exp(-(best * best) / (armWidth * armWidth)) * Math.exp(-r * 1.6);
        double bulge = Math.exp(-r * r / 0.012) * 1.4;
        double disc = Math.exp(-r * 3.2) * 0.18;
        double clusters = Math.max(0.0, n.fbm(sx / 24.0, sz / 24.0, 4, 2.0, 0.55)) * 0.55;
        double d = (arms * 0.95 + bulge + disc) * (0.55 + clusters) * (1.0 - Math.pow(r, 6));
        // Crowded skies: arms are packed and a floor of field stars keeps the inter-arm voids explorable.
        d = d * 1.7 + 0.06 * (1.0 - r * r);
        return Math.max(0.0, Math.min(0.97, d));
    }

    /** The star in a slot, or null when the slot is empty. */
    public static Star star(long seed, int sx, int sz, int slot) {
        if (sx < 0 || sz < 0 || sx >= SECTORS || sz >= SECTORS || slot < 0 || slot >= SLOTS_PER_SECTOR) {
            return null;
        }
        String key = seed + "/" + sx + ":" + sz + ":" + slot;
        Star cached = STAR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Star star = generateStar(seed, sx, sz, slot);
        if (star == null) {
            return null;
        }
        if (STAR_CACHE.size() > 20000) {
            STAR_CACHE.clear();
        }
        STAR_CACHE.put(key, star);
        return star;
    }

    private static Star generateStar(long seed, int sx, int sz, int slot) {
        boolean isSol = sx == SOL_SX && sz == SOL_SZ;
        if (isSol) {
            if (slot != SOL_SLOT) {
                return null;
            }
            return new Star(sx, sz, slot, Hash.of(seed, 0x534F4CL), "Sol", StarClass.G,
                    sx * SECTOR_LY + 4.0, 0.0, sz * SECTOR_LY + 4.0, 8, true);
        }
        long h = Hash.of(seed, sx, sz, slot);
        double p = density(seed, sx + 0.5, sz + 0.5);
        if (Hash.unit(h) > p) {
            return null;
        }
        Hash.Rng rng = new Hash.Rng(h);
        StarClass cls = StarClass.roll(rng.nextDouble());
        double lx = sx * SECTOR_LY + rng.range(0.5, SECTOR_LY - 0.5);
        double lz = sz * SECTOR_LY + rng.range(0.5, SECTOR_LY - 0.5);
        double dist = Math.hypot(sx - CENTER, sz - CENTER) / CENTER;
        double thickness = 60.0 * (1.0 - dist) + 8.0;
        double ly = rng.range(-thickness, thickness);
        int planets = rng.nextInt(cls.minPlanets, cls.maxPlanets);
        String name = NameGenerator.starName(h);
        return new Star(sx, sz, slot, h, name, cls, lx, ly, lz, Math.min(planets, PLANET_SLOTS - 1), false);
    }

    public static Star sol(long seed) {
        return star(seed, SOL_SX, SOL_SZ, SOL_SLOT);
    }

    public static List<Star> starsInSector(long seed, int sx, int sz) {
        List<Star> out = new ArrayList<>(SLOTS_PER_SECTOR);
        for (int slot = 0; slot < SLOTS_PER_SECTOR; slot++) {
            Star s = star(seed, sx, sz, slot);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** All stars within a square sector radius of a centre sector, nearest-first not guaranteed. */
    public static List<Star> starsAround(long seed, int csx, int csz, int radius) {
        List<Star> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.addAll(starsInSector(seed, csx + dx, csz + dz));
            }
        }
        return out;
    }

    /** Parses "GMC-1180-0700-2" (or "1180 700 2"). Returns null when malformed or empty. */
    public static Star byDesignation(long seed, String text) {
        String t = text.trim().toUpperCase().replace("GMC", "").replaceAll("[^0-9]+", " ").trim();
        if (t.isEmpty()) {
            return null;
        }
        String[] parts = t.split(" ");
        if (parts.length < 2) {
            return null;
        }
        try {
            int sx = Integer.parseInt(parts[0]);
            int sz = Integer.parseInt(parts[1]);
            int slot = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
            if (slot >= 0) {
                return star(seed, sx, sz, slot);
            }
            List<Star> stars = starsInSector(seed, sx, sz);
            return stars.isEmpty() ? null : stars.get(0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
