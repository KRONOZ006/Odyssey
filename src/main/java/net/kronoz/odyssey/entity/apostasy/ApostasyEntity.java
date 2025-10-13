package net.kronoz.odyssey.entity.apostasy;

import net.kronoz.odyssey.init.ModEntities;
import net.kronoz.odyssey.init.ModParticles;
import net.kronoz.odyssey.entity.projectile.LaserProjectileEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
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
    private static final double REPULSE_RADIUS = 3.5;
    private static final double REPULSE_STRENGTH = 1.2;

    private static final int LIGHTNING_COOLDOWN = 80;
    private static final int TELEGRAPH_TICKS = 20;
    private static final int PHASE3_BURST = 8;
    private static final int PHASE3_MIN_R = 4, PHASE3_MAX_R = 10;


    private static final double HOVER_OFFSET = 8.0;
    private static final double HOVER_K = 0.35;
    private static final double HOVER_D = 0.50;
    private static final double HOVER_MAX_VY = 0.5;
    private static final double HOVER_SAMPLE_UP = 24.0;
    private static final double HOVER_SAMPLE_DOWN = 64.0;

    private int ringShotCooldown = 10;
    private int hugeLaserCooldown = 40;
    private int lightningCooldown = 40;

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
        PlayerEntity target = getClosestTarget(48.0);
        if (target == null) return;
        switch (phase) {
            case P1 -> handlePhase1(target);
            case P2 -> handlePhase2(target);
            case P3, P4 -> handlePhase3(target);
        }
    }

    private void repulsePlayers() {
        List<PlayerEntity> near = this.getWorld().getEntitiesByClass(
                PlayerEntity.class, this.getBoundingBox().expand(REPULSE_RADIUS),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (PlayerEntity p : near) {
            Vec3d diff = p.getPos().subtract(this.getPos());
            double dist = Math.max(0.3, diff.length());
            Vec3d n = diff.multiply(1.0 / dist);
            p.addVelocity(n.x * REPULSE_STRENGTH, 0.35, n.z * REPULSE_STRENGTH);
            p.velocityDirty = true;
        }
    }

    private @Nullable PlayerEntity getClosestTarget(double range) {
        return this.getWorld().getClosestPlayer(this, range);
    }

    private void handlePhase1(PlayerEntity target) {
        if (ringShotCooldown-- <= 0) { ringShotCooldown = RING_SHOT_INTERVAL; fireRingsBurst(target); }
        if (this.random.nextFloat() < 0.02f && hugeLaserCooldown <= 0) { hugeLaserCooldown = 50; fireEyeHugeLaser(target); }
        else if (hugeLaserCooldown > 0) hugeLaserCooldown--;
    }

    private void handlePhase2(PlayerEntity target) {
        if (ringShotCooldown-- <= 0) { ringShotCooldown = RING_SHOT_INTERVAL + 4; fireRingsBurst(target); }
        if (lightningCooldown-- <= 0) { lightningCooldown = LIGHTNING_COOLDOWN; BlockPos strikePos = safeStrikeNear(target.getBlockPos(), 3, 6); scheduleLightning(strikePos, TELEGRAPH_TICKS); }
        if (this.random.nextFloat() < 0.03f && hugeLaserCooldown <= 0) { hugeLaserCooldown = 45; fireEyeHugeLaser(target); }
        else if (hugeLaserCooldown > 0) hugeLaserCooldown--;
    }

    private void handlePhase3(PlayerEntity target) {
        if (hugeLaserCooldown-- <= 0) { hugeLaserCooldown = HUGE_LASER_INTERVAL; fireEyeHugeLaser(target); }
        if (ringShotCooldown-- <= 0) { ringShotCooldown = RING_SHOT_INTERVAL + 8; fireRingsBurst(target); }
        if (lightningCooldown-- <= 0) {
            lightningCooldown = LIGHTNING_COOLDOWN;
            for (int i = 0; i < PHASE3_BURST; i++) {
                BlockPos around = randomPosAround(PHASE3_MIN_R, PHASE3_MAX_R);
                BlockPos safe = safeStrikeAt(around);
                scheduleLightning(safe, TELEGRAPH_TICKS + this.random.nextBetween(0, 10));
            }
        }
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
        Iterator<PendingStrike> it = pendingStrikes.iterator();
        while (it.hasNext()) {
            PendingStrike ps = it.next();
            spawnGroundTelegraph(sw, ps.pos, 0.9, 0.9, TELEGRAPH_TICKS);
            ps.ticks--;
            if (ps.ticks <= 0) {
                if (!playerOn(ps.pos, 1.5)) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(ps.pos));
                        sw.spawnEntity(bolt);
                        sw.emitGameEvent(GameEvent.LIGHTNING_STRIKE, ps.pos, GameEvent.Emitter.of(this));
                    }
                }
                it.remove();
            }
        }
    }

    private void spawnGroundTelegraph(ServerWorld sw, BlockPos pos, double rx, double rz, int life) {
        var e = net.kronoz.odyssey.init.ModEntities.GROUND_DECAL.create(sw);
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
        nbt.putDouble("hvVy", hoverVy);
        nbt.putDouble("lastTY", lastTargetY);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        try { phase = Phase.valueOf(nbt.getString("Phase")); } catch (Exception ignored) {}
        ringShotCooldown = nbt.getInt("cdRing");
        hugeLaserCooldown = nbt.getInt("cdHuge");
        lightningCooldown = nbt.getInt("cdBolt");
        if (nbt.contains("hvVy")) hoverVy = nbt.getDouble("hvVy");
        if (nbt.contains("lastTY")) lastTargetY = nbt.getDouble("lastTY");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
