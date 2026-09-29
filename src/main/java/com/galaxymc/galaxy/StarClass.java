package com.galaxymc.galaxy;

/** Spectral classes, with the numbers that drive planet temperatures and the corona landing zone. */
public enum StarClass {
    O("O-type Blue Giant", 0x9bb0ff, 30000.0, 9000, 0.003, 1, 5),
    B("B-type Blue Star", 0xaabfff, 800.0, 6000, 0.013, 2, 6),
    A("A-type White Star", 0xcad7ff, 20.0, 3500, 0.03, 2, 7),
    F("F-type Yellow-White Star", 0xf8f7ff, 3.0, 2500, 0.06, 2, 7),
    G("G-type Yellow Dwarf", 0xfff4ea, 1.0, 1800, 0.10, 3, 7),
    K("K-type Orange Dwarf", 0xffd2a1, 0.35, 1300, 0.16, 2, 7),
    M("M-type Red Dwarf", 0xffcc6f, 0.04, 900, 0.52, 1, 6),
    RED_GIANT("Red Giant", 0xff7b4a, 300.0, 1200, 0.02, 1, 5),
    WHITE_DWARF("White Dwarf", 0xeef3ff, 0.01, 4000, 0.035, 0, 3),
    NEUTRON("Neutron Star", 0xb8f0ff, 0.002, 0, 0.007, 0, 3),
    BLACK_HOLE("Black Hole", 0x301040, 0.0, 0, 0.004, 0, 3);

    public final String displayName;
    public final int color;
    public final double luminosity;
    /** Surface temperature of the landable corona, or 0 where no corona can be visited. */
    public final int coronaTemp;
    public final double weight;
    public final int minPlanets;
    public final int maxPlanets;

    StarClass(String displayName, int color, double luminosity, int coronaTemp, double weight, int minPlanets, int maxPlanets) {
        this.displayName = displayName;
        this.color = color;
        this.luminosity = luminosity;
        this.coronaTemp = coronaTemp;
        this.weight = weight;
        this.minPlanets = minPlanets;
        this.maxPlanets = maxPlanets;
    }

    public boolean hasCorona() {
        return coronaTemp > 0;
    }

    public static StarClass roll(double u) {
        double total = 0;
        for (StarClass c : values()) {
            total += c.weight;
        }
        double t = u * total;
        for (StarClass c : values()) {
            t -= c.weight;
            if (t <= 0) {
                return c;
            }
        }
        return M;
    }
}
