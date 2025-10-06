package net.kronoz.odyssey.entity.sentinel;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;

public class SentinelEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);

    private static final TrackedData<Float> TRACK_HEAD_PITCH = DataTracker.registerData(SentinelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_HEAD_YAW   = DataTracker.registerData(SentinelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_EYE_YAW    = DataTracker.registerData(SentinelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_EYE_PITCH  = DataTracker.registerData(SentinelEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private boolean spotted;
    private int spottedTicks;

    public static final float HEAD_PITCH_MIN = (float)Math.toRadians(-45);
    public static final float HEAD_PITCH_MAX = (float)Math.toRadians(45);
    public static final float HEAD_YAW_MIN   = (float)Math.toRadians(-60);
    public static final float HEAD_YAW_MAX   = (float)Math.toRadians(60);

    public static final float EYE_PITCH_MIN = (float)Math.toRadians(-30);
    public static final float EYE_PITCH_MAX = (float)Math.toRadians(30);
    public static final float EYE_YAW_MIN = (float)Math.toRadians(-50);
    public static final float EYE_YAW_MAX = (float)Math.toRadians(50);

    public static final double MAX_RANGE = 24.0;
    public static final float FOV_DEG = 60f;

    public SentinelEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.ignoreCameraFrustum = true;
        this.experiencePoints = 0;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TRACK_HEAD_PITCH, 0f);
        builder.add(TRACK_HEAD_YAW, 0f);
        builder.add(TRACK_EYE_YAW, 0f);
        builder.add(TRACK_EYE_PITCH, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.4)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(3, new SentinelSeekPlayerGoal(this));
        this.goalSelector.add(2, new EyeTrackGoal(this));
        this.goalSelector.add(1, new LookAroundGoal(this));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(false);
        nav.setCanEnterOpenDoors(false);
        return nav;
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        if (!this.getWorld().isClient) {
            if (this.spotted) {
                spottedTicks++;
                if (spottedTicks % 20 == 0) {
                    LivingEntity tgt = getTargetPlayer();
                    if (tgt != null) {
                        tgt.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, true, true, true));
                    }
                }
            } else {
                spottedTicks = 0;
            }
        }
    }

    public void setSpotted(boolean v) { this.spotted = v; }

    public void setDesiredHead(float yawRad, float pitchRad) {
        this.dataTracker.set(TRACK_HEAD_YAW, MathHelper.clamp(yawRad, HEAD_YAW_MIN, HEAD_YAW_MAX));
        this.dataTracker.set(TRACK_HEAD_PITCH, MathHelper.clamp(pitchRad, HEAD_PITCH_MIN, HEAD_PITCH_MAX));
    }
    public float getDesiredHeadYaw()   { return this.dataTracker.get(TRACK_HEAD_YAW); }
    public float getDesiredHeadPitch() { return this.dataTracker.get(TRACK_HEAD_PITCH); }

    public void setDesiredEye(float yawRad, float pitchRad) {
        this.dataTracker.set(TRACK_EYE_YAW, MathHelper.clamp(yawRad, EYE_YAW_MIN, EYE_YAW_MAX));
        this.dataTracker.set(TRACK_EYE_PITCH, MathHelper.clamp(pitchRad, EYE_PITCH_MIN, EYE_PITCH_MAX));
    }
    public float getDesiredEyeYaw() { return this.dataTracker.get(TRACK_EYE_YAW); }
    public float getDesiredEyePitch() { return this.dataTracker.get(TRACK_EYE_PITCH); }

    public LivingEntity getTargetPlayer() {
        if (this.getWorld().isClient) return null;
        double best = MAX_RANGE + 1;
        LivingEntity bestEnt = null;
        for (PlayerEntity p : ((ServerWorld)this.getWorld()).getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double d = this.distanceTo(p);
            if (d < best) {
                best = d;
                bestEnt = p;
            }
        }
        return bestEnt;
    }

    public boolean canSeePlayerInCone(PlayerEntity p) {
        if (p == null) return false;
        if (this.distanceTo(p) > MAX_RANGE) return false;
        Vec3d eyePos = this.getEyePos();
        Vec3d to = p.getEyePos().subtract(eyePos).normalize();
        Vec3d forward = Vec3d.fromPolar(0, this.getYaw()).normalize();
        double dot = forward.dotProduct(to);
        double angle = Math.toDegrees(Math.acos(MathHelper.clamp((float)dot, -1f, 1f)));
        if (angle > (FOV_DEG * 0.5)) return false;
        return this.getVisibilityCache().canSee(p);
    }

    public void steerTowards(Vec3d pos, double speed) {
        this.getNavigation().stop();
        this.getMoveControl().moveTo(pos.x, pos.y, pos.z, speed);
    }

    public void faceYawDeg(float targetYawDeg, float lerp) {
        float newYaw = MathHelper.lerp(lerp, this.getYaw(), MathHelper.wrapDegrees(targetYawDeg));
        this.setYaw(newYaw);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(DefaultAnimations.genericIdleController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
