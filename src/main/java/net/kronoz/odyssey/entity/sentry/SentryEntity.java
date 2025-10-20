package net.kronoz.odyssey.entity.sentry;

import net.kronoz.odyssey.entity.sentinel.SentinelEntity;
import net.kronoz.odyssey.init.ModParticles;
import net.kronoz.odyssey.init.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
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
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.constant.DefaultAnimations;

public class SentryEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private static final TrackedData<Float> TRACK_HEAD_PITCH = DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_EYE_YAW    = DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACK_EYE_PITCH  = DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public static final float HEAD_PITCH_MIN = (float)Math.toRadians(-45);
    public static final float HEAD_PITCH_MAX = (float)Math.toRadians(45);
    public static final float EYE_YAW_MIN    = (float)Math.toRadians(-50);
    public static final float EYE_YAW_MAX    = (float)Math.toRadians(50);
    public static final float EYE_PITCH_MIN  = (float)Math.toRadians(-30);
    public static final float EYE_PITCH_MAX  = (float)Math.toRadians(30);



    private int ticksSinceHit = 0;
    private boolean wasHitRecently = false;
    private boolean shieldDown = false;

    private static final TrackedData<Integer> HIT_COUNT =
            DataTracker.registerData(SentryEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int hitCount = 0;

    public SentryEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new MoveControl(this);
        this.experiencePoints = 0;
    }


    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 10.0);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!shieldDown) {
            boolean axeHit = false;
            Entity attacker = source.getAttacker();
            this.playSound(ModSounds.ENERGY_SHIELD_HIT, 0.7f, 1.0f);

            if (attacker instanceof PlayerEntity player) {
                axeHit = player.getMainHandStack().isIn(ItemTags.AXES);
                if (!this.getWorld().isClient) {
                    double x1 = this.getX();
                    double y1 = this.getY() + this.getHeight() / 2.0;
                    double z1 = this.getZ();
                    double x2 = player.getX();
                    double y2 = player.getY() + player.getHeight() / 2.0;
                    double z2 = player.getZ();
                    double midX = (x1 + x2) / 2.0;
                    double midY = (y1 + y2) / 2.0;
                    double midZ = (z1 + z2) / 2.0;

                    this.getWorld().addParticle(
                            net.kronoz.odyssey.init.ModParticles.SENTRY_SHIELD_FULL_PARTICLE,
                            midX, midY, midZ,
                            0.0, 0.0, 0.0
                    );
                }
            }

            if (axeHit) {
                wasHitRecently = true;
                ticksSinceHit = 0;
                hitCount++;
                if (hitCount >= 4) {
                    shieldDown = true;
                    this.playSound(ModSounds.ENERGY_SHIELD_BREAK, 1.0f, 2.0f);
                }
            }

            return false;
        } else {
            return super.damage(source, amount);
        }
    }



//    protected void onSentryDamaged(DamageSource source, float amount) {
//
//
//        if (shieldDown == false) {
//            hitCount++;
//
//
//            if (hitCount == 1) {
//                this.getWorld().playSound(this, this.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.AMBIENT, 1.0f, 1.0f);
//
//
//                wasHitRecently = true;
//
//            }
//            if (hitCount >= 4) {
//                this.getWorld().playSound(this, this.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.AMBIENT, 1.0f, 1.0f);
//
//
//                wasHitRecently = true;
//                shieldDown = true;
//
//            }
//        }


//    }





    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("HitCount", hitCount);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        hitCount = nbt.getInt("HitCount");
    }

    public int getHitCount() {
        return hitCount;
    }



    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.SENTRY_STEP, 0.3F, 1.0F);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TRACK_HEAD_PITCH, 0f);
        builder.add(TRACK_EYE_YAW, 0f);
        builder.add(TRACK_EYE_PITCH, 0f);
        builder.add(HIT_COUNT, 0);
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


        if (wasHitRecently) {
            ticksSinceHit++;
            if (ticksSinceHit > 40) {
                wasHitRecently = false;
                hitCount = 0;
                ticksSinceHit = 0;
                NbtCompound nbt = new NbtCompound();
                this.writeCustomDataToNbt(nbt);
            }
        }


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
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }


    private PlayState predicate(AnimationState<SentryEntity> s) {
        if (s.isMoving()) s.getController().setAnimation(RawAnimation.begin().then("animation.sentry.walk", Animation.LoopType.LOOP));
            
         else s.getController().setAnimation(RawAnimation.begin().then("animation.sentry.idle", Animation.LoopType.LOOP));
        return PlayState.CONTINUE;

    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
