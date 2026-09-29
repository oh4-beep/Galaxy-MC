package com.galaxymc.entity.goal;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Dive attacks: swoop at the target, strike on contact, pull up and circle round for another pass. */
public class FlyAttackGoal extends Goal {
    private final Mob mob;
    private int cooldown;
    private int climb;

    public FlyAttackGoal(Mob mob) {
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
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (climb > 0) {
            climb--;
            Vec3 away = mob.position().subtract(t.position()).normalize();
            mob.getMoveControl().setWantedPosition(mob.getX() + away.x * 6, t.getY() + 6 + mob.getBbHeight(), mob.getZ() + away.z * 6, 1.1);
            return;
        }
        mob.getMoveControl().setWantedPosition(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1.3);
        double reach = mob.getBbWidth() * 0.8 + 1.4;
        if (--cooldown <= 0 && mob.distanceToSqr(t) < reach * reach && mob.level() instanceof ServerLevel level) {
            cooldown = 20;
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            mob.doHurtTarget(level, t);
            climb = 25 + mob.getRandom().nextInt(20);
        }
    }
}
