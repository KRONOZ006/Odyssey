package net.kronoz.odyssey.entity.apostasy;

import net.kronoz.odyssey.net.BossHudClearPayload;
import net.kronoz.odyssey.net.BossHudUpdatePayload;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.UUID;

public class ApostasyEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private ApostasyRingAnimator.Result ringPose;


    private float headPitch;
    private boolean didShockwave;
    private int phaseTick;
    private Phase phase = Phase.SPAWN_INTRO;
    private UUID forcedTarget;

    public enum Phase { SPAWN_INTRO, PATTERN_A, PATTERN_B, ENRAGED }

    public ApostasyEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 128.0);
    }

    @Override
    protected void initGoals() {
        // FIX: do not reassign goalSelector; just add your goal
        this.goalSelector.add(1, new BossBrain());
    }

    public void setHeadPitch(float pitch) { this.headPitch = pitch; }
    public float getHeadPitch() { return this.headPitch; }
    public ApostasyRingAnimator.Result getRingPose() { return ringPose; }

    @Override
    public boolean hasNoGravity() { return true; }

    @Override
    public boolean canTarget(EntityType<?> type) { return true; }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);

        if (isServer()) {
            if (!didShockwave) { didShockwave = true; doSpawnShockwave(); }
            keepAggro();
            hoverAt4();
            phaseTick++;
            updatePhase();

            switch (phase) {
                case PATTERN_A -> patternA();
                case PATTERN_B -> patternB();
                case ENRAGED   -> patternEnraged();
                default -> {}
            }

            if (this.age % 5 == 0) {
                float hp = this.getHealth(), mx = this.getMaxHealth();
                String name = (this.getDisplayName() != null) ? this.getDisplayName().getString() : "Apostasy";
                var s = sw();
                if (s != null) {
                    for (var sp : s.getPlayers(p -> !p.isSpectator() && p.squaredDistanceTo(this) < 256*256)) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                                .send(sp, new BossHudUpdatePayload(this.getId(), name, hp, mx));
                    }
                }
            }
        }

        ringPose = net.kronoz.odyssey.entity.apostasy.ApostasyRingAnimator.sample(getId(), age);

        this.setBodyYaw(0f);
        this.setHeadYaw(0f);
        this.setYaw(0f);
        this.setPitch(0f);
    }


    private void hoverAt4() {
        double g = sampleGroundYPlus(getX(), getY(), getZ());
        double ty = g + 4.0;
        double dy = ty - getY();
        double vy = MathHelper.clamp(dy * 0.25, -0.35, 0.35);
        setVelocity(getVelocity().x * 0.9, vy, getVelocity().z * 0.9);
        move(MovementType.SELF, getVelocity());
    }


    private void keepAggro() {
        PlayerEntity p = getLockedTarget();
        if (p == null) p = this.getWorld().getClosestPlayer(this, 128.0);
        if (p != null) {
            if (forcedTarget == null) forcedTarget = p.getUuid();
            this.setTarget(p);
        }
    }

    private PlayerEntity getLockedTarget() {
        var s = sw();
        if (s == null || forcedTarget == null) return null;
        var sp = s.getServer().getPlayerManager().getPlayer(forcedTarget);
        return (sp == null || sp.isSpectator()) ? null : sp;
    }


    private void updatePhase() {
        float hp = this.getHealth() / this.getMaxHealth();
        if (phase == Phase.SPAWN_INTRO && phaseTick > 60) { phase = Phase.PATTERN_A; phaseTick = 0; }
        else if (phase == Phase.PATTERN_A && (phaseTick > 20*15 || hp < 0.66f)) { phase = Phase.PATTERN_B; phaseTick = 0; }
        else if (phase == Phase.PATTERN_B && (phaseTick > 20*20 || hp < 0.33f)) { phase = Phase.ENRAGED; phaseTick = 0; }
    }

    private void patternA() {
        if (phaseTick % 28 == 0) fireFromNextGun(0);
        if (phaseTick % 36 == 0) fireFromNextGun(1);
    }

    private void patternB() {
        if (phaseTick % 18 == 0) fireFromNextGun(2);
        if (phaseTick % 32 == 0) fireFromNextGun(1);
    }

    private void patternEnraged() {
        if (phaseTick % 10 == 0) fireFromNextGun(0);
        if (phaseTick % 14 == 0) fireFromNextGun(1);
        if (phaseTick % 18 == 0) fireFromNextGun(2);
    }

    private static final int[] RING_COUNTS = {8,12,8};
    private static final float[] RING_RADII = {1.6f, 2.2f, 2.8f};
    private static final float[] RING_Y = {0.6f, 0.6f, 0.6f};
    private final int[] gunIndex = {0,0,0};

    private void fireFromNextGun(int ring) {
        var p = getLockedTarget();
        if (p == null) return;

        int count = RING_COUNTS[ring];
        int idx = gunIndex[ring] % count;
        gunIndex[ring] = (gunIndex[ring] + 1) % count;

        float ringYaw = 0f;
        if (ringPose != null) ringYaw = (ring == 0) ? ringPose.r1y : (ring == 1) ? ringPose.r2y : ringPose.r3y;

        float idxAngle = (float)(2.0 * Math.PI * (idx / (double)count));
        float angle = ringYaw + idxAngle;

        float r = RING_RADII[ring];
        double ox = net.minecraft.util.math.MathHelper.cos(angle) * r;
        double oz = net.minecraft.util.math.MathHelper.sin(angle) * r;
        double oy = RING_Y[ring];

        var spawn = new net.minecraft.util.math.Vec3d(getX() + ox, getY() + oy, getZ() + oz);
        var dir = p.getEyePos().subtract(spawn).normalize();

        if (isServer()) {
            var e = new net.kronoz.odyssey.entity.projectile.LaserProjectileEntity(net.kronoz.odyssey.init.ModEntities.LASER_PROJECTILE, getWorld());
            e.setOwner(this);
            e.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, 0f, 0f);
            e.setVelocity(dir.x, dir.y, dir.z, 2.8f, 0.0f);
            e.setDamage(6.0f);
            e.setLifetime(60);
            this.getWorld().spawnEntity(e);
            this.getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE,
                    net.minecraft.sound.SoundCategory.HOSTILE, 0.4f, 1.8f);
        }
    }


    private void doSpawnShockwave() {
        var s = sw();
        if (s == null) return;

        double rad = 12.0;
        for (var p : s.getPlayers(pl -> !pl.isSpectator() && pl.squaredDistanceTo(this) <= rad*rad)) {
            var n = p.getPos().subtract(this.getPos());
            n = (n.lengthSquared() < 1e-6) ? new net.minecraft.util.math.Vec3d(0,0,0) : n.normalize();
            p.addVelocity(n.x*1.6, 0.9, n.z*1.6);
            p.velocityModified = true;
        }
        s.playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_WITHER_SPAWN,
                net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.7f);
    }


    private boolean isServer() { return !this.getWorld().isClient; }
    private net.minecraft.server.world.ServerWorld sw() {
        return (this.getWorld() instanceof net.minecraft.server.world.ServerWorld s) ? s : null;
    }

    private double sampleGroundYPlus(double x, double y, double z) {
        var from = new Vec3d(x, y + 1.5, z);
        var to = new Vec3d(x, y - (double) 32, z);
        var hit = this.getWorld().raycast(new net.minecraft.world.RaycastContext(
                from, to,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                this));
        if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return hit.getPos().y;
        return this.getWorld().getBottomY();
    }

    @Override
    public void onRemoved() {
        var s = sw();
        if (s != null) for (var sp : s.getPlayers(p -> true))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                    .send(sp, new BossHudClearPayload());
        super.onRemoved();
    }

    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource src) {
        var s = sw();
        if (s != null) for (var sp : s.getPlayers(p -> true))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                    .send(sp, new BossHudClearPayload());
        super.onDeath(src);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (forcedTarget != null) nbt.putUuid("ForcedTarget", forcedTarget);
        nbt.putInt("PhaseTick", phaseTick);
        nbt.putString("Phase", phase.name());
        nbt.putBoolean("Shock", didShockwave);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("ForcedTarget")) forcedTarget = nbt.getUuid("ForcedTarget");
        phaseTick = nbt.getInt("PhaseTick");
        try { phase = Phase.valueOf(nbt.getString("Phase")); } catch (Exception ignored) {}
        didShockwave = nbt.getBoolean("Shock");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    private static final class BossBrain extends Goal {
        @Override public boolean canStart() { return true; }
    }
}
