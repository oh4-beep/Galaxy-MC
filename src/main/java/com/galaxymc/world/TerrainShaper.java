package com.galaxymc.world;

import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.util.Hash;
import com.galaxymc.util.Noise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terrain maths for one planet: a height function for surface worlds and a density function for the
 * floating ones (cloud-islands, asteroid fields). All inputs are world block coordinates; outputs are
 * deterministic so any column can be recomputed on demand (structures, spawn search, landing).
 *
 * <p>The building blocks are layered Perlin fields: a domain-warped continental fBm for large shapes,
 * ridged multifractal for mountain spines, billow noise for dunes, stacked crater fields for airless
 * bodies, and terraced canyon masks for Mars-like worlds.
 */
public final class TerrainShaper {
    private static final Map<String, TerrainShaper> CACHE = new ConcurrentHashMap<>();

    public final PlanetProfile profile;
    private final PlanetType type;
    private final Noise continent;
    private final Noise detail;
    private final Noise ridge;
    private final Noise warp;
    private final Noise feature;
    private final Noise cave;
    private final Noise island;
    private final long seed;
    private final double f;

    private TerrainShaper(PlanetProfile profile) {
        this.profile = profile;
        this.type = profile.type;
        this.seed = profile.seed;
        this.continent = new Noise(Hash.of(seed, 1));
        this.detail = new Noise(Hash.of(seed, 2));
        this.ridge = new Noise(Hash.of(seed, 3));
        this.warp = new Noise(Hash.of(seed, 4));
        this.feature = new Noise(Hash.of(seed, 5));
        this.cave = new Noise(Hash.of(seed, 6));
        this.island = new Noise(Hash.of(seed, 7));
        this.f = profile.frequency;
    }

    public static TerrainShaper of(PlanetProfile profile) {
        TerrainShaper s = CACHE.get(profile.id);
        if (s == null || s.seed != profile.seed) {
            if (CACHE.size() > 256) {
                CACHE.clear();
            }
            s = new TerrainShaper(profile);
            CACHE.put(profile.id, s);
        }
        return s;
    }

    public boolean isVolumetric() {
        return type == PlanetType.GAS_GIANT || type == PlanetType.SHATTERED;
    }

    // ------------------------------------------------------------------ height

    /** Surface height of a column (the y of the top solid block), before frontier edge falloff. */
    public double height(double x, double z) {
        double base = profile.baseHeight;
        double a = profile.amplitude;
        double wx = x + warp.fbm(x / (420.0 / f), z / (420.0 / f), 3, 2.0, 0.5) * 70.0;
        double wz = z + warp.fbm(x / (420.0 / f) + 91.7, z / (420.0 / f) - 33.1, 3, 2.0, 0.5) * 70.0;
        double c = continent.fbm(wx / (720.0 / f), wz / (720.0 / f), 4, 2.0, 0.5);
        double d = detail.fbm(x / (96.0 / f), z / (96.0 / f), 4, 2.0, 0.5);
        double h;
        switch (type) {
            case CRATERED -> h = base + c * a * 0.55 + d * 4.0 + craters(x, z);
            case BARREN_ROCK -> {
                double r = ridge.ridged(wx / (380.0 / f), wz / (380.0 / f), 5, 2.1, 0.5);
                h = base + c * a * 0.6 + r * r * a * 1.3 + d * 6.0;
            }
            case DESERT -> {
                double dunes = feature.billow(rotX(x, z, 0.6) / 44.0, rotZ(x, z, 0.6) / 70.0, 3, 2.0, 0.5);
                double mesaMask = Noise.smoothstep(0.42, 0.52, ridge.fbm(x / 260.0, z / 260.0, 3, 2.0, 0.5) + 0.5);
                h = base + c * a * 0.45 + dunes * 9.0 + d * 3.0 + terrace(mesaMask * a * 1.1, 7.0);
            }
            case DUNE_SEA -> {
                double dunes = feature.billow(rotX(x, z, 0.4) / 80.0, rotZ(x, z, 0.4) / 130.0, 3, 2.0, 0.45);
                h = base + c * a * 0.25 + Math.pow(dunes, 1.3) * a * 1.6 + d * 2.0;
            }
            case CANYON -> {
                double plateau = base + c * a * 0.35 + d * 4.0;
                double v = Math.abs(ridge.fbm(wx / (300.0 / f), wz / (300.0 / f), 4, 2.0, 0.5));
                double open = Noise.smoothstep(0.02, 0.16, v);
                double floor = profile.baseHeight - a * 1.25 + feature.billow(x / 50.0, z / 50.0, 2, 2.0, 0.5) * 5.0;
                h = Noise.lerpD(open, floor, plateau);
                h = terraceSoft(h, 6.0, 0.75);
                h += volcano(x, z, 1400.0, a * 2.4);
            }
            case OCEAN -> {
                // Archipelagos: the continental field only breaks the surface on its highest swells.
                double isl = Math.max(0.0, c - 0.05) * a * 2.6;
                h = base + c * a * 1.2 + isl + d * 6.0;
            }
            case ICE -> {
                double r = ridge.ridged(wx / (420.0 / f), wz / (420.0 / f), 4, 2.0, 0.5);
                h = base + c * a * 0.55 + r * a * 0.6 + d * 3.0;
                double crev = Math.abs(feature.sample(x / 110.0, z / 110.0));
                if (crev < 0.035) {
                    h -= (0.035 - crev) / 0.035 * 14.0;
                }
            }
            case TUNDRA -> h = base + c * a * 0.7 + d * 5.0 + ridge.ridged(wx / 300.0, wz / 300.0, 3, 2.0, 0.5) * a * 0.35;
            case LAVA -> {
                double r = ridge.ridged(wx / (360.0 / f), wz / (360.0 / f), 5, 2.1, 0.5);
                h = base + c * a * 0.6 + r * a * 0.7 + d * 5.0 + volcano(x, z, 520.0, a * 1.6);
            }
            case ASH -> {
                double r = ridge.ridged(wx / (400.0 / f), wz / (400.0 / f), 4, 2.0, 0.5);
                h = base + c * a * 0.5 + terraceSoft(r * a, 5.0, 0.6) + d * 4.0 + volcano(x, z, 640.0, a * 1.8);
                if (profile.palette != null && profile.palette.fluid() != null) {
                    h = river(x, z, h, profile.seaLevel - 2);
                }
            }
            case JUNGLE -> {
                double r = ridge.ridged(wx / (340.0 / f), wz / (340.0 / f), 5, 2.0, 0.5);
                h = base + c * a * 0.7 + r * a * 0.7 + d * 8.0;
                h = river(x, z, h, profile.seaLevel - 3);
            }
            case FUNGAL -> h = base + c * a * 0.6 + d * 9.0 + feature.billow(x / 60.0, z / 60.0, 2, 2.0, 0.5) * 6.0;
            case CRYSTAL -> {
                double r = ridge.ridged(wx / (300.0 / f), wz / (300.0 / f), 5, 2.1, 0.5);
                h = base + c * a * 0.6 + r * r * a * 1.1 + d * 5.0;
            }
            case TOXIC -> h = base + c * a * 0.55 + d * 6.0 + feature.billow(x / 70.0, z / 70.0, 2, 2.0, 0.5) * 4.0;
            case GRASSLAND -> {
                h = base + c * a * 0.5 + d * 4.0 + ridge.ridged(wx / 500.0, wz / 500.0, 3, 2.0, 0.5) * a * 0.35;
                h = river(x, z, h, profile.seaLevel - 2);
            }
            case STELLAR -> h = base + c * a * 0.9 + d * 6.0 + Math.max(0, ridge.ridged(wx / 200.0, wz / 200.0, 3, 2.0, 0.5) - 0.5) * a;
            default -> h = base + c * a * 0.5 + d * 4.0;
        }
        return h;
    }

    private static double rotX(double x, double z, double ang) {
        return x * Math.cos(ang) - z * Math.sin(ang);
    }

    private static double rotZ(double x, double z, double ang) {
        return x * Math.sin(ang) + z * Math.cos(ang);
    }

    private static double terrace(double h, double step) {
        return Math.floor(h / step) * step;
    }

    private static double terraceSoft(double h, double step, double sharp) {
        double k = Math.floor(h / step);
        double t = h / step - k;
        double s = Noise.smoothstep(0.5 - sharp * 0.5, 0.5 + sharp * 0.5, t);
        return (k + s) * step;
    }

    private double river(double x, double z, double h, int bed) {
        double r = Math.abs(feature.fbm(x / 520.0, z / 520.0, 3, 2.0, 0.5));
        if (r < 0.05) {
            double t = Noise.smoothstep(0.0, 0.05, r);
            return Noise.lerpD(t, bed, Math.max(h, bed));
        }
        return h;
    }

    /** Sparse shield volcanoes: a broad cone with a collapsed caldera at the top. */
    private double volcano(double x, double z, double cellSize, double peak) {
        int cx = (int) Math.floor(x / cellSize);
        int cz = (int) Math.floor(z / cellSize);
        double total = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long h = Hash.of(seed ^ 0x564F4C43L, cx + i, cz + j);
                if (Hash.unit(h) > 0.22) {
                    continue;
                }
                double px = (cx + i + 0.2 + Hash.unit(h, 1) * 0.6) * cellSize;
                double pz = (cz + j + 0.2 + Hash.unit(h, 2) * 0.6) * cellSize;
                double radius = cellSize * (0.18 + Hash.unit(h, 3) * 0.2);
                double d = Math.hypot(x - px, z - pz) / radius;
                if (d < 1.0) {
                    double cone = Math.pow(1.0 - d, 1.6) * peak * (0.6 + Hash.unit(h, 4) * 0.4);
                    if (d < 0.12) {
                        cone -= (0.12 - d) / 0.12 * peak * 0.25;
                    }
                    total = Math.max(total, cone);
                }
            }
        }
        return total;
    }

    /** Three scales of impact craters: bowls with raised rims, stacked and overlapping. */
    private double craters(double x, double z) {
        return craterScale(x, z, 360.0, 0.5, 1) + craterScale(x, z, 110.0, 0.6, 2) + craterScale(x, z, 34.0, 0.55, 3);
    }

    private double craterScale(double x, double z, double cell, double prob, int salt) {
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double sum = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long h = Hash.of(seed ^ (0x43524154L * salt), cx + i, cz + j);
                if (Hash.unit(h) > prob) {
                    continue;
                }
                double px = (cx + i + Hash.unit(h, 1)) * cell;
                double pz = (cz + j + Hash.unit(h, 2)) * cell;
                double radius = cell * (0.16 + Hash.unit(h, 3) * 0.3);
                double t = Math.hypot(x - px, z - pz) / radius;
                if (t > 1.8) {
                    continue;
                }
                double depth = radius * 0.34;
                double rim = radius * 0.11;
                if (t < 1.0) {
                    sum -= depth * (1.0 - t * t);
                    // Central peak on the largest craters.
                    if (cell > 200 && t < 0.18) {
                        sum += depth * 0.5 * (1.0 - t / 0.18);
                    }
                }
                double rt = (t - 1.0) / 0.28;
                sum += rim * Math.exp(-rt * rt);
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------ volumetric worlds

    /** Solid where positive. Only meaningful when {@link #isVolumetric()}. */
    public double density(double x, double y, double z) {
        if (type == PlanetType.GAS_GIANT) {
            double centre = profile.baseHeight + continent.fbm(x / 600.0, z / 600.0, 3, 2.0, 0.5) * 50.0;
            double thickness = profile.amplitude * (0.6 + 0.5 * (detail.fbm(x / 300.0, z / 300.0, 2, 2.0, 0.5) + 1.0));
            double dy = (y - centre) / thickness;
            // Flatten the tops: islands are mesas of cloud with rocky cores hanging beneath.
            double vertical = dy > 0 ? dy * dy * 3.2 : dy * dy * 0.9;
            double n = island.fbm(x / 150.0, y / 60.0, z / 150.0, 4, 2.0, 0.5);
            return n + 0.02 - vertical;
        }
        // Asteroid field: sparse lumps of rock scattered through a tall band of space.
        double band = 1.0 - Math.abs((y - profile.baseHeight) / (profile.amplitude * 2.4));
        if (band <= 0) {
            return -1;
        }
        double n = island.fbm(x / 70.0, y / 70.0, z / 70.0, 3, 2.0, 0.5);
        double big = continent.fbm(x / 400.0, y / 300.0, z / 400.0, 2, 2.0, 0.5);
        return n * 1.3 + big * 0.35 - 0.42 + band * 0.12 - 0.12;
    }

    /** Cheese-cave field in roughly [-1, 1]; carve where it exceeds the planet's threshold. */
    public double caveNoise(double x, double y, double z) {
        return cave.fbm(x / 72.0, y / 40.0, z / 72.0, 2, 2.0, 0.5);
    }

    public double caveThreshold() {
        return 0.62 - 0.14 * profile.caveDensity;
    }

    /** Low-frequency 2D noise used for surface variation (patches, flora density), in [-1, 1]. */
    public double patch(double x, double z) {
        return feature.fbm(x / 48.0 + 311.0, z / 48.0 - 97.0, 2, 2.0, 0.5);
    }

    public Noise warpNoise() {
        return warp;
    }

    public Noise detailNoise() {
        return detail;
    }
}
