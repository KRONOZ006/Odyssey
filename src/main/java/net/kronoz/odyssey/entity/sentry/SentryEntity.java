package net.kronoz.odyssey.entity.sentry;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;

public class SentryEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);

    private static final TrackedData<Float> TRACK_HEAD_PITCH = DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_EYE_YAW    = DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_EYE_PITCH  = DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public static final float HEAD_PITCH_MIN = (float)Math.toRadians(-45);
    public static final float HEAD_PITCH_MAX = (float)Math.toRadians(45);
    public static final float EYE_YAW_MIN    = (float)Math.toRadians(-50);
    public static final float EYE_YAW_MAX    = (float)Math.toRadians(50);
    public static final float EYE_PITCH_MIN  = (float)Math.toRadians(-30);
    public static final float EYE_PITCH_MAX  = (float)Math.toRadians(30);

    public SentryEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new MoveControl(this);
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.29)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TRACK_HEAD_PITCH, 0f);
        builder.add(TRACK_EYE_YAW, 0f);
        builder.add(TRACK_EYE_PITCH, 0f);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SentryMeleeGoal(this, 1.35, true));
        this.goalSelector.add(2, new SentryLookGoal(this));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true, p -> !p.isSpectator() && !p.isInCreativeMode() && p.hasStatusEffect(StatusEffects.WEAKNESS)));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        MobNavigation nav = new MobNavigation(this, world);
        nav.setCanPathThroughDoors(true);
        nav.setCanSwim(false);
        nav.setCanEnterOpenDoors(true);
        return nav;
    }

    @Override
    public void tick() {
        super.tick();
        this.setBodyYaw(this.getYaw());
        this.setHeadYaw(this.getYaw());
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {}

    public void setHeadPitch(float pitchRad) {
        this.dataTracker.set(TRACK_HEAD_PITCH, MathHelper.clamp(pitchRad, HEAD_PITCH_MIN, HEAD_PITCH_MAX));
    }
    public float getHeadPitch() { return this.dataTracker.get(TRACK_HEAD_PITCH); }

    public void setEye(float yawRad, float pitchRad) {
        this.dataTracker.set(TRACK_EYE_YAW, MathHelper.clamp(yawRad, EYE_YAW_MIN, EYE_YAW_MAX));
        this.dataTracker.set(TRACK_EYE_PITCH, MathHelper.clamp(pitchRad, EYE_PITCH_MIN, EYE_PITCH_MAX));
    }
    public float getEyeYaw() { return this.dataTracker.get(TRACK_EYE_YAW); }
    public float getEyePitch() { return this.dataTracker.get(TRACK_EYE_PITCH); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(DefaultAnimations.genericIdleController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
