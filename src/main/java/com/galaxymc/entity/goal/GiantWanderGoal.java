package com.galaxymc.entity.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Wandering for creatures far too big for vanilla pathfinding. Giants pick a distant point and simply
 * stride towards it with the move control, stepping over terrain with their huge step height, instead
 * of asking the path finder for a route it cannot compute for something thirty blocks wide.
 */
public class GiantWanderGoal extends Goal {
    private final Mob mob;
    private double tx;
    private double tz;
    private int ticks;

    public GiantWanderGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && mob.getRandom().nextInt(80) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && ticks > 0 && mob.distanceToSqr(tx, mob.getY(), tz) > 9.0;
    }

    @Override
    public void start() {
        double a = mob.getRandom().nextDouble() * Math.PI * 2.0;
        double r = 20 + mob.getRandom().nextDouble() * 40 + mob.getBbWidth() * 2;
        tx = mob.getX() + Math.cos(a) * r;
        tz = mob.getZ() + Math.sin(a) * r;
        ticks = 400;
    }

    @Override
    public void tick() {
        ticks--;
        int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tx), (int) Math.floor(tz));
        mob.getMoveControl().setWantedPosition(tx, y, tz, 0.7);
        mob.getLookControl().setLookAt(tx, y + mob.getEyeHeight(), tz);
    }
}
