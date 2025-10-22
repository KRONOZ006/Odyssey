package net.kronoz.odyssey.entity.arcangel;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.kronoz.odyssey.init.ModSounds;
import net.kronoz.odyssey.systems.cam.ClientFx;
import net.kronoz.odyssey.systems.cam.RapidShake;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ArcangelEntity extends PathAwareEntity implements GeoEntity {
// also don't ask why it's a path aware entity XD, it just is caus minecraft. (idk either) -Dark
    // === Synced flags/data ===
    public static final TrackedData<Boolean> SHOOTING =
            DataTracker.registerData(ArcangelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Integer> BLOOD =
            DataTracker.registerData(ArcangelEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // === Anim clips ===
    private static final RawAnimation IDLE  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("shoot");

    private boolean prevShootingClient;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final int SHAKE_DELAY_TICKS = 40; // 2.5s à 20 TPS

    private int shakeDelayClient = -1;
    private boolean shakeFiredThisShot = false;    private static final int SHOOT_DURATION_TICKS = 80;
    private static final int DAMAGE_HIT_TICKS     = 60;
    private static final float ARCANGEL_DAMAGE    = 150.0f;

    private int shootingTicks;
    private boolean playedShootSfx;
    @Nullable private UUID lockedTarget;
    private float fullBodyYawDeg;
    private float headPitchDeg;
    private float recoilRad;
    private float bodyYawTargetDeg;
    private float headPitchTargetDeg;

    public ArcangelEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPersistent();
        this.setAiDisabled(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SHOOTING, false);
        builder.add(BLOOD, 0);
    }

    // ======= External blood feed =======
    public void addBlood(int amount) {
        if (this.getWorld().isClient) {
            this.dataTracker.get(BLOOD);
            return;
        }
        int cur = MathHelper.clamp(this.dataTracker.get(BLOOD) + amount, 0, 100);
        this.dataTracker.set(BLOOD, cur);
        if (cur >= 100) {
            startShooting();
            this.dataTracker.set(BLOOD, 0);
        }
        this.dataTracker.get(BLOOD);
    }


    // ======= Shoot sequence =======
    public void startShooting() {
        if (this.getWorld().isClient) return;
        if (!this.dataTracker.get(SHOOTING)) {
            this.dataTracker.set(SHOOTING, true);
            this.shootingTicks = SHOOT_DURATION_TICKS;
            this.playedShootSfx = false;

            ApostasyEntity target = findNearestApostasyUnlimited();
            this.lockedTarget = (target != null) ? target.getUuid() : null;

            updateAimTargets(target);
            this.recoilRad = 0.35f;
        }
    }

    @Override
    public void tick() {
        super.tick();

        this.setVelocity(Vec3d.ZERO);
        this.fallDistance = 0f;

        ApostasyEntity visibleTarget = resolveLockedTarget();
        if (visibleTarget == null) {
            visibleTarget = findNearestApostasyUnlimited();
        }
        updateAimTargets(visibleTarget);

        this.fullBodyYawDeg = approachAngleDeg(this.fullBodyYawDeg, this.bodyYawTargetDeg);
        this.headPitchDeg   = MathHelper.lerp(0.2f, this.headPitchDeg, this.headPitchTargetDeg);

        if (this.recoilRad > 0f) {
            this.recoilRad *= 0.88f;
            if (this.recoilRad < 0.005f) this.recoilRad = 0f;
        }
        if (this.getWorld().isClient) {
            boolean now = this.dataTracker.get(SHOOTING);

            if (now && !prevShootingClient) {
                shakeDelayClient = SHAKE_DELAY_TICKS;
                shakeFiredThisShot = false;
            }

            if (now && shakeDelayClient >= 0) {
                if (shakeDelayClient-- == 0 && !shakeFiredThisShot) {
                    net.kronoz.odyssey.systems.cam.ClientFx.rapidShakeStart(200.75f, 40);
                    shakeFiredThisShot = true;
                }
            }

            if (!now) {
                shakeDelayClient = -1;
                shakeFiredThisShot = false;
            }

            prevShootingClient = now;
        }
        if (!this.getWorld().isClient) {
            if (this.dataTracker.get(SHOOTING)) {
                if (shootingTicks == DAMAGE_HIT_TICKS) {
                    ApostasyEntity target = resolveLockedTarget();
                    if (target == null) target = findNearestApostasyUnlimited();
                    if (target != null && target.isAlive()) {
                        target.damage((this.getWorld()).getDamageSources().mobAttack(this), ARCANGEL_DAMAGE);
                    }
                }
            }
        }
        if (!this.getWorld().isClient) {
            if (this.dataTracker.get(SHOOTING)) {
                if (!playedShootSfx) {
                    playedShootSfx = true;
                    var sound = (ModSounds.ARC_SHOOT != null)
                            ? ModSounds.ARC_SHOOT
                            : SoundEvents.ENTITY_GUARDIAN_ATTACK;
                    this.getWorld().playSound(null, this.getBlockPos(), sound, SoundCategory.HOSTILE, 1.0f, 1.0f);
                }

                if (shootingTicks == DAMAGE_HIT_TICKS) {
                    ApostasyEntity target = resolveLockedTarget();
                    if (target == null) target = findNearestApostasyUnlimited();
                    if (target != null && target.isAlive()) {
                        target.damage((this.getWorld()).getDamageSources().mobAttack(this), ARCANGEL_DAMAGE);
                    }
                }


                if (--shootingTicks <= 0) {
                    this.dataTracker.set(SHOOTING, false);
                    this.lockedTarget = null;
                }
            }
        }
    }

    // ======= Aiming helpers =======
    @Nullable
    private ApostasyEntity resolveLockedTarget() {
        if (lockedTarget == null) return null;
        List<ApostasyEntity> list = this.getWorld().getEntitiesByClass(
                ApostasyEntity.class,
                this.getBoundingBox().expand(1_000_000.0), // effectively global
                e -> e.getUuid().equals(lockedTarget) && e.isAlive()
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    private static float approachAngleDeg(float current, float target) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > (float) 6.0) delta = (float) 6.0;
        if (delta < -(float) 6.0) delta = -(float) 6.0;
        return current + delta;
    }

    private void updateAimTargets(@Nullable ApostasyEntity t) {
        if (t == null) return;
        Vec3d d = t.getEyePos().subtract(this.getEyePos());
        this.bodyYawTargetDeg  = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        this.headPitchTargetDeg = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x*d.x + d.z*d.z))));
    }


    @Nullable
    private ApostasyEntity findNearestApostasyUnlimited() {
        Box huge = this.getBoundingBox().expand(1_000_000.0); // ~infinite for practical purposes
        List<ApostasyEntity> list = this.getWorld().getEntitiesByClass(ApostasyEntity.class, huge, LivingEntity::isAlive);
        return list.stream().min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
    }

    // ======= Geckolib =======
    private <E extends ArcangelEntity> PlayState controller(AnimationState<E> state) {
        if (this.dataTracker.get(SHOOTING)) {
            state.setAnimation(SHOOT);
            return PlayState.CONTINUE;
        }
        state.setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::controller));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object o) {
        return this.age;
    }

    // ======= Model hooks =======
    public float getFullBodyYaw() { return this.fullBodyYawDeg; }
    public float getHeadPitchDeg() { return this.headPitchDeg; }
    public float getRecoilRad() { return this.recoilRad; }

    // ======= Save/Load =======
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.shootingTicks = nbt.getInt("ShootingTicks");
        this.dataTracker.set(SHOOTING, nbt.getBoolean("Shooting"));
        this.dataTracker.set(BLOOD, MathHelper.clamp(nbt.getInt("Blood"), 0, 100));
        this.playedShootSfx = nbt.getBoolean("PlayedShootSfx");
        this.fullBodyYawDeg = nbt.getFloat("FullBodyYawDeg");
        this.headPitchDeg = nbt.getFloat("HeadPitchDeg");
        this.recoilRad = nbt.getFloat("RecoilRad");
        this.bodyYawTargetDeg = nbt.getFloat("BodyYawTargetDeg");
        this.headPitchTargetDeg = nbt.getFloat("HeadPitchTargetDeg");
        this.lockedTarget = nbt.containsUuid("LockedTarget") ? nbt.getUuid("LockedTarget") : null;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("ShootingTicks", this.shootingTicks);
        nbt.putBoolean("Shooting", this.dataTracker.get(SHOOTING));
        nbt.putInt("Blood", this.dataTracker.get(BLOOD));
        nbt.putBoolean("PlayedShootSfx", this.playedShootSfx);
        nbt.putFloat("FullBodyYawDeg", this.fullBodyYawDeg);
        nbt.putFloat("HeadPitchDeg", this.headPitchDeg);
        nbt.putFloat("RecoilRad", this.recoilRad);
        nbt.putFloat("BodyYawTargetDeg", this.bodyYawTargetDeg);
        nbt.putFloat("HeadPitchTargetDeg", this.headPitchTargetDeg);
        if (this.lockedTarget != null) nbt.putUuid("LockedTarget", this.lockedTarget);
    }

    // ======= Extra immobility =======
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluids() { return false; } //why is mojang like this :(
    @Override public void takeKnockback(double s, double x, double z) { /* no-op */ }
    @Override public void travel(Vec3d in) { this.setVelocity(Vec3d.ZERO); }
    @Override public boolean cannotDespawn() {return true;}
    @Override protected boolean isDisallowedInPeaceful() {return false;}
    @Override public boolean isPersistent() {return true;}
}