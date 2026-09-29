package com.galaxymc.climate;

import com.galaxymc.GalaxyMC;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.network.ClimatePayload;
import com.galaxymc.registry.Reg;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Server-side survival loop for planets: temperature stress, its consequences, and planetary gravity.
 *
 * <p>Stress is a single signed meter (-100 frozen .. +100 cooked). Outside the suit's safe range it
 * climbs at a rate proportional to how far outside you are; inside it relaxes back to zero. Cold
 * drives the vanilla freezing overlay, heat drives the HUD's heat haze, and past the thresholds both
 * start to hurt - harder the further you are beyond your gear.
 */
public final class ClimateTicker {
    private ClimateTicker() {}

    public static final ResourceKey<DamageType> HYPOTHERMIA = ResourceKey.create(Registries.DAMAGE_TYPE, Reg.id("hypothermia"));
    public static final ResourceKey<DamageType> HEATSTROKE = ResourceKey.create(Registries.DAMAGE_TYPE, Reg.id("heatstroke"));
    private static final Identifier GRAVITY_ID = Reg.id("planet_gravity");
    private static final Identifier FALL_ID = Reg.id("planet_safe_fall");

    private static final class State {
        double stress;
        double local;
        long lastScan = -1000;
        boolean warned;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public static double stress(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? 0 : s.stress;
    }

    public static void remove(UUID id) {
        STATES.remove(id);
    }

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, time);
        }
    }

    private static void tickPlayer(ServerPlayer player, long time) {
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        ServerLevel level = player.level();
        PlanetProfile p = Planets.at(level.dimension(), GalaxyMC.galaxySeed(), player.getX(), player.getZ());

        if (time % 20 == 0) {
            applyGravity(player, p);
        }
        boolean exempt = player.isCreative() || player.isSpectator();

        if (s.stress < -1 && !exempt) {
            int frozen = (int) Math.min(139, -s.stress * 1.39);
            player.setTicksFrozen(Math.max(player.getTicksFrozen(), frozen));
        }
        if (time % 10 != 0) {
            return;
        }

        if (p == null || exempt) {
            s.stress = relax(s.stress, 8);
            ServerPlayNetworking.send(player, p == null ? ClimatePayload.INACTIVE
                    : new ClimatePayload(true, p.name, (float) p.baseTemp, -9999, 9999, 0, false, (float) p.gravity));
            return;
        }

        BlockPos pos = player.blockPosition();
        if (time - s.lastScan >= 40) {
            s.local = Climate.localSources(level, pos);
            s.lastScan = time;
        }
        double ambient = Climate.baseAt(level, p, pos) + s.local;
        boolean lifeSupport = LifeSupportRegistry.covered(level, player.getX(), player.getY(), player.getZ());
        int[] range = ThermalMaterials.suitRange(player);

        if (lifeSupport) {
            s.stress = relax(s.stress, 10);
        } else if (ambient < range[0]) {
            double exposure = range[0] - ambient;
            s.stress = Math.max(-100, s.stress - (1.5 + exposure / 6.0));
        } else if (ambient > range[1]) {
            double exposure = ambient - range[1];
            s.stress = Math.min(100, s.stress + (1.5 + exposure / 6.0));
        } else {
            s.stress = relax(s.stress, 5);
        }

        consequences(player, level, s, ambient, range);
        ServerPlayNetworking.send(player, new ClimatePayload(true, p.name, (float) ambient, range[0], range[1],
                (float) s.stress, lifeSupport, (float) p.gravity));
    }

    private static double relax(double stress, double amount) {
        if (stress > 0) {
            return Math.max(0, stress - amount);
        }
        return Math.min(0, stress + amount);
    }

    private static void consequences(ServerPlayer player, ServerLevel level, State s, double ambient, int[] range) {
        double a = Math.abs(s.stress);
        boolean cold = s.stress < 0;
        if (a >= 40 && !s.warned) {
            s.warned = true;
            player.sendOverlayMessage(Component.translatable(cold ? "message.galaxy_mc.freezing" : "message.galaxy_mc.overheating"));
        } else if (a < 20) {
            s.warned = false;
        }
        if (a >= 60) {
            player.addEffect(new MobEffectInstance(cold ? MobEffects.SLOWNESS : MobEffects.WEAKNESS, 40, a >= 90 ? 1 : 0, true, false));
            if (cold) {
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 0, true, false));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, true, false));
            }
        }
        if (a >= 75 && level.getGameTime() % 20 == 0) {
            double exposure = cold ? range[0] - ambient : ambient - range[1];
            float damage = (float) Math.min(20.0, 1.0 + Math.max(0, exposure) / 40.0);
            if (a >= 100) {
                damage *= 2.0F;
            }
            DamageSource src = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(cold ? HYPOTHERMIA : HEATSTROKE));
            player.hurtServer(level, src, damage);
            if (!cold && exposure > 250) {
                player.igniteForSeconds(4.0F);
            }
        }
    }

    private static void applyGravity(ServerPlayer player, PlanetProfile p) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        AttributeInstance fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (gravity == null || fall == null) {
            return;
        }
        if (p == null || Math.abs(p.gravity - 1.0) < 0.01) {
            gravity.removeModifier(GRAVITY_ID);
            fall.removeModifier(FALL_ID);
            return;
        }
        double g = Math.max(0.25, Math.min(2.5, p.gravity));
        gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, g - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        fall.addOrUpdateTransientModifier(new AttributeModifier(FALL_ID, 1.0 / g - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
