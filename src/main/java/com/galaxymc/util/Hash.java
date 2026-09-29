package com.galaxymc.util;

/**
 * Stateless hashing helpers (SplitMix64 finaliser). Everything procedural in Galaxy MC derives its
 * randomness from these so that generation is order-independent: the same coordinates always hash to
 * the same value no matter which thread or chunk asks first.
 */
public final class Hash {
    private Hash() {}

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long of(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    public static long of(long seed, long a, long b) {
        return mix(of(seed, a) ^ mix(b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L));
    }

    public static long of(long seed, long a, long b, long c) {
        return mix(of(seed, a, b) ^ mix(c * 0xD6E8FEB86659FD93L + 0x632BE59BD9B4E019L));
    }

    /** Uniform double in [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double unit(long seed, long a) {
        return unit(of(seed, a));
    }

    public static double unit(long seed, long a, long b) {
        return unit(of(seed, a, b));
    }

    public static double unit(long seed, long a, long b, long c) {
        return unit(of(seed, a, b, c));
    }

    /** Uniform int in [0, bound). */
    public static int range(long h, int bound) {
        return (int) Long.remainderUnsigned(h, bound);
    }

    public static long stringSeed(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001B3L;
        }
        return mix(h);
    }

    /** A tiny deterministic RNG for places where sequential draws read better than keyed hashes. */
    public static final class Rng {
        private long state;

        public Rng(long seed) {
            this.state = mix(seed);
        }

        public long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return mix(state);
        }

        public double nextDouble() {
            return unit(nextLong());
        }

        public int nextInt(int bound) {
            return Hash.range(nextLong(), bound);
        }

        public int nextInt(int min, int maxInclusive) {
            return min + nextInt(maxInclusive - min + 1);
        }

        public double range(double min, double max) {
            return min + nextDouble() * (max - min);
        }

        public boolean chance(double p) {
            return nextDouble() < p;
        }

        public <T> T pick(T[] values) {
            return values[nextInt(values.length)];
        }

        public <T> T pick(java.util.List<T> values) {
            return values.get(nextInt(values.size()));
        }
    }
}
