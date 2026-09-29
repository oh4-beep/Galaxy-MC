package com.galaxymc.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/** Identity of a procedural alien relic: its name, power and strength. */
public record RelicData(long seed, String name, RelicPower power, int level, int color) implements TooltipProvider {
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

    /** Cooldown shrinks as the relic's strength grows. */
    public int cooldownTicks() {
        return Math.max(20, (int) (power.cooldown * (1.15 - level * 0.07)));
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(Component.literal(power.description).withStyle(s -> s.withColor(TextColor.fromRgb(color))));
        consumer.accept(Component.translatable("tooltip.galaxy_mc.relic_level", level, cooldownTicks() / 20).withStyle(ChatFormatting.GRAY));
    }
}
