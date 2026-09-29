package com.galaxymc.network;

import com.galaxymc.registry.Reg;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Twice-a-second climate snapshot for the HUD: where you are, how hot it is, what your suit can take
 * and how close you are to hypothermia (negative stress) or heatstroke (positive stress).
 */
public record ClimatePayload(boolean active, String planet, float ambient, int low, int high, float stress,
                             boolean lifeSupport, float gravity) implements CustomPacketPayload {
    public static final Type<ClimatePayload> TYPE = new Type<>(Reg.id("climate"));
    public static final StreamCodec<ByteBuf, ClimatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClimatePayload::active,
            ByteBufCodecs.STRING_UTF8, ClimatePayload::planet,
            ByteBufCodecs.FLOAT, ClimatePayload::ambient,
            ByteBufCodecs.VAR_INT, ClimatePayload::low,
            ByteBufCodecs.VAR_INT, ClimatePayload::high,
            ByteBufCodecs.FLOAT, ClimatePayload::stress,
            ByteBufCodecs.BOOL, ClimatePayload::lifeSupport,
            ByteBufCodecs.FLOAT, ClimatePayload::gravity,
            ClimatePayload::new);

    public static final ClimatePayload INACTIVE = new ClimatePayload(false, "", 20, -5, 40, 0, false, 1);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
