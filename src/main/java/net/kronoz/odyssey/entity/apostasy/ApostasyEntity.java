package net.kronoz.odyssey.entity.apostasy;

import net.kronoz.odyssey.entity.projectile.LaserProjectileEntity;
import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.*;

public class ApostasyEntity extends PathAwareEntity implements software.bernie.geckolib.animatable.GeoEntity {
    public enum Phase { P1, P2, P3, P4 }

    private static final double RING1_R = 2.4;
    private static final double RING2_R = 3.1;
    private static final double RING3_R = 3.8;
    private static final double RINGS_Y = 1.6;
    private static final int RING_SHOT_INTERVAL = 20;
    private static final int HUGE_LASER_INTERVAL = 65;
    private static final double HUGE_LASER_SPEED = 2.75;
    private static final double SMALL_LASER_SPEED = 2.15;

    private static final int EYE_BURST_INTERVAL = 6;
    private static final int EYE_BURST_LIFETIME = 60;
    private static final double EYE_BURST_DAMAGE = 4.0;
    private static final double EYE_MUZZLE_FORWARD = 0.60;
    private static final double EYE_ALIGN_DEG = 22.5;

    private int eyeBurstShotsLeft = 0;
    private int eyeBurstTimer = 0;
    private int eyeBurstTargetId = -1;

    private static final int TELEGRAPH_TICKS = 20;
    private static final int P2_SINGLE_COOLDOWN = 60;
    private static final int P3_ZONE_COOLDOWN = 150;
    private static final int PHASE3_BURST = 20;
    private static final int PHASE3_MIN_R = 10, PHASE3_MAX_R = 50;
    private static final double KEEP_OUT_RADIUS = 20.0;
    private static final double KEEP_OUT_PUSH = 20.4;
    private static final double LINE_TRIGGER_RANGE = 20.0;

    private static final double HOVER_OFFSET = 8.0;
    private static final double HOVER_K = 0.35;
    private static final double HOVER_D = 0.50;
    private static final double HOVER_MAX_VY = 0.5;
    private static final double HOVER_SAMPLE_UP = 24.0;
    private static final double HOVER_SAMPLE_DOWN = 64.0;

    private int ringShotCooldown = 10;
    private int hugeLaserCooldown = 40;
    private int lightningCooldown = 40;
    private int glowShotCooldown = 0;          // ticks
    private int glowMuzzleIndex = 0;
    private Vec3d lastShotDir = Vec3d.ZERO;

    private int shockwaveCooldown = 0;
    private static final int SHOCKWAVE_COOLDOWN_P1 = 120;
    private static final int SHOCKWAVE_COOLDOWN_P2 = 90;
    private static final int SHOCKWAVE_COOLDOWN_P3 = 70;

    private Phase phase = Phase.P1;
    private double hoverVy = 0.0;
    private double lastTargetY = Double.NaN;

    private static final class PendingStrike {
        final BlockPos pos;
        int ticks;
        PendingStrike(BlockPos pos, int ticks) { this.pos = pos; this.ticks = ticks; }
    }
    private final List<PendingStrike> pendingStrikes = new ArrayList<>();

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    public ApostasyEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 50;
        this.ignoreCameraFrustum = true;
        this.setNoGravity(true);
        this.noClip = false;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 600.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    private void updatePhase() {
        float pct = getHealth() / getMaxHealth();
        Phase newPhase = pct > 0.75f ? Phase.P1 : pct > 0.50f ? Phase.P2 : pct > 0.25f ? Phase.P3 : Phase.P4;
        if (newPhase != phase) phase = newPhase;
    }
    public Phase getPhase() { return phase; }

    @Override
    protected void initGoals() { this.goalSelector.add(0, new BrainGoal()); }

    private class BrainGoal extends Goal {
        BrainGoal() { setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() { return true; }
        @Override public void tick() { ApostasyEntity.this.tickBrain(); }
    }

    private void tickBrain() {
        if (this.getWorld().isClient) return;
        updatePhase();
        repulsePlayers();
        tickPendingStrikes();
        PlayerEntity target = getClosestTarget(64.0);
        if (target == null) return;
        switch (phase) {
            case P1 -> handlePhase1(target);
            case P2 -> handlePhase2(target);
            case P3, P4 -> handlePhase3(target);
        }
        tickGlowFire();
        if (shockwaveCooldown > 0) shockwaveCooldown--;
    }

    private void repulsePlayers() {
        var box = this.getBoundingBox().expand(KEEP_OUT_RADIUS);
        var players = this.getWorld().getEntitiesByClass(PlayerEntity.class, box, p -> p.isAlive() && !p.isSpectator());
        for (PlayerEntity p : players) {
            Vec3d d = p.getPos().subtract(this.getPos());
            double dist = Math.max(0.2, d.length());
            double t = Math.max(0.0, KEEP_OUT_RADIUS - dist) / KEEP_OUT_RADIUS;
            Vec3d n = d.multiply(1.0 / dist);
            double push = KEEP_OUT_PUSH * (0.6 + 0.4 * t);
            p.addVelocity(n.x * push, 0.35, n.z * push);
            p.velocityDirty = true;
            p.slowMovement(p.getWorld().getBlockState(p.getBlockPos()), new Vec3d(0.2, 1.0, 0.2));
        }
    }

    private @Nullable PlayerEntity getClosestTarget(double range) {
        return this.getWorld().getClosestPlayer(this, range);
    }

    private void handlePhase1(PlayerEntity target) {
        if (ringShotCooldown-- <= 0) { ringShotCooldown = RING_SHOT_INTERVAL; fireRingsBurst(target); }
        if (this.random.nextFloat() < 0.10f) tryStartEyeBurst(target, 6);
        if (this.random.nextFloat() < 0.04f) tryShockwave(target);
    }

    private void handlePhase2(PlayerEntity target) {
        if (hugeLaserCooldown-- <= 0) { hugeLaserCooldown = HUGE_LASER_INTERVAL; fireEyeHugeLaser(target); }
        if (this.random.nextFloat() < 0.14f) tryStartEyeBurst(target, 8);
        if (lightningCooldown-- <= 0) {
            lightningCooldown = P2_SINGLE_COOLDOWN;
            BlockPos strikePos = safeStrikeNear(target.getBlockPos(), 2, 5);
            scheduleLightning(strikePos, TELEGRAPH_TICKS);
            if (this.getWorld() instanceof ServerWorld sw) {
                java.util.List<BlockState> pulled = pullDownColumnBlocks(sw, strikePos, 8, 4);
                spawnDebrisCloud(sw, strikePos, pulled, 4);
            }
        }
        if (this.random.nextFloat() < 0.06f) tryShockwave(target);
        boolean near = this.squaredDistanceTo(target) <= (LINE_TRIGGER_RANGE * LINE_TRIGGER_RANGE);
        if (near && this.random.nextFloat() < 0.05f) {
            scheduleLightningLineTowards(target, 8, 4.0, 5);
        }
        if (this.random.nextFloat() < 0.03f && hugeLaserCooldown <= 0) { hugeLaserCooldown = 45; fireEyeHugeLaser(target); }
        else if (hugeLaserCooldown > 0) hugeLaserCooldown--;
    }

    private void handlePhase3(PlayerEntity target) {
        if (hugeLaserCooldown-- <= 0) { hugeLaserCooldown = HUGE_LASER_INTERVAL; fireEyeHugeLaser(target); }
        if (this.random.nextFloat() < 0.14f) tryStartEyeBurst(target, 8);
        if (lightningCooldown-- <= 0) {
            lightningCooldown = P3_ZONE_COOLDOWN;
            int delay = 0;
            for (int i = 0; i < PHASE3_BURST; i++) {
                BlockPos around = randomPosAround(PHASE3_MIN_R, PHASE3_MAX_R);
                BlockPos safe = safeStrikeAt(around);
                scheduleLightning(safe, TELEGRAPH_TICKS + delay);
                delay += 6;
            }
        }
        if (this.random.nextFloat() < 0.08f) tryShockwave(target);
        boolean near = this.squaredDistanceTo(target) <= (LINE_TRIGGER_RANGE * LINE_TRIGGER_RANGE);
        if (near && this.random.nextFloat() < 0.07f) {
            scheduleLightningLineTowards(target, 12, 4.0, 4);
        }
    }

    private boolean isEyeAlignedWith(PlayerEntity target, double maxDeg) {
        float headYaw = this.getHeadYaw();
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double bearingDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double d = MathHelper.wrapDegrees(bearingDeg - headYaw);
        return Math.abs(d) <= maxDeg;
    }
    private void tickGlowFire() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        PlayerEntity target = getClosestTarget(64.0);
        if (target == null) return;

        if (glowShotCooldown > 0) { glowShotCooldown--; return; }

        List<Vec3d> muzzles = getGlowMuzzles(); // server-side stand-in for “bones starting with glow”
        if (muzzles.isEmpty()) return;

        if (glowMuzzleIndex >= muzzles.size()) glowMuzzleIndex = 0;
        Vec3d mCenter = muzzles.get(glowMuzzleIndex++);

        Vec3d aim = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
        Vec3d dir = aim.subtract(mCenter);
        double L2 = dir.lengthSquared();
        if (L2 < 1e-6) dir = new Vec3d(0,0,1); else dir = dir.normalize();

        Vec3d muzzle = mCenter.add(dir.multiply(0.35));

        LaserProjectileEntity laser = ModEntities.LASER_PROJECTILE.create(sw);
        if (laser != null) {
            laser.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, 0, 0);
            laser.setOwner(this);
            laser.setVelocity(dir.multiply(2.6));
            laser.setDamage(4.0);
            laser.setLifetime(60);
            sw.spawnEntity(laser);
        }

        lastShotDir = dir;
        orientTowards(dir, 12f); // smooth rotate to shot direction

        glowShotCooldown = 10; // 0.5s @ 20tps
    }

    private void orientTowards(Vec3d dir, float maxStepDeg) {
        if (dir.lengthSquared() < 1e-6) return;
        float targetYaw = (float)(MathHelper.atan2(dir.z, dir.x) * (180F/Math.PI)) - 90f;
        float targetPitch = (float)(-(MathHelper.atan2(dir.y, Math.sqrt(dir.x*dir.x + dir.z*dir.z)) * (180F/Math.PI)));

        float newYaw = MathHelper.stepUnwrappedAngleTowards(this.getYaw(), targetYaw, maxStepDeg);
        float newHead = MathHelper.stepUnwrappedAngleTowards(this.getHeadYaw(), targetYaw, maxStepDeg);
        float newPitch = MathHelper.stepUnwrappedAngleTowards(this.getPitch(), targetPitch, maxStepDeg);

        this.setYaw(newYaw);
        this.setHeadYaw(newHead);
        this.setPitch(newPitch);
    }

    private List<Vec3d> getGlowMuzzles() {
        // Server can’t read model bones; use your existing ring muzzles as the “glow” emitters.
        double yawRad = MathHelper.RADIANS_PER_DEGREE * (this.getYaw() % 360f);
        List<Vec3d> out = new ArrayList<>(24);
        out.addAll(buildRingMuzzles(2.4, yawRad * 1.00, 8)); // pretend these are glow1..8
        out.addAll(buildRingMuzzles(3.1, yawRad * 0.97, 8)); // glow9..16
        out.addAll(buildRingMuzzles(3.8, yawRad * 0.94, 8)); // glow17..24
        return out;
    }

    private Vec3d eyeMuzzle() {
        return this.getPos().add(0, this.getStandingEyeHeight() + 0.4, 0);
    }

    private Vec3d aimDirectionFor(PlayerEntity target) {
        Vec3d vel = target.getVelocity();
        if (vel.lengthSquared() > 0.05) return vel.normalize();
        Vec3d eye = eyeMuzzle();
        Vec3d aim = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
        Vec3d dir = aim.subtract(eye);
        double L2 = dir.lengthSquared();
        if (L2 < 1e-6) return new Vec3d(0, 0, 1);
        return dir.normalize();
    }

    private void tryStartEyeBurst(PlayerEntity target, int shots) {
        if (eyeBurstShotsLeft > 0) return;
        if (!isEyeAlignedWith(target, EYE_ALIGN_DEG)) return;
        eyeBurstShotsLeft = Math.max(1, shots);
        eyeBurstTimer = 0;
        eyeBurstTargetId = target.getId();
    }


    private void fireOneEyeLaser(PlayerEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        Vec3d eye = eyeMuzzle();
        Vec3d dir = aimDirectionFor(target);
        Vec3d muzzle = eye.add(dir.multiply(EYE_MUZZLE_FORWARD));
        LaserProjectileEntity laser = ModEntities.LASER_PROJECTILE.create(sw);
        if (laser == null) return;
        laser.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, 0, 0);
        laser.setOwner(this);
        laser.setVelocity(dir.multiply(2.6));
        laser.setDamage(EYE_BURST_DAMAGE);
        laser.setLifetime(EYE_BURST_LIFETIME);
        sw.spawnEntity(laser);
    }

    private void tryShockwave(PlayerEntity target) {
        if (shockwaveCooldown > 0) return;
        spawnShockwaveAtPlayerY(target);
        shockwaveCooldown = switch (phase) {
            case P1 -> SHOCKWAVE_COOLDOWN_P1;
            case P2 -> SHOCKWAVE_COOLDOWN_P2;
            default -> SHOCKWAVE_COOLDOWN_P3;
        };
    }

    private void spawnShockwaveAtPlayerY(PlayerEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        var e = net.kronoz.odyssey.init.ModEntities.SHOCKWAVE.create(sw);
        if (e == null) return;

        double py = Math.floor(target.getY()) + 0.05; // spawn at player's level
        e.refreshPositionAndAngles(this.getX(), py, this.getZ(), 0, 0);

        // OLD: e.setup(50f, 40, py, this.getId());
        e.setup(50f, 40, false); // false = ground wave (yOffset ~ 0.05)

        sw.spawnEntity(e);
    }


    private BlockPos randomPosAround(int minR, int maxR) {
        double a = this.random.nextDouble() * Math.PI * 2;
        int r = this.random.nextBetween(minR, maxR);
        int x = MathHelper.floor(getX() + Math.cos(a) * r);
        int z = MathHelper.floor(getZ() + Math.sin(a) * r);
        return new BlockPos(x, getBlockY(), z);
    }

    private boolean playerOn(BlockPos pos, double radius) {
        return !this.getWorld().getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(radius), p -> p.isAlive() && !p.isSpectator()).isEmpty();
    }

    private BlockPos safeStrikeNear(BlockPos center, int minR, int maxR) {
        for (int tries = 0; tries < 16; tries++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            int r = this.random.nextBetween(minR, maxR);
            BlockPos candidate = center.add(MathHelper.floor(Math.cos(a) * r), 0, MathHelper.floor(Math.sin(a) * r));
            BlockPos safe = safeStrikeAt(candidate);
            if (!playerOn(safe, 1.5)) return safe;
        }
        return safeStrikeAt(center);
    }

    private BlockPos safeStrikeAt(BlockPos approx) {
        double x = approx.getX() + 0.5;
        double z = approx.getZ() + 0.5;
        double yStart = this.getY() + 16.0;
        double yEnd = this.getY() - 64.0;
        var hit = this.getWorld().raycast(new RaycastContext(new Vec3d(x, yStart, z), new Vec3d(x, yEnd, z), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        double y = hit.getType() == HitResult.Type.BLOCK ? hit.getPos().y : this.getWorld().getBottomY() + 1;
        return BlockPos.ofFloored(x, y, z);
    }

    private void scheduleLightning(BlockPos pos, int delay) {
        this.pendingStrikes.add(new PendingStrike(pos, delay));
    }

    private void tickPendingStrikes() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        List<PendingStrike> next = new ArrayList<>(pendingStrikes.size());
        for (int i = 0; i < pendingStrikes.size(); i++) {
            PendingStrike ps = pendingStrikes.get(i);
            spawnGroundTelegraph(sw, ps.pos, 0.9, 0.9, TELEGRAPH_TICKS);
            ps.ticks--;
            if (ps.ticks <= 0) {
                BlockPos strikePos = ps.pos;
                if (!playerOn(strikePos, 1.5)) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
                    if (bolt != null) {
                        bolt.setCosmetic(true);
                        bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(strikePos));
                        sw.spawnEntity(bolt);
                        sw.emitGameEvent(GameEvent.LIGHTNING_STRIKE, strikePos, GameEvent.Emitter.of(this));
                        causeDebrisOnLightning(sw, strikePos);
                    }
                }
            } else {
                next.add(ps);
            }
        }
        pendingStrikes.clear();
        pendingStrikes.addAll(next);
    }

    private void scheduleLightningLineTowards(PlayerEntity target, int nodes, double step, int tickDelayStep) {
        BlockPos start = safeStrikeAt(BlockPos.ofFloored(getX(), getY(), getZ()));
        Vec3d startV = new Vec3d(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        BlockPos tgt = safeStrikeAt(target.getBlockPos());
        Vec3d dir = new Vec3d(tgt.getX() + 0.5 - startV.x, 0, tgt.getZ() + 0.5 - startV.z).normalize();
        int delay = 0;
        for (int i = 0; i < nodes; i++) {
            Vec3d p = startV.add(dir.multiply(step * i));
            BlockPos pos = safeStrikeAt(BlockPos.ofFloored(p.x, getY(), p.z));
            if (!playerOn(pos, 1.5)) {
                scheduleLightning(pos, TELEGRAPH_TICKS + delay);
                delay += tickDelayStep;
            }
        }
    }

    private void causeDebrisOnLightning(ServerWorld sw, BlockPos strikePos) {
        java.util.ArrayList<BlockState> list = new java.util.ArrayList<>();
        BlockState ground = groundStateAt(sw, strikePos);
        if (ground != null) list.add(ground);
        list.addAll(pullDownColumnBlocks(sw, strikePos, 12, 6));
        spawnDebrisCloud(sw, strikePos, list, 6);
    }

    private BlockState groundStateAt(ServerWorld sw, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 3; i++) {
            BlockState st = sw.getBlockState(p);
            if (!st.isAir()) return st;
            p = p.down();
        }
        return Blocks.COBBLESTONE.getDefaultState();
    }

    private BlockPos randomPosInRadius(BlockPos center, int radius) {
        double a = this.random.nextDouble() * Math.PI * 2.0;
        int r = this.random.nextBetween(radius / 3, radius);
        int x = center.getX() + MathHelper.floor(Math.cos(a) * r);
        int z = center.getZ() + MathHelper.floor(Math.sin(a) * r);
        return new BlockPos(x, center.getY(), z);
    }

    private java.util.List<BlockState> pullDownColumnBlocks(ServerWorld sw, BlockPos pos, int scanUp, int maxPull) {
        java.util.ArrayList<BlockState> grabbed = new java.util.ArrayList<>();
        int pulled = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = 1; dy <= scanUp && pulled < maxPull; dy++) {
            m.set(pos.getX(), pos.getY() + dy, pos.getZ());
            var st = sw.getBlockState(m);
            if (st.isAir()) continue;
            var blk = st.getBlock();
            if (blk == Blocks.BEDROCK || blk == Blocks.BARRIER) continue;
            if (st.getCollisionShape(sw, m).isEmpty()) continue;
            sw.setBlockState(m, Blocks.AIR.getDefaultState(), 3);
            var f = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(sw, m, st);
            if (f != null) {
                f.dropItem = false;
                try { f.setDestroyedOnLanding(); } catch (Throwable ignored) {}
                double spread = 0.35;
                f.setVelocity(
                        (this.random.nextDouble() - 0.5) * spread,
                        -0.2 - this.random.nextDouble() * 0.2,
                        (this.random.nextDouble() - 0.5) * spread
                );
            }
            grabbed.add(st);
            pulled++;
        }
        return grabbed;
    }

    private void spawnDebrisCloud(ServerWorld sw, BlockPos pos, java.util.List<BlockState> states, int count) {
        for (int i = 0; i < count; i++) {
            var e = ModEntities.DEBRIS_BLOCK.create(sw);
            if (e == null) continue;
            BlockState st = states != null && !states.isEmpty()
                    ? states.get(this.random.nextInt(states.size()))
                    : Blocks.COBBLESTONE.getDefaultState();
            e.init(st, 40 + this.random.nextBetween(0, 20));
            double ox = (this.random.nextDouble() - 0.5) * 1.8;
            double oz = (this.random.nextDouble() - 0.5) * 1.8;
            double oy = 0.4 + this.random.nextDouble() * 0.4;
            e.refreshPositionAndAngles(pos.getX() + 0.5 + ox, pos.getY() + 0.6, pos.getZ() + 0.5 + oz, 0, 0);
            e.setVelocity(ox * 0.6, oy, oz * 0.6);
            float avx = (float) ((this.random.nextDouble() - 0.5) * 20.0);
            float avy = (float) ((this.random.nextDouble() - 0.5) * 20.0);
            float avz = (float) ((this.random.nextDouble() - 0.5) * 20.0);
            e.setAngularVelocity(avx, avy, avz);
            sw.spawnEntity(e);
        }
    }

    private void spawnGroundTelegraph(ServerWorld sw, BlockPos pos, double rx, double rz, int life) {
        var e = ModEntities.GROUND_DECAL.create(sw);
        if (e == null) return;
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.01, pos.getZ() + 0.5, 0, 0);
        e.setup(0.95, life);
        sw.spawnEntity(e);
    }

    private void fireEyeHugeLaser(PlayerEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        Vec3d eye = getEyeMuzzleCenter();
        Vec3d aim = target.getPos().add(0, target.getStandingEyeHeight(), 0);
        Vec3d dir = aim.subtract(eye).normalize();
        Vec3d muzzle = eye.add(dir.multiply(0.6));
        LaserProjectileEntity beam = ModEntities.LASER_PROJECTILE.create(sw);
        if (beam == null) return;
        beam.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, 0, 0);
        beam.setOwner(this);
        beam.setVelocity(dir.multiply(HUGE_LASER_SPEED));
        beam.setDamage(10.0);
        beam.setLifetime(70);
        sw.spawnEntity(beam);
    }

    private void fireRingsBurst(PlayerEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        List<Vec3d> muzzles = getRingsMuzzles();
        Vec3d aim = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
        for (Vec3d mCenter : muzzles) {
            Vec3d dir = aim.subtract(mCenter).normalize();
            Vec3d m = mCenter.add(dir.multiply(0.35));
            LaserProjectileEntity laser = ModEntities.LASER_PROJECTILE.create(sw);
            if (laser == null) continue;
            laser.refreshPositionAndAngles(m.x, m.y, m.z, 0, 0);
            laser.setOwner(this);
            laser.setVelocity(dir.multiply(SMALL_LASER_SPEED));
            laser.setDamage(3.0);
            laser.setLifetime(60);
            sw.spawnEntity(laser);
        }
    }

    private Vec3d getEyeMuzzleCenter() {
        return this.getPos().add(0, this.getStandingEyeHeight() + 0.4, 0);
    }

    private List<Vec3d> getRingsMuzzles() {
        double yawRad = MathHelper.RADIANS_PER_DEGREE * (this.getYaw() % 360f);
        List<Vec3d> out = new ArrayList<>(24);
        out.addAll(buildRingMuzzles(RING1_R, yawRad, 8));
        out.addAll(buildRingMuzzles(RING2_R, yawRad * 0.97, 8));
        out.addAll(buildRingMuzzles(RING3_R, yawRad * 0.94, 8));
        return out;
    }

    private List<Vec3d> buildRingMuzzles(double radius, double yawRad, int guns) {
        List<Vec3d> list = new ArrayList<>(guns);
        Vec3d center = this.getPos().add(0, RINGS_Y, 0);
        for (int i = 0; i < guns; i++) {
            double a = yawRad + (Math.PI * 2.0) * (i / (double) guns);
            double x = center.x + MathHelper.cos((float) a) * radius;
            double z = center.z + MathHelper.sin((float) a) * radius;
            list.add(new Vec3d(x, center.y, z));
        }
        return list;
    }

    @Override
    public void tick() {
        super.tick();
        double targetY = groundYAt(getX(), getY(), getZ()) + HOVER_OFFSET;
        double err = targetY - this.getY();
        hoverVy += HOVER_K * err - HOVER_D * hoverVy;
        hoverVy = MathHelper.clamp(hoverVy, -HOVER_MAX_VY, HOVER_MAX_VY);
        double vx = this.getVelocity().x * 0.90;
        double vz = this.getVelocity().z * 0.90;
        this.setVelocity(vx, hoverVy, vz);
        this.move(MovementType.SELF, this.getVelocity());
        if (this.verticalCollision && hoverVy < 0) hoverVy = 0;
        this.fallDistance = 0f;
        this.setNoGravity(true);
        this.noClip = false;
        lastTargetY = targetY;
    }

    private double groundYAt(double x, double yRef, double z) {
        Vec3d start = new Vec3d(x, yRef + HOVER_SAMPLE_UP, z);
        Vec3d end   = new Vec3d(x, yRef - HOVER_SAMPLE_DOWN, z);
        var hit = this.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) return hit.getPos().y;
        return this.getWorld().getBottomY() + 1.0;
    }

    @Override
    public boolean isFireImmune() { return true; }

    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.LIGHTNING_BOLT)) return false;
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) return false;
        return super.damage(source, amount);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Phase", phase.name());
        nbt.putInt("cdRing", ringShotCooldown);
        nbt.putInt("cdHuge", hugeLaserCooldown);
        nbt.putInt("cdBolt", lightningCooldown);
        nbt.putInt("cdShock", shockwaveCooldown);
        nbt.putDouble("hvVy", hoverVy);
        nbt.putDouble("lastTY", lastTargetY);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        try { phase = Phase.valueOf(nbt.getString("Phase")); } catch (Exception ignored) {}
        ringShotCooldown = nbt.getInt("cdRing");
        hugeLaserCooldown = nbt.getInt("cdHuge");
        lightningCooldown = nbt.getInt("cdBolt");
        if (nbt.contains("cdShock")) shockwaveCooldown = nbt.getInt("cdShock");
        if (nbt.contains("hvVy")) hoverVy = nbt.getDouble("hvVy");
        if (nbt.contains("lastTY")) lastTargetY = nbt.getDouble("lastTY");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
