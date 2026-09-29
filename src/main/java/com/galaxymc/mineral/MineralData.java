package com.galaxymc.mineral;

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

/**
 * A procedural mineral's full identity, carried on the item so clients never need to regenerate it.
 * Two stacks only merge when every field matches, i.e. when they are the same mineral.
 */
public record MineralData(long seed, int tier, String name, int color, MineralTrait trait, int power) implements TooltipProvider {
    public static final Codec<MineralData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("seed").forGetter(MineralData::seed),
            Codec.INT.fieldOf("tier").forGetter(MineralData::tier),
            Codec.STRING.fieldOf("name").forGetter(MineralData::name),
            Codec.INT.fieldOf("color").forGetter(MineralData::color),
            MineralTrait.CODEC.fieldOf("trait").forGetter(MineralData::trait),
            Codec.INT.fieldOf("power").forGetter(MineralData::power)
    ).apply(i, MineralData::new));

    public static final StreamCodec<ByteBuf, MineralData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, MineralData::seed,
            ByteBufCodecs.VAR_INT, MineralData::tier,
            ByteBufCodecs.STRING_UTF8, MineralData::name,
            ByteBufCodecs.INT, MineralData::color,
            ByteBufCodecs.idMapper(i -> MineralTrait.values()[i], MineralTrait::ordinal), MineralData::trait,
            ByteBufCodecs.VAR_INT, MineralData::power,
            MineralData::new);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(Component.literal(trait.adjective + " - " + trait.effect(power)).withStyle(s -> s.withColor(TextColor.fromRgb(trait.color))));
        consumer.accept(Component.translatable("tooltip.galaxy_mc.mineral_tier", tier).withStyle(ChatFormatting.GRAY));
    }
}
