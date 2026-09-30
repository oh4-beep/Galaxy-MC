package com.galaxymc.client.hazard;

import com.galaxymc.entity.Voices;
import com.galaxymc.galaxy.Hazard;
import com.galaxymc.network.HazardPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the hazards the server streams to us. Nothing here touches the world: it is all particles,
 * sounds and a little camera shake, so it can run at full density without any server cost.
 * <ul>
 *   <li>Tornadoes are a spinning funnel of cloud and ground debris under a dark wall cloud.</li>
 *   <li>Tsunamis are a moving wall of blue water capped with foam.</li>
 *   <li>Eruptions are a towering smoke plume with lava fountains, falling ash and shaking ground.</li>
 *   <li>Meteors are burning streaks; lightning storms bring driving rain.</li>
 * </ul>
 */
public final class HazardEffects {
    private HazardEffects() {}

    private static final class Live {
        HazardPayload.Entry entry;
        double x;
        double z;
        float[] meteors;
        int stale;
        int meteorCount;
    }

    private static final Map<Integer, Live> LIVE = new HashMap<>();
    private static final DustParticleOptions WATER_DEEP = new DustParticleOptions(0x2a5ab8, 3.0F);
    private static final DustParticleOptions WATER_LIGHT = new DustParticleOptions(0x5a9ae8, 2.4F);
    private static final DustParticleOptions STORM_DARK = new DustParticleOptions(0x3a3e48, 4.0F);
    private static float shakeYaw;
    private static float shakePitch;

    public static void receive(HazardPayload payload) {
        for (HazardPayload.Entry e : payload.events()) {
            Live live = LIVE.computeIfAbsent(e.id(), k -> new Live());
            live.entry = e;
            live.x = e.x();
            live.z = e.z();
            live.meteors = e.meteors().clone();
            live.stale = 0;
        }
    }

    public static void clear() {
        LIVE.clear();
    }

    /** The hazard nearest the player, for the HUD; null when none is within range. */
    public static HazardPayload.Entry nearest(double px, double pz, double range) {
        HazardPayload.Entry best = null;
        double bestD = range;
        for (Live l : LIVE.values()) {
            double d = Math.hypot(l.x - px, l.z - pz);
            if (l.entry.type() == Hazard.METEORS || l.entry.type() == Hazard.LIGHTNING) {
                d = Math.min(d, 1.0);
            }
            if (d < bestD) {
                bestD = d;
                best = l.entry;
            }
        }
        return best;
    }

    /** Current (dead-reckoned) position of a hazard, for the HUD's distance readout. */
    public static double[] position(int id) {
        Live l = LIVE.get(id);
        return l == null ? null : new double[]{l.x, l.z};
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            LIVE.clear();
            return;
        }
        float shake = 0.0F;
        Iterator<Live> it = LIVE.values().iterator();
        while (it.hasNext()) {
            Live l = it.next();
            if (++l.stale > 30) {
                it.remove();
                continue;
            }
            HazardPayload.Entry e = l.entry;
            if (e.active() && (e.type() == Hazard.TORNADOES || e.type() == Hazard.TSUNAMIS)) {
                l.x += e.dx() * e.speed();
                l.z += e.dz() * e.speed();
            }
            double dist = Math.hypot(l.x - player.getX(), l.z - player.getZ());
            if (dist > 420 && e.type() != Hazard.METEORS && e.type() != Hazard.LIGHTNING) {
                continue;
            }
            RandomSource r = level.getRandom();
            switch (e.type()) {
                case TORNADOES -> tornado(level, l, r, dist, player);
                case TSUNAMIS -> tsunami(level, l, r, dist, player);
                case ERUPTIONS -> shake = Math.max(shake, eruption(level, l, r, dist, player));
                case METEORS -> meteors(level, l, r, player);
                case LIGHTNING -> storm(level, l, r, player);
            }
        }
        applyShake(player, shake);
    }

    // ------------------------------------------------------------------ drawing

    private static void add(ClientLevel level, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(p, true, x, y, z, vx, vy, vz);
    }

    private static void tornado(ClientLevel level, Live l, RandomSource r, double dist, LocalPlayer player) {
        HazardPayload.Entry e = l.entry;
        double in = e.intensity();
        double h = e.height();
        double base = e.y();
        BlockState ground = level.getBlockState(BlockPos.containing(l.x, base - 1, l.z));
        ParticleOptions debris = ground.isAir() ? ParticleTypes.CLOUD : new BlockParticleOption(ParticleTypes.BLOCK, ground);
        int n = (int) (40 + 60 * in);
        for (int i = 0; i < n; i++) {
            double t = Math.pow(r.nextDouble(), 1.2);
            double y = base + t * h;
            double radius = e.radius() * 0.5 + e.radius() * 2.4 * Math.pow(t, 1.6);
            double a = r.nextDouble() * Math.PI * 2.0;
            double px = l.x + Math.cos(a) * radius;
            double pz = l.z + Math.sin(a) * radius;
            double swirl = 0.25 + 0.2 * in;
            ParticleOptions type = t < 0.12 && r.nextInt(3) == 0 ? debris : r.nextInt(5) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD;
            add(level, type, px, y, pz, -Math.sin(a) * swirl, 0.04 + r.nextDouble() * 0.1, Math.cos(a) * swirl);
        }
        // Dust skirt at the base and the wall cloud overhead.
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double radius = e.radius() * (1.2 + r.nextDouble());
            add(level, debris, l.x + Math.cos(a) * radius, base + r.nextDouble(), l.z + Math.sin(a) * radius,
                    Math.cos(a) * 0.3, 0.1, Math.sin(a) * 0.3);
        }
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double radius = e.radius() * (2 + r.nextDouble() * 4);
            add(level, STORM_DARK, l.x + Math.cos(a) * radius, base + h * (0.95 + r.nextDouble() * 0.1), l.z + Math.sin(a) * radius,
                    -Math.sin(a) * 0.2, 0, Math.cos(a) * 0.2);
        }
        if (e.age() % 30 == 0) {
            sound(level, player, l.x, base + 4, l.z, dist, 180, "item.elytra.flying", 3.0F, 0.45F);
            sound(level, player, l.x, base + 4, l.z, dist, 180, "entity.breeze.wind_burst", 2.0F, 0.5F);
        }
    }

    private static void tsunami(ClientLevel level, Live l, RandomSource r, double dist, LocalPlayer player) {
        HazardPayload.Entry e = l.entry;
        double sea = e.y();
        double px = -e.dz();
        double pz = e.dx();
        double height = e.active() ? e.height() : e.height() * Math.min(1.0, e.age() / 200.0) * 0.6;
        for (double s = -e.radius(); s <= e.radius(); s += 2.5) {
            double cx = l.x + px * s;
            double cz = l.z + pz * s;
            for (int k = 0; k < 2; k++) {
                double y = sea + r.nextDouble() * Math.max(0.5, height);
                double back = r.nextDouble() * 3.0;
                add(level, r.nextBoolean() ? WATER_DEEP : WATER_LIGHT, cx - e.dx() * back, y, cz - e.dz() * back,
                        e.dx() * e.speed(), 0.02, e.dz() * e.speed());
            }
            if (r.nextInt(2) == 0) {
                add(level, ParticleTypes.CLOUD, cx, sea + height + r.nextDouble() * 0.8, cz, e.dx() * 0.4, 0.05, e.dz() * 0.4);
            }
            if (r.nextInt(4) == 0) {
                add(level, ParticleTypes.SPLASH, cx + e.dx(), sea + height, cz + e.dz(), 0, 0.2, 0);
            }
        }
        if (e.age() % 20 == 0) {
            sound(level, player, l.x, sea + 2, l.z, dist, 220, "entity.player.splash.high_speed", 3.0F, 0.5F);
            sound(level, player, l.x, sea + 2, l.z, dist, 220, "weather.rain.above", 3.0F, 0.6F);
        }
    }

    /** Returns how hard the ground should shake for the player. */
    private static float eruption(ClientLevel level, Live l, RandomSource r, double dist, LocalPlayer player) {
        HazardPayload.Entry e = l.entry;
        double top = e.y();
        double in = e.intensity();
        int smoke = e.active() ? 14 : 3;
        for (int i = 0; i < smoke; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double off = r.nextDouble() * e.radius() * 0.6;
            add(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, l.x + Math.cos(a) * off, top + r.nextDouble() * 4, l.z + Math.sin(a) * off,
                    (r.nextDouble() - 0.5) * 0.05, 0.25 + r.nextDouble() * 0.35 * in, (r.nextDouble() - 0.5) * 0.05);
        }
        if (e.active()) {
            for (int i = 0; i < 8; i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double hs = r.nextDouble() * 0.5;
                add(level, ParticleTypes.FLAME, l.x + Math.cos(a) * 2, top + 1, l.z + Math.sin(a) * 2,
                        Math.cos(a) * hs, 0.6 + r.nextDouble() * 0.8, Math.sin(a) * hs);
                add(level, ParticleTypes.LAVA, l.x + (r.nextDouble() - 0.5) * e.radius(), top, l.z + (r.nextDouble() - 0.5) * e.radius(), 0, 0, 0);
            }
            for (int i = 0; i < 4; i++) {
                add(level, ParticleTypes.LARGE_SMOKE, l.x + (r.nextDouble() - 0.5) * 30, top + e.height() * (0.4 + r.nextDouble() * 0.5),
                        l.z + (r.nextDouble() - 0.5) * 30, (r.nextDouble() - 0.5) * 0.2, 0.05, (r.nextDouble() - 0.5) * 0.2);
            }
            // Ash falls on anyone downwind.
            if (dist < 220) {
                int ash = (int) (30 * (1.0 - dist / 220.0)) + 4;
                for (int i = 0; i < ash; i++) {
                    add(level, r.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, player.getX() + (r.nextDouble() - 0.5) * 32,
                            player.getY() + 4 + r.nextDouble() * 12, player.getZ() + (r.nextDouble() - 0.5) * 32, 0, -0.05, 0);
                }
            }
            if (e.age() % 20 == 0) {
                sound(level, player, l.x, top, l.z, dist, 400, "entity.generic.explode", 4.0F, 0.4F + r.nextFloat() * 0.2F);
            }
            if (e.age() % 60 == 0) {
                sound(level, player, l.x, top, l.z, dist, 500, "entity.lightning_bolt.thunder", 3.0F, 0.5F);
            }
            if (e.age() % 7 == 0) {
                sound(level, player, l.x, top, l.z, dist, 120, "block.lava.pop", 2.0F, 0.7F);
            }
            return dist < 200 ? (float) (1.1 * in * (1.0 - dist / 200.0)) : 0.0F;
        }
        if (e.age() % 40 == 0) {
            sound(level, player, l.x, top, l.z, dist, 400, "entity.lightning_bolt.thunder", 2.0F, 0.4F);
        }
        return dist < 200 ? (float) (0.4 * (1.0 - dist / 200.0)) : 0.0F;
    }

    private static void meteors(ClientLevel level, Live l, RandomSource r, LocalPlayer player) {
        float[] m = l.meteors;
        int count = m.length / 6;
        for (int i = 0; i < count; i++) {
            int k = i * 6;
            m[k] += m[k + 3];
            m[k + 1] += m[k + 4];
            m[k + 2] += m[k + 5];
            double x = m[k];
            double y = m[k + 1];
            double z = m[k + 2];
            for (int s = 0; s < 4; s++) {
                double back = s * 0.6;
                add(level, ParticleTypes.FLAME, x - m[k + 3] * back, y - m[k + 4] * back, z - m[k + 5] * back,
                        (r.nextDouble() - 0.5) * 0.05, 0.02, (r.nextDouble() - 0.5) * 0.05);
            }
            add(level, ParticleTypes.LARGE_SMOKE, x - m[k + 3] * 2, y - m[k + 4] * 2, z - m[k + 5] * 2, 0, 0.02, 0);
            add(level, ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            if (r.nextInt(3) == 0) {
                add(level, ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
        }
        if (count > l.meteorCount && count > 0) {
            int k = (count - 1) * 6;
            double d = Math.hypot(m[k] - player.getX(), m[k + 2] - player.getZ());
            sound(level, player, m[k], m[k + 1], m[k + 2], d, 250, "entity.blaze.shoot", 2.5F, 0.4F);
        }
        l.meteorCount = count;
        // Keep the shower's sky lit with the odd distant streak.
        if (r.nextInt(8) == 0) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double x = player.getX() + Math.cos(a) * 120;
            double z = player.getZ() + Math.sin(a) * 120;
            add(level, ParticleTypes.FIREWORK, x, player.getY() + 90, z, Math.cos(a) * -0.6, -0.4, Math.sin(a) * -0.6);
        }
    }

    private static void storm(ClientLevel level, Live l, RandomSource r, LocalPlayer player) {
        if (!l.entry.active()) {
            return;
        }
        for (int i = 0; i < 40; i++) {
            double x = player.getX() + (r.nextDouble() - 0.5) * 30;
            double z = player.getZ() + (r.nextDouble() - 0.5) * 30;
            double y = player.getY() + 8 + r.nextDouble() * 10;
            if (level.canSeeSky(BlockPos.containing(x, player.getY() + 1, z))) {
                add(level, ParticleTypes.FALLING_WATER, x, y, z, 0.1, -1.0, 0.05);
            }
        }
        if (r.nextInt(4) == 0) {
            double a = r.nextDouble() * Math.PI * 2.0;
            add(level, STORM_DARK, player.getX() + Math.cos(a) * 40, player.getY() + 45 + r.nextDouble() * 20, player.getZ() + Math.sin(a) * 40,
                    0.1, 0, 0.05);
        }
        if (l.entry.age() % 40 == 0) {
            level.playLocalSound(player.getX(), player.getY() + 10, player.getZ(), Voices.sound("weather.rain.above"), SoundSource.WEATHER,
                    1.5F, 0.8F, false);
        }
    }

    // ------------------------------------------------------------------ sound and shake

    /**
     * Plays a sound for a far-off source: beyond 32 blocks it is moved onto the line between player and
     * source, 32 blocks out, and quietened with distance, so an eruption half a kilometre away rumbles.
     */
    private static void sound(ClientLevel level, LocalPlayer player, double x, double y, double z, double dist, double range, String id,
                              float volume, float pitch) {
        if (dist > range) {
            return;
        }
        SoundEvent sound = Voices.sound(id);
        if (sound == null) {
            return;
        }
        double sx = x;
        double sy = y;
        double sz = z;
        float v = volume;
        if (dist > 32) {
            double k = 32.0 / dist;
            sx = player.getX() + (x - player.getX()) * k;
            sy = player.getY() + (y - player.getY()) * k;
            sz = player.getZ() + (z - player.getZ()) * k;
            v = (float) Math.max(0.25, volume * (1.0 - dist / range));
        }
        level.playLocalSound(sx, sy, sz, sound, SoundSource.WEATHER, v, pitch, false);
    }

    /** A tremor: the view jitters by a fraction of a degree, always returning to where it was. */
    private static void applyShake(LocalPlayer player, float amount) {
        float yaw = 0;
        float pitch = 0;
        if (amount > 0.01F) {
            float t = player.tickCount;
            yaw = amount * (Mth.sin(t * 2.1F) + 0.5F * Mth.sin(t * 5.3F));
            pitch = amount * 0.6F * Mth.sin(t * 3.7F);
        }
        player.setYRot(player.getYRot() + yaw - shakeYaw);
        player.setXRot(Mth.clamp(player.getXRot() + pitch - shakePitch, -90.0F, 90.0F));
        shakeYaw = yaw;
        shakePitch = pitch;
    }

    /** Every hazard currently known, for commands and debugging overlays. */
    public static List<HazardPayload.Entry> all() {
        List<HazardPayload.Entry> out = new ArrayList<>();
        for (Live l : LIVE.values()) {
            out.add(l.entry);
        }
        return out;
    }
}
