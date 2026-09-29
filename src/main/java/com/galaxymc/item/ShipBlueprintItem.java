package com.galaxymc.item;

import com.galaxymc.ship.ShipType;
import net.minecraft.world.item.Item;

public class ShipBlueprintItem extends Item {
    private final ShipType type;

    public ShipBlueprintItem(ShipType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ShipType type() {
        return type;
    }
}
