package com.galaxymc.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Identity of a procedural alien relic: its name, power and strength. */
public record RelicData(long seed, String name, RelicPower power, int level, int color) {
    public static final Codec<RelicData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("seed").forGetter(RelicData::seed),
            Codec.STRING.fieldOf("name").forGetter(RelicData::name),
            RelicPower.CODEC.fieldOf("power").forGetter(RelicData::power),
            Codec.INT.fieldOf("level").forGetter(RelicData::level),
            Codec.INT.fieldOf("color").forGetter(RelicData::color)
    ).apply(i, RelicData::new));

    public static final StreamCodec<ByteBuf, RelicData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RelicData::seed,
            ByteBufCodecs.STRING_UTF8, RelicData::name,
            ByteBufCodecs.idMapper(i -> RelicPower.values()[i], RelicPower::ordinal), RelicData::power,
            ByteBufCodecs.VAR_INT, RelicData::level,
            ByteBufCodecs.INT, RelicData::color,
            RelicData::new);
}
