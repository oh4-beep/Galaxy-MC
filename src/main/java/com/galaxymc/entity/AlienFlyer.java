package com.galaxymc.entity;

import com.galaxymc.entity.goal.FlyAttackGoal;
import com.galaxymc.entity.goal.FlyWanderGoal;
import com.galaxymc.entity.goal.SpitAttackGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Winged and floating creatures: gliders, rays, wyverns, moths, jellies, eyes and crystal sprites. They
 * never touch the ground if they can help it; floaters drift slowly and bob, flyers swoop.
 */
public class AlienFlyer extends AlienMob {
    public AlienFlyer(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, species != null && species.giant ? 6 : 20, true);
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        Species s = SpeciesRegistry.of(getType());
        if (s == null) {
            return;
        }
        if (s.attack == Species.Attack.SPIT) {
            goalSelector.addGoal(2, new SpitAttackGoal(this, s.spitRange));
        }
        if (s.attack == Species.Attack.MELEE && !s.passive()) {
            goalSelector.addGoal(3, new FlyAttackGoal(this));
        }
        goalSelector.addGoal(5, new FlyWanderGoal(this, s.kind == Species.Kind.FLOATER));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        if (!s.passive()) {
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
        }
        if (s.hostile()) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && species != null && species.kind == Species.Kind.FLOATER && getTarget() == null) {
            // Floaters bob gently in place between drifts.
            setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount * 0.07 + getId()) * 0.004, 0));
        }
    }
}
