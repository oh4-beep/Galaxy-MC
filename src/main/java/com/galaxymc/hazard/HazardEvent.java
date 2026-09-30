package com.galaxymc.hazard;

import com.galaxymc.galaxy.Hazard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One running natural disaster. Positions are in world blocks: a tornado's base, the centre of a
 * tsunami's wave front, a volcano's summit, or the centre of a storm or meteor shower.
 */
public final class HazardEvent {
    /** A meteor in flight. */
    public static final class Meteor {
        public double x;
        public double y;
        public double z;
        public double vx;
        public double vy;
        public double vz;
        public final float power;

        Meteor(double x, double y, double z, double vx, double vy, double vz, float power) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.power = power;
        }
    }

    /** A lava source poured over a crater rim, drained again at {@code removeAt}. */
    record Pour(BlockPos pos, long removeAt) {}

    public final int id;
    public final Hazard type;
    public final ResourceKey<Level> dimension;
    public final String planetId;
    public final String name;
    public double x;
    public double y;
    public double z;
    /** Heading (unit vector) for tornadoes and tsunamis. */
    public double dx;
    public double dz;
    public double speed;
    /** Funnel radius, wave half-width, crater radius or storm radius. */
    public double radius;
    /** Funnel height, wave height above the sea, or plume height. */
    public double height;
    public int age;
    public final int warmup;
    public final int lifetime;
    public final long seed;

    // Tsunamis: the sea level, how far offshore the wave starts, and how far it can run inland.
    public int seaLevel;
    public double coastDistance;
    public double landRun;
    public double travelled;

    public final List<Meteor> meteors = new ArrayList<>();
    final List<Pour> pours = new ArrayList<>();
    int idleTicks;

    HazardEvent(int id, Hazard type, ResourceKey<Level> dimension, String planetId, String name, int warmup, int lifetime, long seed) {
        this.id = id;
        this.type = type;
        this.dimension = dimension;
        this.planetId = planetId;
        this.name = name;
        this.warmup = warmup;
        this.lifetime = lifetime;
        this.seed = seed;
    }

    /** 0 while forming, rising to 1 once active and falling back to 0 over the last five seconds. */
    public double intensity() {
        if (age < warmup) {
            return 0.25 * age / Math.max(1, warmup);
        }
        double rise = Math.min(1.0, (age - warmup) / 60.0);
        double fade = Math.min(1.0, (lifetime - age) / 100.0);
        return Math.max(0.0, Math.min(rise, fade)) * 0.75 + 0.25;
    }

    public boolean active() {
        return age >= warmup;
    }

    public boolean finished() {
        return age >= lifetime;
    }

    /** Current height of a tsunami's surge above the sea, shrinking as it runs up the land. */
    public double surge() {
        double inland = Math.max(0.0, travelled - coastDistance);
        return height * Math.max(0.0, 1.0 - inland / Math.max(1.0, landRun));
    }
}
