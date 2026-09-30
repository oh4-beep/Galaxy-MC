package com.galaxymc.network;

import com.galaxymc.galaxy.Hazard;
import com.galaxymc.hazard.HazardEvent;
import com.galaxymc.registry.Reg;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The hazards near a player, sent a few times a second. The client draws them (funnels, wave walls,
 * eruption plumes, meteor trails, rain) and dead-reckons movement between updates.
 */
public record HazardPayload(List<Entry> events) implements CustomPacketPayload {
    public static final Type<HazardPayload> TYPE = new Type<>(Reg.id("hazards"));
    public static final StreamCodec<ByteBuf, HazardPayload> CODEC = StreamCodec.of(HazardPayload::write, HazardPayload::read);

    /** One event as the client sees it. {@code meteors} holds x, y, z, vx, vy, vz per meteor. */
    public record Entry(int id, Hazard type, String name, double x, double y, double z, float dx, float dz, float speed, float radius,
                        float height, float intensity, boolean active, int age, float[] meteors) {
        public static Entry of(HazardEvent e) {
            float[] m = new float[e.meteors.size() * 6];
            for (int i = 0; i < e.meteors.size(); i++) {
                HazardEvent.Meteor met = e.meteors.get(i);
                m[i * 6] = (float) met.x;
                m[i * 6 + 1] = (float) met.y;
                m[i * 6 + 2] = (float) met.z;
                m[i * 6 + 3] = (float) met.vx;
                m[i * 6 + 4] = (float) met.vy;
                m[i * 6 + 5] = (float) met.vz;
            }
            // A tsunami is drawn from the sea surface up to its current surge height.
            boolean wave = e.type == Hazard.TSUNAMIS;
            return new Entry(e.id, e.type, e.name, e.x, wave ? e.seaLevel : e.y, e.z, (float) e.dx, (float) e.dz, (float) e.speed,
                    (float) e.radius, (float) (wave ? e.surge() : e.height), (float) e.intensity(), e.active(), e.age, m);
        }
    }

    private static void write(ByteBuf buf, HazardPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buf, payload.events.size());
        for (Entry e : payload.events) {
            ByteBufCodecs.VAR_INT.encode(buf, e.id);
            ByteBufCodecs.VAR_INT.encode(buf, e.type.ordinal());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.name);
            buf.writeDouble(e.x);
            buf.writeDouble(e.y);
            buf.writeDouble(e.z);
            buf.writeFloat(e.dx);
            buf.writeFloat(e.dz);
            buf.writeFloat(e.speed);
            buf.writeFloat(e.radius);
            buf.writeFloat(e.height);
            buf.writeFloat(e.intensity);
            buf.writeBoolean(e.active);
            ByteBufCodecs.VAR_INT.encode(buf, e.age);
            ByteBufCodecs.VAR_INT.encode(buf, e.meteors.length);
            for (float f : e.meteors) {
                buf.writeFloat(f);
            }
        }
    }

    private static HazardPayload read(ByteBuf buf) {
        int n = Math.min(64, ByteBufCodecs.VAR_INT.decode(buf));
        List<Entry> events = new ArrayList<>(n);
        Hazard[] types = Hazard.values();
        for (int i = 0; i < n; i++) {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            Hazard type = types[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), types.length)];
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float dx = buf.readFloat();
            float dz = buf.readFloat();
            float speed = buf.readFloat();
            float radius = buf.readFloat();
            float height = buf.readFloat();
            float intensity = buf.readFloat();
            boolean active = buf.readBoolean();
            int age = ByteBufCodecs.VAR_INT.decode(buf);
            int m = Math.min(6 * 64, ByteBufCodecs.VAR_INT.decode(buf));
            float[] meteors = new float[m];
            for (int k = 0; k < m; k++) {
                meteors[k] = buf.readFloat();
            }
            events.add(new Entry(id, type, name, x, y, z, dx, dz, speed, radius, height, intensity, active, age, meteors));
        }
        return new HazardPayload(events);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
