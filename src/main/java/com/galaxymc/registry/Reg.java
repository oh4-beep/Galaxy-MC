package com.galaxymc.registry;

import com.galaxymc.GalaxyMC;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Thin registration helpers; also records creative-tab order so the tab lists things as declared. */
public final class Reg {
    private Reg() {}

    public static final List<Item> TAB_ORDER = new ArrayList<>();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GalaxyMC.MOD_ID, path);
    }

    public static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        Block block = blockNoItem(name, factory, props);
        blockItem(name, block, new Item.Properties());
        return block;
    }

    public static Block block(String name, BlockBehaviour.Properties props) {
        return block(name, Block::new, props);
    }

    public static Block blockNoItem(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        Block block = factory.apply(props.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    public static Item blockItem(String name, Block block, Item.Properties props) {
        return item(name, p -> new BlockItem(block, p), props.useBlockDescriptionPrefix());
    }

    public static <T extends Item> T item(String name, Function<Item.Properties, T> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        T item = factory.apply(props.setId(key));
        if (item instanceof BlockItem blockItem) {
            blockItem.registerBlocks(Item.BY_BLOCK, item);
        }
        Registry.register(BuiltInRegistries.ITEM, key, item);
        TAB_ORDER.add(item);
        return item;
    }

    public static Item item(String name, Item.Properties props) {
        return item(name, Item::new, props);
    }

    public static Item item(String name) {
        return item(name, Item::new, new Item.Properties());
    }
}
