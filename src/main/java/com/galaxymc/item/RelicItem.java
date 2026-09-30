package com.galaxymc.item;

import com.galaxymc.entity.Voices;
import com.galaxymc.registry.ModComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** An alien relic. Right-click to unleash its power; stronger relics recharge faster and hit harder. */
public class RelicItem extends Item {
    public RelicItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RelicData relic = stack.get(ModComponents.RELIC);
        if (relic == null) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            activate(server, sp, relic);
            player.getCooldowns().addCooldown(stack, relic.cooldownTicks());
        }
        return InteractionResult.SUCCESS;
    }

    private static List<LivingEntity> around(ServerLevel level, Player player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof Player));
    }

    private static void activate(ServerLevel level, ServerPlayer player, RelicData relic) {
        int lv = relic.level();
        Vec3 eye = player.getEyePosition();
        switch (relic.power()) {
            case BLINK -> {
                Vec3 look = player.getLookAngle();
                double reach = 8 + lv * 2;
                Vec3 best = null;
                for (double d = reach; d > 1; d -= 0.5) {
                    Vec3 at = eye.add(look.scale(d));
                    BlockPos feet = BlockPos.containing(at.x, at.y - 1.5, at.z);
                    if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                        best = new Vec3(at.x, feet.getY(), at.z);
                        break;
                    }
                }
                if (best != null) {
                    level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.8, 0.3, 0.3);
                    player.teleportTo(best.x, best.y, best.z);
                    player.resetFallDistance();
                    Voices.play(level, player.blockPosition(), "entity.enderman.teleport", SoundSource.PLAYERS, 1.0F, 1.2F);
                }
            }
            case NOVA -> {
                for (LivingEntity e : around(level, player, 4 + lv)) {
                    Vec3 away = e.position().subtract(player.position()).normalize();
                    DamageSource source = level.damageSources().playerAttack(player);
                    float damage = 3 + lv * 1.5F;
                    e.knockback(1.2 + lv * 0.25, -away.x, -away.z, source, damage);
                    e.hurtServer(level, source, damage);
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
                Voices.play(level, player.blockPosition(), "entity.warden.sonic_boom", SoundSource.PLAYERS, 0.8F, 1.3F);
            }
            case MENDING -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100 + lv * 20, Math.min(3, lv / 3)));
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(), 6, 0.5, 0.5, 0.5, 0.1);
                Voices.play(level, player.blockPosition(), "block.amethyst_block.resonate", SoundSource.PLAYERS, 1.0F, 1.5F);
            }
            case ASCENT -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 + lv * 6, 2));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200 + lv * 20, 0));
                Voices.play(level, player.blockPosition(), "entity.phantom.flap", SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case FROST -> {
                for (LivingEntity e : around(level, player, 5 + lv)) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100 + lv * 20, 4));
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
                    level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
                }
                Voices.play(level, player.blockPosition(), "block.glass.break", SoundSource.PLAYERS, 1.0F, 0.6F);
            }
            case FLARE -> {
                for (LivingEntity e : around(level, player, 5 + lv)) {
                    e.igniteForSeconds(3 + lv);
                    level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 1, e.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
                }
                Voices.play(level, player.blockPosition(), "entity.blaze.shoot", SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case PHASE -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200 + lv * 40, 0));
                Voices.play(level, player.blockPosition(), "entity.illusioner.mirror_move", SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            case SURGE -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200 + lv * 30, Math.min(3, lv / 3)));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200 + lv * 30, Math.min(3, lv / 3)));
                Voices.play(level, player.blockPosition(), "block.beacon.activate", SoundSource.PLAYERS, 1.0F, 1.6F);
            }
            case INSIGHT -> {
                for (LivingEntity e : around(level, player, 24 + lv * 6)) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200 + lv * 30, 0));
                }
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400 + lv * 40, 0));
                Voices.play(level, player.blockPosition(), "block.amethyst_cluster.break", SoundSource.PLAYERS, 1.0F, 1.8F);
            }
            case STARFALL -> {
                int strikes = 0;
                for (LivingEntity e : around(level, player, 10 + lv)) {
                    if (strikes++ >= 2 + lv / 2) {
                        break;
                    }
                    LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
                    bolt.setPos(e.getX(), e.getY(), e.getZ());
                    bolt.setCause(player);
                    level.addFreshEntity(bolt);
                }
            }
        }
    }
}
