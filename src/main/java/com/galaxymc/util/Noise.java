package com.galaxymc.util;

/**
 * Seeded improved Perlin noise (Ken Perlin, 2002) with the fractal helpers the terrain code leans on.
 *
 * <p>Every planet owns several of these, each seeded from the planet seed plus a salt, so the same
 * planet always regenerates identically on any machine. Instances are immutable after construction and
 * safe to share between world-gen threads.
 */
public final class Noise {
    private final int[] perm = new int[512];
    private final double offX;
    private final double offY;
    private final double offZ;

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = Hash.mix(s + i);
            int j = (int) Long.remainderUnsigned(s, i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
        // Random sub-lattice offsets stop every noise field from sharing a zero at the origin.
        offX = Hash.unit(Hash.mix(seed ^ 0x9E3779B97F4A7C15L)) * 256.0;
        offY = Hash.unit(Hash.mix(seed ^ 0xC2B2AE3D27D4EB4FL)) * 256.0;
        offZ = Hash.unit(Hash.mix(seed ^ 0x165667B19E3779F9L)) * 256.0;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** Raw 3D Perlin noise, roughly in [-1, 1]. */
    public double sample(double x, double y, double z) {
        x += offX;
        y += offY;
        z += offZ;
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        xi &= 255;
        yi &= 255;
        zi &= 255;
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);
        int a = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;
        return lerp(w,
                lerp(v,
                        lerp(u, grad(perm[aa], xf, yf, zf), grad(perm[ba], xf - 1, yf, zf)),
                        lerp(u, grad(perm[ab], xf, yf - 1, zf), grad(perm[bb], xf - 1, yf - 1, zf))),
                lerp(v,
                        lerp(u, grad(perm[aa + 1], xf, yf, zf - 1), grad(perm[ba + 1], xf - 1, yf, zf - 1)),
                        lerp(u, grad(perm[ab + 1], xf, yf - 1, zf - 1), grad(perm[bb + 1], xf - 1, yf - 1, zf - 1))));
    }

    /** 2D Perlin noise (a slice through the 3D field). */
    public double sample(double x, double z) {
        return sample(x, 0.5, z);
    }

    /** Fractal Brownian motion: summed octaves, normalised to roughly [-1, 1]. */
    public double fbm(double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        for (int i = 0; i < octaves; i++) {
            sum += sample(x * freq, i * 17.31, z * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    public double fbm(double x, double y, double z, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        for (int i = 0; i < octaves; i++) {
            sum += sample(x * freq, y * freq + i * 31.7, z * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /** Ridged multifractal in [0, 1]: sharp crests where the underlying noise crosses zero. */
    public double ridged(double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        double weight = 1.0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(sample(x * freq, i * 11.13, z * freq));
            n *= n;
            n *= weight;
            weight = Math.min(1.0, Math.max(0.0, n * 2.0));
            sum += n * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /** Billowy noise in [0, 1]: rounded dunes and puffy clouds. */
    public double billow(double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        for (int i = 0; i < octaves; i++) {
            sum += Math.abs(sample(x * freq, i * 7.77, z * freq)) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerpD(double t, double a, double b) {
        return a + t * (b - a);
    }
}
