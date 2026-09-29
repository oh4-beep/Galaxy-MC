package com.galaxymc.entity;

import com.galaxymc.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The head of a segmented creature: sandworms, burrowers, serpents, centipedes, eels and the vast
 * Worldeater and Leviathan.
 *
 * <p>The head flies its own course instead of using pathfinding: it holds a velocity and turns it a
 * little towards a goal each tick (giants turn slowly, so they carve wide arcs). Burrowers swim through
 * rock, hunt from below and breach up through their prey before diving back; surface crawlers hug the
 * ground; swimmers stay in their lake or lava sea. The body is a chain of {@link WormSegment}s that each
 * keep a fixed distance from the one in front, which is all it takes for a two-hundred-block worm to
 * move like one animal.
 */
public class WormHead extends AlienMob {
    private enum Phase { WANDER, HUNT, BREACH, DIVE }

    private final List<WormSegment> segments = new ArrayList<>();
    private final Map<Integer, Long> lastHit = new HashMap<>();
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 wanderGoal;
    private Phase phase = Phase.WANDER;
    private int phaseTicks;
    private Vec3 lastWet;
    private boolean wasInside;

    public WormHead(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        Species s = SpeciesRegistry.of(getType());
        if (s == null || s.passive()) {
            return;
        }
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        if (s.hostile()) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        }
    }

    @Override
    public void travel(Vec3 input) {
        // Movement is steered manually in aiStep.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    public List<WormSegment> segments() {
        return segments;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            return;
        }
        if (species == null) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (segments.isEmpty()) {
            grow(level);
        }
        steer(level);
        Vec3 next = position().add(velocity);
        if (species.wormMode == Species.WormMode.SURFACE) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(next.x), Mth.floor(next.z));
            next = new Vec3(next.x, Mth.lerp(0.35, next.y, ground + 0.02), next.z);
        }
        next = new Vec3(next.x, Mth.clamp(next.y, level.getMinY() + 6, level.getMaxY() - 4), next.z);
        setDeltaMovement(next.subtract(position()));
        setPos(next.x, next.y, next.z);
        face(this, velocity);
        follow(level);
        bite(level);
        effects(level);
    }

    private double speedNow() {
        double base = species.movementSpeed();
        return getTarget() != null ? base * 1.3 : base * 0.65;
    }

    private double segmentLength() {
        return species.width * getScale() * species.spacing;
    }

    private void grow(ServerLevel level) {
        Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-1);
        for (int i = 0; i < species.segments; i++) {
            WormSegment seg = ModEntities.WORM_SEGMENT.create(level, EntitySpawnReason.TRIGGERED);
            if (seg == null) {
                return;
            }
            double taper = 1.0 - 0.55 * Math.pow((i + 1.0) / (species.segments + 1.0), 2.2);
            seg.bind(this, i, species.segments, species.width * getScale() * taper);
            Vec3 p = position().add(back.scale((i + 1) * segmentLength()));
            seg.setPos(p.x, p.y, p.z);
            level.addFreshEntity(seg);
            segments.add(seg);
        }
    }

    private void steer(ServerLevel level) {
        LivingEntity target = getTarget();
        Vec3 pos = position();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
        double depth = 4 + getBbHeight() * 1.5;
        Vec3 goal;
        if (species.wormMode == Species.WormMode.SWIM) {
            goal = swimGoal(level, target, pos);
        } else if (target == null || !target.isAlive() || target.distanceToSqr(this) > 90 * 90) {
            phase = Phase.WANDER;
            if (wanderGoal == null || pos.distanceToSqr(wanderGoal) < 16 || random.nextInt(200) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 24 + random.nextDouble() * 48;
                double gx = pos.x + Math.cos(a) * r;
                double gz = pos.z + Math.sin(a) * r;
                int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(gx), Mth.floor(gz));
                boolean surface = species.wormMode == Species.WormMode.SURFACE || random.nextInt(6) == 0;
                wanderGoal = new Vec3(gx, surface ? gy + 1 + getBbHeight() : gy - depth, gz);
            }
            goal = wanderGoal;
        } else if (species.wormMode == Species.WormMode.SURFACE) {
            goal = target.position();
        } else {
            goal = burrowGoal(level, target, pos, depth);
        }
        Vec3 desired = goal.subtract(pos);
        if (desired.lengthSqr() < 1.0E-6) {
            return;
        }
        desired = desired.normalize();
        double turn = species.giant ? 0.045 : 0.14;
        double speed = speedNow();
        velocity = velocity.lengthSqr() < 1.0E-6 ? desired.scale(speed)
                : velocity.normalize().scale(1.0 - turn).add(desired.scale(turn)).normalize().scale(speed);
        if (species.wormMode == Species.WormMode.BURROW && pos.y > ground + getBbHeight() * 2 && phase != Phase.BREACH) {
            // Out in the open air: gravity wins, the worm arcs back down.
            velocity = velocity.add(0, -0.04 - speed * 0.05, 0);
        }
    }

    private Vec3 burrowGoal(ServerLevel level, LivingEntity target, Vec3 pos, double depth) {
        int tGround = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(target.getX()), Mth.floor(target.getZ()));
        double horizontal = Math.hypot(target.getX() - pos.x, target.getZ() - pos.z);
        phaseTicks++;
        switch (phase) {
            case WANDER, HUNT -> {
                phase = Phase.HUNT;
                if (horizontal < 6 + getBbWidth() * 1.2 && pos.y < target.getY()) {
                    phase = Phase.BREACH;
                    phaseTicks = 0;
                }
                return new Vec3(target.getX(), tGround - depth, target.getZ());
            }
            case BREACH -> {
                if (pos.y > target.getY() + 3 + getBbHeight() || phaseTicks > 80) {
                    phase = Phase.DIVE;
                    phaseTicks = 0;
                }
                return target.position().add(0, 4 + getBbHeight() * 1.5, 0);
            }
            default -> {
                if (phaseTicks > 50 + getBbWidth() * 4) {
                    phase = Phase.HUNT;
                    phaseTicks = 0;
                }
                Vec3 fwd = velocity.lengthSqr() > 0 ? new Vec3(velocity.x, 0, velocity.z).normalize() : Vec3.ZERO;
                return pos.add(fwd.scale(12)).add(0, -(pos.y - (tGround - depth * 1.5)), 0);
            }
        }
    }

    private Vec3 swimGoal(ServerLevel level, LivingEntity target, Vec3 pos) {
        BlockPos here = BlockPos.containing(pos);
        boolean wet = !level.getFluidState(here).isEmpty();
        if (wet) {
            lastWet = pos;
        } else if (lastWet != null) {
            return lastWet;
        }
        double surface = pos.y;
        BlockPos.MutableBlockPos m = here.mutable();
        for (int i = 0; i < 48 && !level.getFluidState(m).isEmpty(); i++) {
            m.move(0, 1, 0);
            surface = m.getY();
        }
        Vec3 goal;
        if (target != null && target.isAlive() && target.distanceToSqr(this) < 48 * 48) {
            goal = target.position();
        } else {
            if (wanderGoal == null || pos.distanceToSqr(wanderGoal) < 9 || random.nextInt(160) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                wanderGoal = pos.add(Math.cos(a) * 16, random.nextDouble() * 6 - 4, Math.sin(a) * 16);
            }
            goal = wanderGoal;
        }
        return new Vec3(goal.x, Math.min(goal.y, surface - 0.6 - getBbHeight() * 0.5), goal.z);
    }

    static void face(Entity e, Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
        e.setYRot(yaw);
        e.setXRot(pitch);
        if (e instanceof LivingEntity l) {
            l.yBodyRot = yaw;
            l.yHeadRot = yaw;
        }
    }

    /** Each segment keeps exactly one segment-length behind the one in front of it. */
    private void follow(ServerLevel level) {
        Vec3 prev = position();
        double len = segmentLength();
        for (int i = 0; i < segments.size(); i++) {
            WormSegment seg = segments.get(i);
            if (seg.isRemoved()) {
                continue;
            }
            Vec3 p = seg.position();
            Vec3 d = p.subtract(prev);
            if (d.lengthSqr() < 1.0E-6) {
                d = velocity.lengthSqr() > 0 ? velocity.scale(-1) : new Vec3(0, 0, 1);
            }
            double taper = 1.0 - 0.55 * Math.pow((i + 1.0) / (segments.size() + 1.0), 2.2);
            Vec3 np = prev.add(d.normalize().scale(len * (0.6 + 0.4 * taper)));
            seg.setDeltaMovement(np.subtract(p));
            seg.setPos(np.x, np.y, np.z);
            face(seg, prev.subtract(np));
            prev = np;
        }
    }

    /** Anything the head ploughs into gets hurt; each victim at most twice a second. */
    private void bite(ServerLevel level) {
        AABB box = getBoundingBox().inflate(0.4);
        long now = level.getGameTime();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive()
                && !(e instanceof WormSegment) && !(e instanceof WormHead))) {
            Long last = lastHit.get(e.getId());
            if (last != null && now - last < 10) {
                continue;
            }
            lastHit.put(e.getId(), now);
            if (doHurtTarget(level, e)) {
                Vec3 push = velocity.normalize().add(0, 0.5, 0);
                e.push(push.x * 0.8, push.y * 0.8, push.z * 0.8);
                e.hurtMarked = true;
            }
        }
        if (lastHit.size() > 64) {
            lastHit.clear();
        }
    }

    /** Dust and rumbling where the worm breaks the ground. */
    private void effects(ServerLevel level) {
        BlockPos at = blockPosition();
        BlockState st = level.getBlockState(at);
        boolean inside = !st.isAir() && st.getFluidState().isEmpty();
        if (inside != wasInside && species.wormMode == Species.WormMode.BURROW) {
            BlockState dust = inside ? st : level.getBlockState(at.below());
            if (!dust.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), getX(), getY(), getZ(),
                        30 + (int) (getBbWidth() * 20), getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0.2);
                var sound = Voices.sound("block.rooted_dirt.break");
                if (sound != null) {
                    level.playSound(null, at, sound, SoundSource.HOSTILE, (float) Math.min(4.0, 1.0 + getBbWidth()), 0.6F);
                }
            }
        }
        wasInside = inside;
        if (inside && tickCount % 4 == 0 && species.wormMode == Species.WormMode.BURROW) {
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            if (surface.getY() - getY() < 8 + getBbHeight()) {
                BlockState top = level.getBlockState(surface.below());
                if (!top.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top), getX(), surface.getY() + 0.1, getZ(),
                            4 + (int) getBbWidth(), getBbWidth() * 0.5, 0.05, getBbWidth() * 0.5, 0.05);
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        for (WormSegment seg : segments) {
            seg.discard();
        }
        segments.clear();
    }
}
