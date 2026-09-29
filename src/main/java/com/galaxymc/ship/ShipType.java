package com.galaxymc.ship;

/**
 * The two hull classes. Rockets are cheap and can only hop between bodies of the current star system;
 * starships carry a far bigger tank and can jump between stars.
 */
public enum ShipType {
    ROCKET("rocket", 1_500, false),
    STARSHIP("starship", 500_000, true);

    public final String id;
    public final double tankCapacity;
    public final boolean interstellar;

    ShipType(String id, double tankCapacity, boolean interstellar) {
        this.id = id;
        this.tankCapacity = tankCapacity;
        this.interstellar = interstellar;
    }

    public static ShipType byId(String id) {
        for (ShipType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return ROCKET;
    }
}
