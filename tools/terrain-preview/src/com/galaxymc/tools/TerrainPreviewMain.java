package com.galaxymc.tools;

import com.galaxymc.galaxy.FrontierMap;
import com.galaxymc.galaxy.FrontierPlanets;
import com.galaxymc.galaxy.Galaxy;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.galaxy.SolarSystem;
import com.galaxymc.galaxy.Star;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.world.PlanetColumns;
import com.galaxymc.world.TerrainShaper;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Offline terrain previewer. Runs Galaxy MC's real planet and terrain code against stub blocks and
 * paints what it would generate.
 *
 * <pre>
 *   list [count]              describe frontier worlds near Sol
 *   sheet out.png [count]     contact sheet of many worlds, one tile each
 *   map id out.png [size]     hill-shaded top-down map of one world ("mars" or "f:sx:sz:slot:orbit")
 *   view id out.png           perspective view across one world
 *   check [count]             verify every lake is sealed (no fluid next to air at its level)
 * </pre>
 */
public final class TerrainPreviewMain {
    private static final int MIN_Y = PlanetColumns.MIN_Y;
    private static final int HEIGHT = PlanetColumns.HEIGHT;
    private static long seed = 0x6A1AC7L;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (System.getenv("GALAXY_SEED") != null) {
            seed = Long.parseLong(System.getenv("GALAXY_SEED"));
        }
        String mode = args.length > 0 ? args[0] : "list";
        switch (mode) {
            case "list" -> list(args.length > 1 ? Integer.parseInt(args[1]) : 40);
            case "sheet" -> sheet(args[1], args.length > 2 ? Integer.parseInt(args[2]) : 24);
            case "map" -> {
                PlanetProfile p = byId(args[1]);
                int size = args.length > 3 ? Integer.parseInt(args[3]) : 640;
                ImageIO.write(renderMap(p, size, radiusOf(p)), "png", new File(args[2]));
            }
            case "view" -> ImageIO.write(renderView(byId(args[1]), 960, 540), "png", new File(args[2]));
            case "check" -> check(args.length > 1 ? Integer.parseInt(args[1]) : 30);
            case "heights" -> heights(args.length > 1 ? Integer.parseInt(args[1]) : 30);
            default -> System.err.println("unknown mode " + mode);
        }
    }

    // ------------------------------------------------------------------ selection

    static PlanetProfile byId(String id) {
        if (id.startsWith("f:")) {
            FrontierMap.Cell c = FrontierMap.parse(id);
            return FrontierPlanets.planet(seed, Galaxy.star(seed, c.sx(), c.sz(), c.slot()), c.planet());
        }
        return SolarSystem.profile(id);
    }

    /** Frontier planets near Sol, walking outward so the list is stable for a seed. */
    static List<PlanetProfile> nearby(int count) {
        List<PlanetProfile> out = new ArrayList<>();
        for (int r = 0; r < 40 && out.size() < count; r++) {
            for (int dx = -r; dx <= r && out.size() < count; dx++) {
                for (int dz = -r; dz <= r && out.size() < count; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (Star s : Galaxy.starsInSector(seed, Galaxy.SOL_SX + dx, Galaxy.SOL_SZ + dz)) {
                        for (int orbit = 1; orbit <= s.planetCount() && out.size() < count; orbit++) {
                            PlanetProfile p = FrontierPlanets.planet(seed, s, orbit);
                            if (p != null) {
                                out.add(p);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    static String traits(PlanetProfile p) {
        String lakes = "none";
        if (p.lakes != null) {
            lakes = (p.lakes.fluid() == null ? "dry(" + p.lakes.bed() + ")" : String.valueOf(p.lakes.fluid()))
                    + (p.lakes.cap() != null ? "+" + p.lakes.cap() : "")
                    + (p.lakes.fluid2() != null ? " & " + p.lakes.fluid2() : "")
                    + String.format(Locale.ROOT, " %.2f", p.lakes.chance());
        }
        return String.format(Locale.ROOT, "%-26s %-22s %5d°C mtn %.2f bright %+.2f cliff %.2f snow %s lakes %s%s",
                p.name, p.type.displayName, Math.round(p.baseTemp), p.mountains, p.brightness, p.cliffSlope,
                p.snowLine == Integer.MAX_VALUE ? "-" : String.valueOf(p.snowLine), lakes, p.strata != null ? " strata" : "");
    }

    static void list(int count) {
        for (PlanetProfile p : nearby(count)) {
            System.out.println(p.id + "  " + traits(p));
            System.out.println("      " + p.description);
        }
    }

    static int radiusOf(PlanetProfile p) {
        return p.radius > 0 ? p.radius + 40 : 700;
    }

    static int centreX(PlanetProfile p) {
        if (!p.isFrontier()) {
            return 0;
        }
        FrontierMap.Cell c = FrontierMap.parse(p.id);
        return FrontierMap.centerX(c.sx(), c.slot());
    }

    static int centreZ(PlanetProfile p) {
        if (!p.isFrontier()) {
            return 0;
        }
        FrontierMap.Cell c = FrontierMap.parse(p.id);
        return FrontierMap.centerZ(c.sz(), c.planet());
    }

    // ------------------------------------------------------------------ colour

    static int mul(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Colour of a block as the client would draw it on this planet (tinted blocks included). */
    static int colour(PlanetProfile p, BlockState st) {
        Block b = st.getBlock();
        int c = b.color;
        if (b == ModBlocks.ALIEN_STONE) {
            return mul(c, p.stoneTint);
        }
        if (b == ModBlocks.ALIEN_DEEP_STONE) {
            return scale(mul(c, p.stoneTint), 0.8);
        }
        if (b == ModBlocks.ALIEN_SAND || b == ModBlocks.ALIEN_REGOLITH || b == ModBlocks.ALIEN_SOIL) {
            return mul(c, p.dustTint);
        }
        if (b == ModBlocks.ALIEN_GRASS) {
            return mul(0xffffff, p.grassColor);
        }
        if (b == ModBlocks.CRYSTAL_BLOCK) {
            return mix(c, p.crystalColor, 0.7);
        }
        return c;
    }

    static boolean isFluid(BlockState st) {
        return st.getBlock() == Blocks.WATER || st.getBlock() == Blocks.LAVA;
    }

    // ------------------------------------------------------------------ sampling

    /** Top-down sample of a square region: surface height and shaded-ready colour per cell. */
    static final class Region {
        final int size;
        final double[] height;
        final int[] rgb;
        final boolean[] fluid;

        Region(int size) {
            this.size = size;
            this.height = new double[size * size];
            this.rgb = new int[size * size];
            this.fluid = new boolean[size * size];
        }
    }

    static Region sample(PlanetProfile p, int cx, int cz, int radius, int size) {
        TerrainShaper shaper = TerrainShaper.of(p);
        Region r = new Region(size);
        double step = radius * 2.0 / size;
        IntStream.range(0, size).parallel().forEach(py -> {
            BlockState[] col = new BlockState[HEIGHT];
            for (int px = 0; px < size; px++) {
                int x = (int) Math.floor(cx - radius + (px + 0.5) * step);
                int z = (int) Math.floor(cz - radius + (py + 0.5) * step);
                java.util.Arrays.fill(col, null);
                PlanetColumns.fill(p, shaper, x, z, col);
                int i = py * size + px;
                int top = -1;
                for (int k = HEIGHT - 1; k >= 0; k--) {
                    if (col[k] != null) {
                        top = k;
                        break;
                    }
                }
                if (top < 0) {
                    r.height[i] = Double.NaN;
                    r.rgb[i] = 0x04040a;
                    continue;
                }
                BlockState st = col[top];
                if (isFluid(st)) {
                    int floor = top;
                    while (floor > 0 && col[floor] != null && isFluid(col[floor])) {
                        floor--;
                    }
                    int depth = top - floor;
                    int bed = col[floor] == null ? 0x202020 : colour(p, col[floor]);
                    boolean lava = st.getBlock() == Blocks.LAVA;
                    int surface = lava ? 0xff7a18 : scale(0x2a5ad0, 1.0);
                    r.rgb[i] = lava ? mix(0xffc040, 0xd84010, Math.min(1, depth / 6.0))
                            : mix(mix(bed, surface, 0.55), 0x10285a, Math.min(1, depth / 18.0));
                    r.fluid[i] = true;
                    r.height[i] = top + MIN_Y;
                } else {
                    r.rgb[i] = colour(p, st);
                    r.height[i] = top + MIN_Y;
                }
            }
        });
        return r;
    }

    // ------------------------------------------------------------------ top-down map

    static BufferedImage renderMap(PlanetProfile p, int size, int radius) {
        Region r = sample(p, centreX(p), centreZ(p), radius, size);
        double step = radius * 2.0 / size;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int i = py * size + px;
                double h = r.height[i];
                int rgb = r.rgb[i];
                if (!Double.isNaN(h)) {
                    double hl = px > 0 && !Double.isNaN(r.height[i - 1]) ? r.height[i - 1] : h;
                    double hu = py > 0 && !Double.isNaN(r.height[i - size]) ? r.height[i - size] : h;
                    double shade = r.fluid[i] ? 1.0 : 1.0 + ((h - hl) + (h - hu)) * 0.09 / Math.max(1.0, step * 0.6);
                    shade *= 0.8 + 0.3 * Math.max(0, Math.min(1, (h - 40) / 220.0));
                    rgb = scale(rgb, Math.max(0.3, Math.min(1.6, shade)));
                } else {
                    // Starfield in the void around frontier discs.
                    long hsh = com.galaxymc.util.Hash.of(99, px, py);
                    if ((hsh & 511) == 0) {
                        rgb = 0xc8c8d8;
                    }
                }
                img.setRGB(px, py, rgb);
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ perspective view

    /** Classic voxel-space rendering: ray-march columns from near to far with a y-buffer. */
    static BufferedImage renderView(PlanetProfile p, int w, int h) {
        int span = 720;
        int cx = centreX(p);
        int cz = centreZ(p);
        Region r = sample(p, cx - span / 3, cz - span / 3, span / 2, span);
        // Region covers [cx - span/3 - span/2, ...] at 1 block per cell.
        int ox = cx - span / 3 - span / 2;
        int oz = cz - span / 3 - span / 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int sky = skyColour(p);
        for (int y = 0; y < h; y++) {
            int c = mix(scale(sky, 0.55), sky, (double) y / h);
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, c);
            }
        }
        double camX = ox + span * 0.5;
        double camZ = oz + span - 4;
        double groundAtCam = heightAt(r, ox, oz, span, camX, camZ);
        double camY = (Double.isNaN(groundAtCam) ? 80 : groundAtCam) + 70;
        double horizon = h * 0.36;
        double scaleY = 320.0;
        int[] ybuf = new int[w];
        java.util.Arrays.fill(ybuf, h);
        double fog = span * 0.95;
        for (double d = 2; d < span * 0.95; d += d < 120 ? 0.5 : d < 300 ? 1.0 : 1.5) {
            double half = d * 0.62;
            for (int sx = 0; sx < w; sx++) {
                double wx = camX - half + (sx / (double) w) * half * 2;
                double wz = camZ - d;
                int gx = (int) Math.floor(wx - ox);
                int gz = (int) Math.floor(wz - oz);
                if (gx < 0 || gz < 0 || gx >= span || gz >= span) {
                    continue;
                }
                int i = gz * span + gx;
                double hh = r.height[i];
                if (Double.isNaN(hh)) {
                    continue;
                }
                int top = (int) ((camY - hh) / d * scaleY + horizon);
                if (top < ybuf[sx]) {
                    double hl = gx > 0 && !Double.isNaN(r.height[i - 1]) ? r.height[i - 1] : hh;
                    double hn = gz > 0 && !Double.isNaN(r.height[i - span]) ? r.height[i - span] : hh;
                    double shade = r.fluid[i] ? 1.0 : 1.0 + ((hh - hl) * 0.6 + (hn - hh) * 0.25) * 0.12;
                    int c = scale(r.rgb[i], Math.max(0.35, Math.min(1.5, shade)));
                    c = mix(c, sky, Math.pow(d / fog, 1.6) * 0.85);
                    for (int y = Math.max(0, top); y < ybuf[sx]; y++) {
                        img.setRGB(sx, y, c);
                    }
                    ybuf[sx] = Math.max(0, top);
                }
            }
        }
        return img;
    }

    static double heightAt(Region r, int ox, int oz, int span, double x, double z) {
        int gx = (int) Math.floor(x - ox);
        int gz = (int) Math.floor(z - oz);
        if (gx < 0 || gz < 0 || gx >= span || gz >= span) {
            return Double.NaN;
        }
        return r.height[gz * span + gx];
    }

    static final String[] SKY_HEX = {
            "000000", "78a7ff", "5fd3c8", "5fe07a", "b8d83a", "e8a850", "c83a2a", "e890b0", "9a6ae0",
            "3a3aa0", "7a7470", "000000", "e8f0ff", "e8c84a", "ff6a1a", "b8d0e8", "1a3a4a"
    };

    static int skyColour(PlanetProfile p) {
        if (!p.isFrontier()) {
            return switch (p.id) {
                case "mars" -> 0xc7926a;
                case "venus" -> 0xd8b25a;
                case "titan" -> 0xc8782a;
                case "sun" -> 0xff8a1e;
                case "jupiter" -> 0xd9b48a;
                default -> 0x05050a;
            };
        }
        int c = Integer.parseInt(SKY_HEX[Math.min(p.biomeIndex, SKY_HEX.length - 1)], 16);
        return c == 0 ? 0x06060c : c;
    }

    // ------------------------------------------------------------------ contact sheet

    static void sheet(String out, int count) throws Exception {
        List<PlanetProfile> planets = pickVaried(count);
        int tile = 300;
        int cols = 4;
        int rows = (planets.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * tile, rows * (tile + 34), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x0a0a12));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < planets.size(); i++) {
            PlanetProfile p = planets.get(i);
            BufferedImage m = renderMap(p, tile, radiusOf(p));
            int x = (i % cols) * tile;
            int y = (i / cols) * (tile + 34);
            g.drawImage(m, x, y, null);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(p.name + "  " + Math.round(p.baseTemp) + "°C", x + 6, y + tile + 14);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.setColor(new Color(0xb0b8d0));
            g.drawString(shortTraits(p), x + 6, y + tile + 28);
            System.out.println(p.id + "  " + traits(p));
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out));
    }

    static String shortTraits(PlanetProfile p) {
        StringBuilder sb = new StringBuilder(p.type.displayName);
        if (p.mountains > 0.75) {
            sb.append(", alpine");
        } else if (p.mountains > 0.4) {
            sb.append(", ranges");
        }
        sb.append(p.brightness < -0.35 ? ", dark" : p.brightness > 0.35 ? ", pale" : "");
        if (p.lakes != null) {
            String f = p.lakes.fluid() == null ? "salt flats" : p.lakes.fluid().getBlock() == Blocks.LAVA ? "lava lakes"
                    : p.lakes.fluid().getBlock() == Blocks.WATER ? (p.lakes.cap() != null ? "frozen lakes" : "lakes") : "ice lakes";
            sb.append(", ").append(f);
            if (p.lakes.fluid2() != null) {
                sb.append(" + lava");
            }
        }
        return sb.toString();
    }

    /** A spread of worlds covering as many archetype / trait combinations as possible. */
    static List<PlanetProfile> pickVaried(int count) {
        Map<String, PlanetProfile> byKey = new LinkedHashMap<>();
        for (PlanetProfile p : nearby(600)) {
            if (p.type == PlanetType.GAS_GIANT || p.type == PlanetType.SHATTERED) {
                continue;
            }
            String key = p.type + "/" + (p.mountains > 0.6) + "/" + (p.lakes == null ? "-" : String.valueOf(p.lakes.fluid()));
            byKey.putIfAbsent(key, p);
            if (byKey.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    // ------------------------------------------------------------------ height statistics

    static void heights(int count) {
        for (PlanetProfile p : nearby(count)) {
            if (p.type == PlanetType.GAS_GIANT || p.type == PlanetType.SHATTERED) {
                continue;
            }
            TerrainShaper shaper = TerrainShaper.of(p);
            int cx = centreX(p);
            int cz = centreZ(p);
            int r = p.radius > 0 ? p.radius - 100 : 800;
            List<Double> hs = new ArrayList<>();
            for (int x = -r; x <= r; x += 6) {
                for (int z = -r; z <= r; z += 6) {
                    if (x * x + z * z > r * r) {
                        continue;
                    }
                    double h = PlanetColumns.height(p, shaper, cx + x, cz + z, null);
                    if (!Double.isNaN(h)) {
                        hs.add(h);
                    }
                }
            }
            hs.sort(Double::compare);
            int n = hs.size();
            System.out.printf(Locale.ROOT, "%-24s %-16s mtn %.2f  p10 %4.0f p50 %4.0f p90 %4.0f p99 %4.0f max %4.0f  >150 %4.1f%% >220 %4.1f%%%n",
                    p.name, p.type.name(), p.mountains, hs.get(n / 10), hs.get(n / 2), hs.get(n * 9 / 10), hs.get(n * 99 / 100),
                    hs.get(n - 1), 100.0 * hs.stream().filter(h -> h > 150).count() / n, 100.0 * hs.stream().filter(h -> h > 220).count() / n);
        }
    }

    // ------------------------------------------------------------------ lake containment check

    static void check(int count) {
        int planets = 0;
        long lakeCols = 0;
        long leaks = 0;
        for (PlanetProfile p : nearby(count * 4)) {
            if (p.lakes == null || p.lakes.fluid() == null || planets >= count) {
                continue;
            }
            planets++;
            TerrainShaper shaper = TerrainShaper.of(p);
            int cx = centreX(p);
            int cz = centreZ(p);
            int radius = Math.min(p.radius > 0 ? p.radius : 600, 600);
            int size = 2 * radius;
            // Surface heights of every column, then look for fluid tops adjacent to lower land.
            int[] top = new int[size * size];
            int[] level = new int[size * size];
            boolean[] liquid = new boolean[size * size];
            IntStream.range(0, size).parallel().forEach(j -> {
                BlockState[] col = new BlockState[HEIGHT];
                for (int i = 0; i < size; i++) {
                    java.util.Arrays.fill(col, null);
                    PlanetColumns.fill(p, shaper, cx - radius + i, cz - radius + j, col);
                    int t = -1;
                    for (int k = HEIGHT - 1; k >= 0; k--) {
                        if (col[k] != null) {
                            t = k;
                            break;
                        }
                    }
                    int solid = t;
                    while (solid >= 0 && col[solid] != null && isFluid(col[solid])) {
                        solid--;
                    }
                    top[j * size + i] = solid + MIN_Y;
                    liquid[j * size + i] = t >= 0 && isFluid(col[t]);
                    level[j * size + i] = t + MIN_Y;
                }
            });
            long planetLeaks = 0;
            long seaLeaks = 0;
            for (int j = 1; j < size - 1; j++) {
                for (int i = 1; i < size - 1; i++) {
                    int k = j * size + i;
                    if (!liquid[k]) {
                        continue;
                    }
                    lakeCols++;
                    int lv = level[k];
                    boolean sea = p.palette.fluid() != null && lv == p.seaLevel;
                    int[] nb = {k - 1, k + 1, k - size, k + size};
                    for (int n : nb) {
                        if ((!liquid[n] && top[n] < lv) || (liquid[n] && level[n] < lv)) {
                            if (sea) {
                                seaLeaks++;
                            } else {
                                planetLeaks++;
                            }
                            break;
                        }
                    }
                }
            }
            leaks += planetLeaks;
            System.out.printf(Locale.ROOT, "%-28s %-40s lake leaks %d, sea leaks %d%n", p.name, shortTraits(p), planetLeaks, seaLeaks);
        }
        System.out.printf(Locale.ROOT, "checked %d planets, %d fluid columns, %d leaking%n", planets, lakeCols, leaks);
    }
}
