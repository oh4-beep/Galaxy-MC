package com.galaxymc.climate;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Live Life Support units. Each unit re-announces itself every second from its block entity ticker;
 * entries that stop announcing (unloaded or broken) expire, so no save data or chunk hooks are needed.
 */
public final class LifeSupportRegistry {
    private LifeSupportRegistry() {}

    public static final int RADIUS = 12;
    private static final long EXPIRY = 60;
    private static final Map<ResourceKey<Level>, Map<Long, Long>> UNITS = new ConcurrentHashMap<>();

    public static void announce(Level level, BlockPos pos) {
        UNITS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.asLong(), level.getGameTime());
    }

    public static void remove(Level level, BlockPos pos) {
        Map<Long, Long> map = UNITS.get(level.dimension());
        if (map != null) {
            map.remove(pos.asLong());
        }
    }

    public static boolean covered(Level level, double x, double y, double z) {
        Map<Long, Long> map = UNITS.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        double r2 = RADIUS * RADIUS;
        Iterator<Map.Entry<Long, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() > EXPIRY || e.getValue() > now + EXPIRY) {
                it.remove();
                continue;
            }
            BlockPos p = BlockPos.of(e.getKey());
            double dx = p.getX() + 0.5 - x;
            double dy = p.getY() + 0.5 - y;
            double dz = p.getZ() + 0.5 - z;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        UNITS.clear();
    }
}
