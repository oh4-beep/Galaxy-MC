package com.galaxymc.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Insulation added to a piece of armour at the Thermal Armor Table, in degrees Celsius.
 *
 * <p>{@code warmth} pushes the wearer's safe range down (protects against cold); {@code cooling}
 * pushes it up (protects against heat). They never cancel: a suit can be ready for both.
 */
public record ThermalData(int warmth, int cooling) implements TooltipProvider {
    public static final ThermalData NONE = new ThermalData(0, 0);
    public static final int MAX_PER_PIECE = 20000;

    public static final Codec<ThermalData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("warmth", 0).forGetter(ThermalData::warmth),
            Codec.INT.optionalFieldOf("cooling", 0).forGetter(ThermalData::cooling)
    ).apply(i, ThermalData::new));

    public static final StreamCodec<ByteBuf, ThermalData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ThermalData::warmth,
            ByteBufCodecs.VAR_INT, ThermalData::cooling,
            ThermalData::new);

    public ThermalData add(int w, int c) {
        return new ThermalData(Math.min(MAX_PER_PIECE, warmth + w), Math.min(MAX_PER_PIECE, cooling + c));
    }

    public boolean isEmpty() {
        return warmth == 0 && cooling == 0;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        if (warmth > 0) {
            consumer.accept(Component.translatable("tooltip.galaxy_mc.warmth", warmth).withStyle(ChatFormatting.GOLD));
        }
        if (cooling > 0) {
            consumer.accept(Component.translatable("tooltip.galaxy_mc.cooling", cooling).withStyle(ChatFormatting.AQUA));
        }
    }
}
