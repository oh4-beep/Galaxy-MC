package com.galaxymc.entity.goal;

import com.galaxymc.entity.Voices;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How giants fight: stride straight at the target, and once it is within reach bring the whole body
 * down in a stomp that damages and flings everything nearby, not just the target.
 */
public class GiantMeleeGoal extends Goal {
    private final Mob mob;
    private int cooldown;

    public GiantMeleeGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) {
            return;
        }
        mob.getLookControl().setLookAt(t, 20.0F, 20.0F);
        double reach = mob.getBbWidth() * 0.6 + 2.5;
        double d2 = mob.distanceToSqr(t.getX(), mob.getY(), t.getZ());
        if (d2 > reach * reach * 0.6) {
            mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
        }
        if (--cooldown <= 0 && d2 <= reach * reach && mob.level() instanceof ServerLevel level) {
            cooldown = 30 + mob.getRandom().nextInt(20);
            stomp(level, reach);
        }
    }

    private void stomp(ServerLevel level, double reach) {
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        AABB area = mob.getBoundingBox().inflate(reach * 0.5, 1.0, reach * 0.5);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != mob && e.isAlive());
        for (LivingEntity e : hit) {
            if (mob.doHurtTarget(level, e)) {
                Vec3 away = e.position().subtract(mob.position()).normalize();
                e.push(away.x * 1.2, 0.6, away.z * 1.2);
                e.hurtMarked = true;
            }
        }
        BlockState ground = level.getBlockState(mob.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), mob.getX(), mob.getY() + 0.2, mob.getZ(),
                    40 + (int) (mob.getBbWidth() * 10), mob.getBbWidth() * 0.5, 0.2, mob.getBbWidth() * 0.5, 0.15);
        }
        var step = Voices.sound("entity.ravager.step");
        if (step != null) {
            level.playSound(null, mob.blockPosition(), step, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }
}
