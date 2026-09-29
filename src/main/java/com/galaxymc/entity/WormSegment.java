package com.galaxymc.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * One link of a worm's body. Segments have no mind of their own: the head places them every tick,
 * damage dealt to them is passed on to the head, and they are never saved - a reloaded worm simply
 * grows a fresh body. One entity type serves every worm species; the species index is synced so the
 * client knows which body to draw.
 */
public class WormSegment extends Mob implements GalaxyCreature {
    public static final EntityDataAccessor<Integer> SPECIES = SynchedEntityData.defineId(WormSegment.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(WormSegment.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(WormSegment.class, EntityDataSerializers.INT);

    private WormHead head;

    public WormSegment(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPECIES, 0);
        builder.define(INDEX, 0);
        builder.define(COLOR, -1);
    }

    public void bind(WormHead head, int index, int count, double size) {
        this.head = head;
        entityData.set(SPECIES, head.species().index);
        entityData.set(INDEX, index);
        entityData.set(COLOR, head.strainColor());
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(Math.max(0.0625, Math.min(16.0, size)));
        }
        refreshDimensions();
    }

    @Override
    public Species species() {
        return SpeciesRegistry.byIndex(entityData.get(SPECIES));
    }

    @Override
    public boolean countsTowardsCap() {
        return false;
    }

    public int index() {
        return entityData.get(INDEX);
    }

    public int strainColor() {
        return entityData.get(COLOR);
    }

    public WormHead head() {
        return head;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        } else if (head == null || head.isRemoved()) {
            discard();
        }
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (head != null && !head.isRemoved() && source.getEntity() != head) {
            return head.hurtServer(level, source, amount * 0.8F);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    }
}
