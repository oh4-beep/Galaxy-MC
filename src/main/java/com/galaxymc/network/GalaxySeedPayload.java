package com.galaxymc.network;

import com.galaxymc.registry.Reg;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells the client the galaxy seed so it can name stars, tint alien terrain and plan routes locally. */
public record GalaxySeedPayload(long seed) implements CustomPacketPayload {
    public static final Type<GalaxySeedPayload> TYPE = new Type<>(Reg.id("galaxy_seed"));
    public static final StreamCodec<ByteBuf, GalaxySeedPayload> CODEC =
            ByteBufCodecs.LONG.map(GalaxySeedPayload::new, GalaxySeedPayload::seed);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
