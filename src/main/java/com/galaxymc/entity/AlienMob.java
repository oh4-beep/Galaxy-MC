package com.galaxymc.entity;

import com.galaxymc.GalaxyMC;
import com.galaxymc.entity.goal.AmbushGoal;
import com.galaxymc.entity.goal.GiantMeleeGoal;
import com.galaxymc.entity.goal.GiantWanderGoal;
import com.galaxymc.entity.goal.SpitAttackGoal;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.Planets;
import com.galaxymc.item.Relics;
import com.galaxymc.mineral.Minerals;
import com.galaxymc.registry.Reg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A ground-dwelling Galaxy MC creature. Everything about it - goals, stats, element, voice - comes from
 * its {@link Species}; everything that differs between planets comes from its {@link Strain}.
 *
 * <p>Elements add an on-hit effect: frost slows and freezes, ember and magma burn, toxic and moss poison,
 * dusk and shadow blind, glow and aurora make you visible, storm occasionally calls lightning, and void
 * creatures blink to their prey's side.
 */
public class AlienMob extends PathfinderMob implements GalaxyCreature {
    public static final EntityDataAccessor<Integer> STRAIN_COLOR = SynchedEntityData.defineId(AlienMob.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DISGUISED = SynchedEntityData.defineId(AlienMob.class, EntityDataSerializers.BOOLEAN);
    private static final Identifier STRAIN_SIZE = Reg.id("strain_size");
    private static final Identifier STRAIN_HEALTH = Reg.id("strain_health");
    private static final Identifier STRAIN_DAMAGE = Reg.id("strain_damage");

    protected final Species species;
    private String strainName = "";
    private boolean strainApplied;
    private int blinkCooldown;

    public AlienMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.species = SpeciesRegistry.of(type);
        if (species != null) {
            this.xpReward = species.giant ? 50 + (int) (species.health / 10) : 3 + (int) (species.health / 8);
        }
    }

    @Override
    public Species species() {
        return species;
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        Species s = SpeciesRegistry.of(getType());
        if (s == null) {
            return;
        }
        goalSelector.addGoal(0, new FloatGoal(this));
        if (s.ambush) {
            goalSelector.addGoal(1, new AmbushGoal(this));
        }
        if (s.passive()) {
            goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        }
        if (s.giant) {
            if (s.attack != Species.Attack.NONE) {
                goalSelector.addGoal(2, new GiantMeleeGoal(this));
            }
            goalSelector.addGoal(5, new GiantWanderGoal(this));
        } else {
            if (s.leap) {
                goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.42F));
            }
            if (s.attack == Species.Attack.SPIT) {
                goalSelector.addGoal(2, new SpitAttackGoal(this, s.spitRange));
            }
            if (s.attack != Species.Attack.NONE && !s.passive()) {
                goalSelector.addGoal(3, new MeleeAttackGoal(this, s.charge ? 1.5 : 1.15, true));
            }
            if (s.kind != Species.Kind.STATIC) {
                goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85));
            }
        }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        if (!s.passive()) {
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
        }
        if (s.hostile()) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        Species s = SpeciesRegistry.of(getType());
        if (s != null && s.climb) {
            return new WallClimberNavigation(this, level);
        }
        return super.createNavigation(level);
    }

    @Override
    public boolean onClimbable() {
        return species != null && species.climb && horizontalCollision || super.onClimbable();
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STRAIN_COLOR, -1);
        builder.define(DISGUISED, false);
    }

    public int strainColor() {
        return entityData.get(STRAIN_COLOR);
    }

    public boolean disguised() {
        return entityData.get(DISGUISED);
    }

    public void setDisguised(boolean v) {
        entityData.set(DISGUISED, v);
    }

    /** Adapts this creature to the planet it spawned on. Idempotent. */
    public void applyStrain(Strain strain) {
        if (strainApplied || strain.isNone()) {
            return;
        }
        strainApplied = true;
        strainName = strain.name();
        entityData.set(STRAIN_COLOR, strain.color());
        modify(Attributes.SCALE, STRAIN_SIZE, strain.size() - 1.0);
        modify(Attributes.MAX_HEALTH, STRAIN_HEALTH, strain.might() - 1.0);
        modify(Attributes.ATTACK_DAMAGE, STRAIN_DAMAGE, strain.might() - 1.0);
        setHealth(getMaxHealth());
    }

    private void modify(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount) {
        AttributeInstance inst = getAttribute(attribute);
        if (inst != null && Math.abs(amount) > 1e-4) {
            inst.removeModifier(id);
            inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("galaxy_strain", strainName);
        output.putInt("galaxy_strain_color", strainColor());
        output.putBoolean("galaxy_strain_applied", strainApplied);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        strainName = input.getStringOr("galaxy_strain", "");
        entityData.set(STRAIN_COLOR, input.getIntOr("galaxy_strain_color", -1));
        strainApplied = input.getBooleanOr("galaxy_strain_applied", false);
    }

    @Override
    protected Component getTypeName() {
        Component base = super.getTypeName();
        if (strainName == null || strainName.isEmpty()) {
            return base;
        }
        return Component.literal(strainName + " ").append(base);
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() || species == null) {
            return;
        }
        if (species.hops && onGround() && getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 && random.nextInt(8) == 0) {
            jumpFromGround();
        }
        LivingEntity target = getTarget();
        if ("void".equals(species.effect) && target != null && --blinkCooldown <= 0 && distanceToSqr(target) > 16.0 && random.nextInt(40) == 0) {
            blinkCooldown = 100;
            double a = random.nextDouble() * Math.PI * 2.0;
            if (randomTeleport(target.getX() + Math.cos(a) * 2.5, target.getY(), target.getZ() + Math.sin(a) * 2.5, true)) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 30,
                        0.4, 0.6, 0.4, 0.05);
            }
        }
        if (tickCount % 10 == 0 && ("glow".equals(species.effect) || "fire".equals(species.effect))) {
            ((ServerLevel) level()).sendParticles("fire".equals(species.effect) ? ParticleTypes.SMALL_FLAME : ParticleTypes.GLOW,
                    getX(), getY() + getBbHeight() * 0.8, getZ(), 1, getBbWidth() * 0.3, 0.2, getBbWidth() * 0.3, 0.0);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            applyElement(level, this, living, species);
        }
        return hit;
    }

    /** The element's on-hit effect; shared with spit attacks and worms. */
    public static void applyElement(ServerLevel level, LivingEntity attacker, LivingEntity victim, Species species) {
        if (species == null || species.effect.isEmpty()) {
            return;
        }
        switch (species.effect) {
            case "frost" -> {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                victim.setTicksFrozen(Math.min(300, victim.getTicksFrozen() + 60));
            }
            case "fire" -> victim.igniteForSeconds(4.0F);
            case "poison" -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            case "blind" -> victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            case "glow" -> victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
            case "storm" -> {
                if (level.getRandom().nextInt(12) == 0) {
                    LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
                    bolt.setPos(victim.getX(), victim.getY(), victim.getZ());
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean isPushable() {
        return species == null || !species.giant && species.kind != Species.Kind.STATIC && super.isPushable();
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        if (species != null && (species.giant || species.flies())) {
            return false;
        }
        return super.causeFallDamage(fallDistance, damageModifier, source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return species == null || disguised() ? null : Voices.sound(Voices.of(species.voice).ambient());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return species == null ? null : Voices.sound(Voices.of(species.voice).hurt());
    }

    @Override
    protected SoundEvent getDeathSound() {
        return species == null ? null : Voices.sound(Voices.of(species.voice).death());
    }

    @Override
    public float getVoicePitch() {
        float base = super.getVoicePitch();
        return species == null ? base : (float) (base * Math.pow(species.scale, -0.3) * (species.element.equals("void") ? 0.8 : 1.0));
    }

    @Override
    protected float getSoundVolume() {
        return species == null ? 1.0F : (float) Math.min(4.0, 0.8 + species.scale * 0.25);
    }

    // ------------------------------------------------------------------ loot

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (species == null || !species.giant) {
            return;
        }
        // Giants carry treasure from the worlds they roam: a relic and a clutch of the local minerals.
        PlanetProfile p = Planets.at(level.dimension(), GalaxyMC.galaxySeed(), getX(), getZ());
        int tier = p == null ? 1 : p.tier;
        spawnAtLocation(level, Relics.random(random.nextLong(), tier + 1));
        if (p != null) {
            spawnAtLocation(level, Minerals.stack(Minerals.forPlanet(p, random.nextInt(4)), 2 + random.nextInt(4)));
        }
    }
}
