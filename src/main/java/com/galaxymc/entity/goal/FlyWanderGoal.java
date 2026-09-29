package com.galaxymc.entity.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/** Picks random points in the open sky above the terrain and flies (or drifts) to them. */
public class FlyWanderGoal extends Goal {
    private final Mob mob;
    private final boolean drift;
    private double tx;
    private double ty;
    private double tz;
    private int ticks;

    public FlyWanderGoal(Mob mob, boolean drift) {
        this.mob = mob;
        this.drift = drift;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && mob.getRandom().nextInt(drift ? 60 : 20) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && ticks > 0 && mob.distanceToSqr(tx, ty, tz) > 4.0;
    }

    @Override
    public void start() {
        double a = mob.getRandom().nextDouble() * Math.PI * 2.0;
        double r = (drift ? 6 : 12) + mob.getRandom().nextDouble() * (drift ? 10 : 24) + mob.getBbWidth();
        tx = mob.getX() + Math.cos(a) * r;
        tz = mob.getZ() + Math.sin(a) * r;
        int ground = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(tx), (int) Math.floor(tz));
        double lift = (drift ? 3 : 5) + mob.getRandom().nextDouble() * (drift ? 8 : 16) + mob.getBbHeight();
        ty = Math.max(ground + lift, mob.level().getMinY() + 4);
        ticks = 200;
    }

    @Override
    public void tick() {
        ticks--;
        mob.getMoveControl().setWantedPosition(tx, ty, tz, drift ? 0.6 : 1.0);
    }
}
