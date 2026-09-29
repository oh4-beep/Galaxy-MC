package com.galaxymc.item;

import com.galaxymc.GalaxyMC;
import com.galaxymc.climate.Climate;
import com.galaxymc.climate.LifeSupportRegistry;
import com.galaxymc.climate.ThermalMaterials;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Reads the air: ambient temperature where you stand, what your suit can take, and how many more
 * degrees of insulation (one wool or one ice per degree) you would need to stitch in to be safe here.
 */
public class ThermometerItem extends Item {
    public ThermometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            PlanetProfile p = Planets.at(server.dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());
            if (p == null) {
                player.sendOverlayMessage(Component.translatable("message.galaxy_mc.thermometer.home").withStyle(ChatFormatting.GREEN));
            } else {
                double ambient = Climate.baseAt(server, p, player.blockPosition()) + Climate.localSources(server, player.blockPosition());
                int[] range = ThermalMaterials.suitRange(player);
                boolean sheltered = LifeSupportRegistry.covered(server, player.getX(), player.getY(), player.getZ());
                Component verdict;
                if (sheltered) {
                    verdict = Component.translatable("message.galaxy_mc.thermometer.sheltered").withStyle(ChatFormatting.AQUA);
                } else if (ambient < range[0]) {
                    int need = (int) Math.ceil(range[0] - ambient);
                    verdict = Component.translatable("message.galaxy_mc.thermometer.too_cold", need).withStyle(ChatFormatting.BLUE);
                } else if (ambient > range[1]) {
                    int need = (int) Math.ceil(ambient - range[1]);
                    verdict = Component.translatable("message.galaxy_mc.thermometer.too_hot", need).withStyle(ChatFormatting.RED);
                } else {
                    verdict = Component.translatable("message.galaxy_mc.thermometer.safe").withStyle(ChatFormatting.GREEN);
                }
                player.sendOverlayMessage(Component.translatable("message.galaxy_mc.thermometer.reading", Math.round(ambient),
                        range[0], range[1]).append(" ").append(verdict));
            }
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }
}
