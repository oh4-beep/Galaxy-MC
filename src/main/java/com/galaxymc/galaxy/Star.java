package com.galaxymc.galaxy;

/**
 * One star system. Identified by its sector coordinates and slot, positioned in light years inside the
 * galactic disc. Planets are generated lazily from {@link #seed}.
 */
public record Star(
        int sx,
        int sz,
        int slot,
        long seed,
        String name,
        StarClass starClass,
        double lx,
        double ly,
        double lz,
        int planetCount,
        boolean sol
) {
    /** Catalogue designation that works as a galaxy-wide search key: GMC-1180-0700-2. */
    public String designation() {
        return String.format("GMC-%04d-%04d-%d", sx, sz, slot);
    }

    public double distanceTo(Star other) {
        double dx = lx - other.lx;
        double dy = ly - other.ly;
        double dz = lz - other.lz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public String key() {
        return sx + ":" + sz + ":" + slot;
    }
}
