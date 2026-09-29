package com.galaxymc.registry;

import com.galaxymc.climate.ThermalData;
import com.galaxymc.mineral.MineralData;
import com.galaxymc.mineral.Infusions;
import com.galaxymc.item.RelicData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Custom item data components. */
public final class ModComponents {
    private ModComponents() {}

    public static final DataComponentType<ThermalData> THERMAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Reg.id("thermal"), DataComponentType.<ThermalData>builder()
                    .persistent(ThermalData.CODEC).networkSynchronized(ThermalData.STREAM_CODEC).build());

    public static final DataComponentType<MineralData> MINERAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Reg.id("mineral"), DataComponentType.<MineralData>builder()
                    .persistent(MineralData.CODEC).networkSynchronized(MineralData.STREAM_CODEC).cacheEncoding().build());

    public static final DataComponentType<Infusions> INFUSIONS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Reg.id("infusions"), DataComponentType.<Infusions>builder()
                    .persistent(Infusions.CODEC).networkSynchronized(Infusions.STREAM_CODEC).build());

    public static final DataComponentType<RelicData> RELIC = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Reg.id("relic"), DataComponentType.<RelicData>builder()
                    .persistent(RelicData.CODEC).networkSynchronized(RelicData.STREAM_CODEC).build());

    public static void init() {
    }
}
