package com.galaxymc.entity.goal;

import com.galaxymc.entity.AlienMob;
import com.galaxymc.entity.GalaxyCreature;
import com.galaxymc.entity.Species;
import com.galaxymc.entity.Voices;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Ranged attack for spitters, scorpions, greys, eyes and hydras: holds its ground at range, then looses
 * a bolt of acid, frost, fire or void energy drawn as a particle beam. The bolt lands if the target is
 * still in sight, carrying the species' element with it.
 */
public class SpitAttackGoal extends Goal {
    private final Mob mob;
    private final double range;
    private int cooldown;

    public SpitAttackGoal(Mob mob, double range) {
        this.mob = mob;
        this.range = range;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= range * range && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        cooldown = 20;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--cooldown > 0) {
            return;
        }
        cooldown = 35 + mob.getRandom().nextInt(30);
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Species species = mob instanceof GalaxyCreature c ? c.species() : null;
        Vec3 from = mob.getEyePosition();
        Vec3 to = target.getEyePosition().add(0, -0.3, 0);
        ParticleOptions particle = particle(species);
        Vec3 step = to.subtract(from);
        int n = (int) Math.max(4, step.length() * 2.5);
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(step.scale(i / (double) n));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        var spit = Voices.sound("entity.llama.spit");
        if (spit != null) {
            level.playSound(null, mob.blockPosition(), spit, SoundSource.HOSTILE, 1.0F, 0.6F + mob.getRandom().nextFloat() * 0.4F);
        }
        if (mob.getRandom().nextFloat() < 0.85F) {
            float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (target.hurtServer(level, level.damageSources().mobAttack(mob), damage)) {
                AlienMob.applyElement(level, mob, target, species);
            }
        }
    }

    private static ParticleOptions particle(Species s) {
        if (s == null) {
            return ParticleTypes.SPIT;
        }
        return switch (s.effect) {
            case "frost" -> ParticleTypes.SNOWFLAKE;
            case "fire" -> ParticleTypes.FLAME;
            case "poison" -> ParticleTypes.ITEM_SLIME;
            case "void" -> ParticleTypes.REVERSE_PORTAL;
            case "glow" -> ParticleTypes.GLOW;
            case "storm" -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.SPIT;
        };
    }
}
