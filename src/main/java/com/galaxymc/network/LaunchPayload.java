package com.galaxymc.network;

import com.galaxymc.registry.Reg;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client asks the navigation console at {@code console} to launch for {@code destination}. */
public record LaunchPayload(BlockPos console, String destination) implements CustomPacketPayload {
    public static final Type<LaunchPayload> TYPE = new Type<>(Reg.id("launch"));
    public static final StreamCodec<ByteBuf, LaunchPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaunchPayload::console,
            ByteBufCodecs.STRING_UTF8, LaunchPayload::destination,
            LaunchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
