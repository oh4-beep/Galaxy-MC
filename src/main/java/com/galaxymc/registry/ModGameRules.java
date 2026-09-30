package com.galaxymc.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Galaxy MC's game rules:
 * <ul>
 *   <li>{@code galaxy_mc:planet_hazards} - whether tornadoes, tsunamis, eruptions, meteor showers and
 *   lightning storms happen at all;</li>
 *   <li>{@code galaxy_mc:hazards_change_terrain} - whether they may move and place blocks (flung soil,
 *   flood water, lava and magma bombs, meteor craters). With it off they are purely a danger to
 *   whoever is caught in them.</li>
 * </ul>
 */
public final class ModGameRules {
    private ModGameRules() {}

    public static final GameRule<Boolean> PLANET_HAZARDS = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.MISC).buildAndRegister(Reg.id("planet_hazards"));
    public static final GameRule<Boolean> HAZARDS_CHANGE_TERRAIN = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.MISC).buildAndRegister(Reg.id("hazards_change_terrain"));

    public static void init() {
    }
}
