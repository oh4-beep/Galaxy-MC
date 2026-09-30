package com.galaxymc.galaxy;

/**
 * Maps the whole procedural galaxy onto one Minecraft dimension.
 *
 * <p>The frontier dimension is cut into square cells of {@link #CELL} blocks. Each galactic sector owns
 * an 8 x 8 block of cells: eight star slots along X, and along Z the star's corona (index 0) followed by
 * its seven possible planets. A planet lives at the centre of its cell as a disc of terrain floating in
 * the void, so worlds can never bleed into each other and every cell is addressable without any saved
 * state. 1536 sectors per side spans about +-18.9 million blocks, inside the 30-million-block border.
 */
public final class FrontierMap {
    private FrontierMap() {}

    /** Cell size: room for a planet of radius {@link #MAX_RADIUS} plus its crumbling rim and some void. */
    public static final int CELL = 3072;
    public static final int MAX_RADIUS = 1380;
    public static final int CELLS_X = Galaxy.SECTORS * Galaxy.SLOTS_PER_SECTOR;
    public static final int CELLS_Z = Galaxy.SECTORS * Galaxy.PLANET_SLOTS;

    public record Cell(int sx, int sz, int slot, int planet) {}

    public static int cellX(double blockX) {
        return (int) Math.floor(blockX / CELL) + CELLS_X / 2;
    }

    public static int cellZ(double blockZ) {
        return (int) Math.floor(blockZ / CELL) + CELLS_Z / 2;
    }

    public static Cell cellAt(double blockX, double blockZ) {
        int cx = cellX(blockX);
        int cz = cellZ(blockZ);
        if (cx < 0 || cz < 0 || cx >= CELLS_X || cz >= CELLS_Z) {
            return null;
        }
        return new Cell(cx / Galaxy.SLOTS_PER_SECTOR, cz / Galaxy.PLANET_SLOTS, cx % Galaxy.SLOTS_PER_SECTOR, cz % Galaxy.PLANET_SLOTS);
    }

    public static int centerX(int sx, int slot) {
        int cx = sx * Galaxy.SLOTS_PER_SECTOR + slot;
        return (cx - CELLS_X / 2) * CELL + CELL / 2;
    }

    public static int centerZ(int sz, int planet) {
        int cz = sz * Galaxy.PLANET_SLOTS + planet;
        return (cz - CELLS_Z / 2) * CELL + CELL / 2;
    }

    /** Planet profile at a frontier block position, or null over the void between worlds. */
    public static PlanetProfile planetAt(long galaxySeed, double blockX, double blockZ) {
        Cell cell = cellAt(blockX, blockZ);
        if (cell == null) {
            return null;
        }
        Star star = Galaxy.star(galaxySeed, cell.sx(), cell.sz(), cell.slot());
        if (star == null) {
            return null;
        }
        return FrontierPlanets.planet(galaxySeed, star, cell.planet());
    }

    /** Horizontal distance from the centre of the planet cell containing this position. */
    public static double distanceFromCenter(double blockX, double blockZ) {
        double lx = Math.floorMod((long) Math.floor(blockX), CELL) + (blockX - Math.floor(blockX)) - CELL / 2.0;
        double lz = Math.floorMod((long) Math.floor(blockZ), CELL) + (blockZ - Math.floor(blockZ)) - CELL / 2.0;
        return Math.sqrt(lx * lx + lz * lz);
    }

    /** Parses a frontier planet id "f:sx:sz:slot:planet". */
    public static Cell parse(String id) {
        if (!id.startsWith("f:")) {
            return null;
        }
        String[] p = id.substring(2).split(":");
        if (p.length != 4) {
            return null;
        }
        try {
            return new Cell(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
