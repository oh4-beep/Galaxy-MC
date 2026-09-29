package com.galaxymc.entity;

/** Anything that belongs to the Galaxy MC bestiary (counted by the fauna spawner's population caps). */
public interface GalaxyCreature {
    Species species();

    default boolean countsTowardsCap() {
        return true;
    }
}
