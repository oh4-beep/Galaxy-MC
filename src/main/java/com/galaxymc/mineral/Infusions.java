package com.galaxymc.mineral;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/** Minerals fused into a tool, weapon or armour piece at the Stellar Forge (at most {@link #MAX}). */
public record Infusions(List<Entry> entries) implements TooltipProvider {
    public static final int MAX = 3;
    public static final Infusions EMPTY = new Infusions(List.of());

    public record Entry(String name, MineralTrait trait, int power, int color) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                MineralTrait.CODEC.fieldOf("trait").forGetter(Entry::trait),
                Codec.INT.fieldOf("power").forGetter(Entry::power),
                Codec.INT.fieldOf("color").forGetter(Entry::color)
        ).apply(i, Entry::new));

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.idMapper(i -> MineralTrait.values()[i], MineralTrait::ordinal), Entry::trait,
                ByteBufCodecs.VAR_INT, Entry::power,
                ByteBufCodecs.INT, Entry::color,
                Entry::new);
    }

    public static final Codec<Infusions> CODEC = Entry.CODEC.listOf().xmap(Infusions::new, Infusions::entries);
    public static final StreamCodec<ByteBuf, Infusions> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Infusions::new, Infusions::entries);

    public Infusions with(Entry e) {
        List<Entry> list = new ArrayList<>(entries);
        list.add(e);
        return new Infusions(List.copyOf(list));
    }

    public boolean full() {
        return entries.size() >= MAX;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        for (Entry e : entries) {
            consumer.accept(Component.translatable("tooltip.galaxy_mc.infused", e.name(), e.trait().adjective)
                    .withStyle(s -> s.withColor(TextColor.fromRgb(e.color()))));
        }
    }
}
