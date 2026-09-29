package com.galaxymc.entity.goal;

import com.galaxymc.entity.AlienMob;
import java.util.EnumSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Mimics sit perfectly still, disguised as a boulder or a lump of ice, until something walks right up
 * to them - or hits them. Then the disguise drops and the goal ends, freeing the attack goals.
 */
public class AmbushGoal extends Goal {
    private final AlienMob mob;

    public AmbushGoal(AlienMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean prey() {
        Player p = mob.level().getNearestPlayer(mob, 3.5);
        return p != null && !p.isCreative() && !p.isSpectator();
    }

    @Override
    public boolean canUse() {
        return mob.getLastHurtByMob() == null && !prey() && mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.setDisguised(true);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        mob.setDisguised(false);
    }
}
