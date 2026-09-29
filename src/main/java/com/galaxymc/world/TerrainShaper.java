package com.galaxymc.world;

import com.galaxymc.galaxy.FrontierMap;
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
 * <p>The height of a column is built in three layers:
 * <ol>
 *   <li><b>Archetype shape</b> - domain-warped continental fBm plus whatever the world type calls for:
 *   dunes, canyon masks, crater fields, shield volcanoes, crevasses, rivers.</li>
 *   <li><b>Mountain ranges</b> - a broad massif mask decides where ranges rise; inside it, warped
 *   ridged multifractal draws knife-edge spines and valleys. The planet's {@code mountains} trait
 *   scales this from nothing to titanic peaks that brush the build limit.</li>
 *   <li><b>Lake basins</b> - scattered per 224-block cell. Each basin's water line is chosen from the
 *   lowest point of its rim, the bowl is carved beneath it and a berm is raised wherever the rim dips
 *   below it, so every lake is sealed and never spills when fluids start ticking.</li>
 * </ol>
 */
public final class TerrainShaper {
    private static final Map<String, TerrainShaper> CACHE = new ConcurrentHashMap<>();

    public static final int NO_LAKE = Integer.MIN_VALUE;
    public static final int LAKE_CELL = 224;
    /** Build ceiling for terrain; peaks are squashed smoothly towards it rather than sliced flat. */
    private static final double PEAK_SOFT = 262.0;
    private static final double PEAK_HARD = 300.0;

    public final PlanetProfile profile;
    private final PlanetType type;
    private final Noise continent;
    private final Noise detail;
    private final Noise ridge;
    private final Noise warp;
    private final Noise feature;
    private final Noise cave;
    private final Noise island;
    private final Noise massif;
    private final Noise shoreline;
    private final long seed;
    private final double f;
    private final Map<Long, Lake> lakes = new ConcurrentHashMap<>();
    private final double lakeCell;

    /** One inland basin. {@code kind} 0 uses the primary lake fluid, 1 the secondary. */
    public record Lake(double x, double z, double radius, int level, double depth, int kind) {
        static final Lake NONE = new Lake(0, 0, 0, NO_LAKE, 0, 0);
    }

    /** Result of sampling one column: final height plus the lake it belongs to, if any. */
    public static final class Column {
        public double height;
        /** Water line of the lake covering this column, or {@link #NO_LAKE}. */
        public int lakeLevel = NO_LAKE;
        public int lakeKind;
        /** True within the lake's shoreline zone (for beaches and berms). */
        public boolean lakeShore;

        void reset() {
            height = 0;
            lakeLevel = NO_LAKE;
            lakeKind = 0;
            lakeShore = false;
        }
    }

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
        this.massif = new Noise(Hash.of(seed, 8));
        this.shoreline = new Noise(Hash.of(seed, 9));
        this.f = profile.frequency;
        this.lakeCell = LAKE_CELL * (profile.lakes == null ? 1.0 : profile.lakes.scale());
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
        return sample(x, z, null);
    }

    /** Height of a column including lakes; fills {@code out} (if given) with lake information. */
    public double sample(double x, double z, Column out) {
        if (out != null) {
            out.reset();
        }
        double h = landHeight(x, z);
        if (profile.lakes != null && !isVolumetric()) {
            h = applyLakes(x, z, h, out);
        }
        h = capPeaks(h);
        if (out != null) {
            out.height = h;
        }
        return h;
    }

    /** Archetype shape plus mountain ranges: the terrain before any lake is carved into it. */
    public double landHeight(double x, double z) {
        return archetype(x, z) + mountains(x, z);
    }

    private static double capPeaks(double h) {
        if (h <= PEAK_SOFT) {
            return h;
        }
        double span = PEAK_HARD - PEAK_SOFT;
        return PEAK_SOFT + span * Math.tanh((h - PEAK_SOFT) / span);
    }

    private double archetype(double x, double z) {
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
                // Mesas: the ridged field is terraced into stepped plateaus broken by gullies.
                h = base + c * a * 0.6 + terraceSoft(r * r * a * 1.3, 9.0, 0.55) + d * 6.0;
                h += craterScale(x, z, 180.0, 0.25, 7) * 0.6;
            }
            case DESERT -> {
                double dunes = feature.billow(rotX(x, z, 0.6) / 44.0, rotZ(x, z, 0.6) / 70.0, 3, 2.0, 0.5);
                double mesaMask = Noise.smoothstep(0.42, 0.52, ridge.fbm(x / 260.0, z / 260.0, 3, 2.0, 0.5) + 0.5);
                h = base + c * a * 0.45 + dunes * 9.0 + d * 3.0 + terrace(mesaMask * a * 1.1, 7.0);
            }
            case DUNE_SEA -> {
                // Star dunes: two crossing billow fields give the crested, many-armed dunes of real ergs.
                double d1 = feature.billow(rotX(x, z, 0.4) / 80.0, rotZ(x, z, 0.4) / 130.0, 3, 2.0, 0.45);
                double d2 = feature.billow(rotX(x, z, 2.1) / 120.0 + 40, rotZ(x, z, 2.1) / 90.0 - 17, 2, 2.0, 0.45);
                double dunes = Math.max(d1, d2 * 0.8);
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
                h += volcano(x, z, 900.0, a * 1.4) * 0.8;
            }
            case ICE -> {
                double r = ridge.ridged(wx / (420.0 / f), wz / (420.0 / f), 4, 2.0, 0.5);
                h = base + c * a * 0.55 + r * a * 0.6 + d * 3.0;
                // Glacial shelves: broad flat ice plateaus with steep calving edges.
                double shelf = Noise.smoothstep(0.1, 0.18, feature.fbm(x / 340.0 + 13, z / 340.0 - 71, 2, 2.0, 0.5));
                h = Noise.lerpD(shelf * 0.6, h, terrace(h, 10.0) + 2.0);
                // Crevasse fields: only where the ice is under stress, not as a maze over the whole world.
                double stress = feature.fbm(x / 700.0 - 55, z / 700.0 + 21, 2, 2.0, 0.5);
                if (stress > 0.12) {
                    double crev = Math.abs(feature.sample(x / 150.0, z / 150.0));
                    double width = 0.03 * Noise.smoothstep(0.12, 0.3, stress);
                    if (crev < width) {
                        h -= (width - crev) / width * 14.0;
                    }
                }
            }
            case TUNDRA -> {
                h = base + c * a * 0.7 + d * 5.0 + ridge.ridged(wx / 300.0, wz / 300.0, 3, 2.0, 0.5) * a * 0.35;
                // Frost-heave hummocks and kettle hollows.
                h += (feature.billow(x / 18.0, z / 18.0, 2, 2.0, 0.5) - 0.35) * 2.5;
                h = river(x, z, h, profile.seaLevel - 2);
            }
            case LAVA -> {
                double r = ridge.ridged(wx / (360.0 / f), wz / (360.0 / f), 5, 2.1, 0.5);
                h = base + c * a * 0.6 + r * a * 0.7 + d * 5.0 + volcano(x, z, 520.0, a * 1.6);
            }
            case ASH -> {
                double r = ridge.ridged(wx / (400.0 / f), wz / (400.0 / f), 4, 2.0, 0.5);
                h = base + c * a * 0.5 + terraceSoft(r * a, 5.0, 0.6) + d * 4.0 + volcano(x, z, 640.0, a * 1.8);
                if (profile.palette != null && profile.palette.fluid() != null && profile.seaLevel > PlanetColumns.MIN_Y + 16) {
                    h = river(x, z, h, profile.seaLevel - 2);
                }
            }
            case JUNGLE -> {
                double r = ridge.ridged(wx / (340.0 / f), wz / (340.0 / f), 5, 2.0, 0.5);
                // Karst towers: tall steep-sided pillars poking out of the canopy.
                double karst = Math.max(0, feature.ridged(x / 70.0, z / 70.0, 2, 2.0, 0.5) - 0.72) * 90.0;
                h = base + c * a * 0.7 + r * a * 0.7 + d * 8.0 + karst;
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

    /**
     * Mountain ranges. The massif mask carves the world into lowlands and highlands; within highlands a
     * warped ridged multifractal gives sharp spines, and its square makes deep U-shaped valleys between
     * them. Foothills fade the ranges into the plains so they never start as a wall.
     */
    public double mountains(double x, double z) {
        double scale = profile.mountains;
        if (scale <= 0.0 || isVolumetric()) {
            return 0.0;
        }
        double s = 1.0 / f;
        double m = massif.fbm(x / (820.0 * s), z / (820.0 * s), 3, 2.0, 0.5);
        double mask = Noise.smoothstep(-0.22, 0.26, m);
        if (mask <= 0.0) {
            return 0.0;
        }
        double wx = x + warp.fbm(x / (260.0 * s) + 17.3, z / (260.0 * s) - 41.9, 2, 2.0, 0.5) * 80.0;
        double wz = z + warp.fbm(x / (260.0 * s) - 63.1, z / (260.0 * s) + 8.7, 2, 2.0, 0.5) * 80.0;
        double spine = massif.ridged(wx / (520.0 * s), wz / (520.0 * s), 6, 2.05, 0.52);
        double peaks = Math.pow(spine, 2.1);
        double foothills = (massif.fbm(x / (210.0 * s) + 300, z / (210.0 * s) - 300, 3, 2.0, 0.5) * 0.5 + 0.5) * 0.24;
        double rough = detail.fbm(x / 34.0, z / 34.0, 2, 2.0, 0.5) * 0.04;
        return Math.pow(mask, 1.5) * (peaks * 1.1 + foothills + rough) * 190.0 * scale;
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

    // ------------------------------------------------------------------ lakes

    /** The basin (if any) owned by lake cell (cx, cz). Computed once and cached. */
    public Lake lakeInCell(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Lake cached = lakes.get(key);
        if (cached != null) {
            return cached;
        }
        Lake lake = buildLake(cx, cz);
        if (lakes.size() > 8192) {
            lakes.clear();
        }
        lakes.put(key, lake);
        return lake;
    }

    private Lake buildLake(int cx, int cz) {
        PlanetProfile.Lakes spec = profile.lakes;
        long h = Hash.of(seed ^ 0x4C414B45L, cx, cz);
        // Great-lake worlds have fewer, larger cells, so each cell is more likely to hold a basin.
        double chance = spec == null ? 0 : Math.min(0.95, spec.chance() * (spec.scale() > 1.0 ? 1.8 : 1.0));
        if (spec == null || Hash.unit(h) >= chance) {
            return Lake.NONE;
        }
        // Centres stay in the middle 40% of their cell and radii stay under 39 blocks, so the shore
        // zones (1.5 radii, plus shoreline bend) of neighbouring basins can never touch.
        double px = (cx + 0.3 + Hash.unit(h, 1) * 0.4) * lakeCell;
        double pz = (cz + 0.3 + Hash.unit(h, 2) * 0.4) * lakeCell;
        double u = Hash.unit(h, 3);
        double radius = (12.0 + u * u * 27.0) * spec.scale();
        if (profile.radius > 0) {
            // Frontier worlds are discs; keep basins well clear of the crumbling rim.
            double d = FrontierMap.distanceFromCenter(px, pz);
            if (d + radius * 1.3 > profile.radius - PlanetColumns.RIM - 10) {
                return Lake.NONE;
            }
        }
        double centre = landHeight(px, pz);
        double minRim = Double.MAX_VALUE;
        double maxRim = -Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            double ang = i * (Math.PI * 2.0 / 16.0);
            double rh = landHeight(px + Math.cos(ang) * radius * 1.2, pz + Math.sin(ang) * radius * 1.2);
            minRim = Math.min(minRim, rh);
            maxRim = Math.max(maxRim, rh);
        }
        // Tarns may sit in mountain cirques, but never on a cliff face.
        if (maxRim - minRim > 26.0 + radius * 0.25) {
            return Lake.NONE;
        }
        int level = (int) Math.floor(Math.min(minRim - 1.0, centre + 1.0));
        if (profile.palette != null && profile.palette.fluid() != null && level <= profile.seaLevel + 1) {
            return Lake.NONE;
        }
        if (level < PlanetColumns.MIN_Y + 24 || level > PEAK_SOFT) {
            return Lake.NONE;
        }
        double depth = Math.min(24.0, 3.0 + radius * 0.16 + Hash.unit(h, 4) * 4.0);
        int kind = spec.fluid2() != null && Hash.unit(h, 5) < spec.secondMix() ? 1 : 0;
        return new Lake(px, pz, radius, level, depth, kind);
    }

    private double applyLakes(double x, double z, double h, Column out) {
        int cx = (int) Math.floor(x / lakeCell);
        int cz = (int) Math.floor(z / lakeCell);
        // Irregular shorelines: the normalised distance is bent by noise so basins are not circles.
        double bend = shoreline.fbm(x / 38.0, z / 38.0, 2, 2.0, 0.5) * 0.22;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Lake lake = lakeInCell(cx + i, cz + j);
                if (lake.level() == NO_LAKE) {
                    continue;
                }
                double t = Math.hypot(x - lake.x(), z - lake.z()) / lake.radius() + bend;
                if (t >= 1.5) {
                    continue;
                }
                int level = lake.level();
                if (t < 1.0) {
                    // Bowl: one block of water at the very edge, deepening smoothly towards the middle.
                    double bowl = level - 1.0 - (lake.depth() - 1.0) * Math.pow(1.0 - t * t, 0.8);
                    h = Math.min(h, bowl);
                    if (out != null && (out.lakeLevel == NO_LAKE || level < out.lakeLevel)) {
                        out.lakeLevel = level;
                        out.lakeKind = lake.kind();
                        out.lakeShore = t > 0.8;
                    }
                } else {
                    // Berm: guarantees the rim stands above the water line wherever the land dips.
                    double s = Noise.smoothstep(1.12, 1.5, t);
                    h = Noise.lerpD(s, Math.max(h, level + 1.0), h);
                    if (out != null && out.lakeLevel == NO_LAKE && t < 1.25 && h <= level + 2.5) {
                        out.lakeShore = true;
                        out.lakeKind = lake.kind();
                    }
                }
            }
        }
        return h;
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

    /** Thin crack network in [0, 1] (0 on a crack): lava veins, crevasse lines, salt polygons. */
    public double cracks(double x, double z) {
        return Math.abs(detail.sample(x / 23.0 + 77.0, 0.3, z / 23.0 - 12.0));
    }

    public Noise warpNoise() {
        return warp;
    }

    public Noise detailNoise() {
        return detail;
    }
}
