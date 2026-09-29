package com.galaxymc.network;

import com.galaxymc.registry.Reg;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Plays the hyperspace streak effect on the client for {@code ticks}, captioned with the destination. */
public record WarpPayload(int ticks, String destination, boolean interstellar) implements CustomPacketPayload {
    public static final Type<WarpPayload> TYPE = new Type<>(Reg.id("warp"));
    public static final StreamCodec<ByteBuf, WarpPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WarpPayload::ticks,
            ByteBufCodecs.STRING_UTF8, WarpPayload::destination,
            ByteBufCodecs.BOOL, WarpPayload::interstellar,
            WarpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
