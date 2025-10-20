package net.kronoz.odyssey.entity.arcangel;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.kronoz.odyssey.init.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.UUID;

public class ArcangelEntity extends HostileEntity implements GeoAnimatable {
    public static final TrackedData<Float> FULL_YAW = DataTracker.registerData(ArcangelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> HEAD_PITCH = DataTracker.registerData(ArcangelEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private float desiredYaw;
    private float desiredPitch;
    private int aimCooldown;

    private int blood;
    private boolean shooting;
    private boolean hitApplied;
    private int shootStartTick;
    private UUID beamTargetUuid;

    private float recoilRad;

    private static final int SHOOT_IMPACT_TICKS = 40;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("shoot");
    private static final RawAnimation SHOOT = RawAnimation.begin().then("shoot", Animation.LoopType.PLAY_ONCE);
    private boolean wasShootingLastTick = false;



    public ArcangelEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setPersistent();
        this.setNoGravity(false);
    }


    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || amount > 5000) {

            return super.damage(source, amount);


        } else {
            return false;
        }
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 2.0)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 10000)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 128.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(FULL_YAW, 0f);
        builder.add(HEAD_PITCH, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            if (--aimCooldown <= 0) {
                aimCooldown = 10;
                ApostasyEntity t = getBeamTarget();
                if (t == null) t = findNearestApostasy(128.0);
                if (t != null) {
                    beamTargetUuid = t.getUuid();
                    Vec3d eye = getPos().add(0, getStandingEyeHeight(), 0);
                    Vec3d tgt = new Vec3d(t.getX(), t.getEyeY(), t.getZ());
                    Vec3d d = tgt.subtract(eye);
                    float yaw = (float)(MathHelper.atan2(d.x, d.z) * (180f / Math.PI));
                    float horiz = MathHelper.sqrt((float)(d.x * d.x + d.z * d.z));
                    float pitch = (float)(MathHelper.atan2(d.y, horiz) * (-180f / Math.PI ));
                    //it does not look fully at the apostasy, maybe you can fix this mr math goat ~ krono
                    desiredYaw = wrap(yaw);
                    desiredPitch = MathHelper.clamp(pitch, -45f, 45f);
                }
            }

            float cy = getDataTracker().get(FULL_YAW);
            float cp = getDataTracker().get(HEAD_PITCH);
            float ny = stepAngle(cy, desiredYaw, 0.8f);
            float np = MathHelper.lerp(0.2f, cp, desiredPitch);
            getDataTracker().set(FULL_YAW, wrap(ny));
            getDataTracker().set(HEAD_PITCH, np);

            if (shooting) {
                int elapsed = this.age - shootStartTick;
                if (!hitApplied && elapsed >= SHOOT_IMPACT_TICKS) {
                    applyGuaranteedHit();
                    hitApplied = true;
                    recoilRad = (float)Math.toRadians(-5.0);
                }
                recoilRad *= 0.85f;
                if (elapsed >= SHOOT_IMPACT_TICKS + 20) {
                    shooting = false;
                }
            }
        }
    }

    public void addBlood(int amount) {
        int old = this.blood;
        this.blood = Math.max(0, Math.min(100, this.blood + amount));
        if (old < 100 && this.blood == 100) {
            ApostasyEntity t = getBeamTarget();
            if (t == null) t = findNearestApostasy(128.0);
            beginShootAt(t);
        }
    }

    public void beginShootAt(ApostasyEntity target) {
        if (shooting) return;
        shooting = true;
        hitApplied = false;
        shootStartTick = this.age;
        beamTargetUuid = target != null ? target.getUuid() : null;
        blood = 0;
        if (!getWorld().isClient) {
            getWorld().playSound(null, getBlockPos(), ModSounds.ARC_SHOOT, SoundCategory.HOSTILE, 1f, 1f);
        }
    }

    private void applyGuaranteedHit() {
        ApostasyEntity t = getBeamTarget();
        if (t == null || !t.isAlive()) t = findNearestApostasy(128.0);
        if (t == null) return;
        ServerWorld sw = (ServerWorld) getWorld();
        LivingEntity attacker = this;
        t.damage(sw.getDamageSources().mobAttack(attacker), 150f);
        t.velocityModified = true;
    }

    public ApostasyEntity getBeamTarget() {
        if (beamTargetUuid == null) return null;
        Entity e = getWorld() instanceof ServerWorld sw ? sw.getEntity(beamTargetUuid) : null;
        return e instanceof ApostasyEntity a ? a : null;
    }

    private ApostasyEntity findNearestApostasy(double range) {
        double r2 = range * range;
        return getWorld()
                .getEntitiesByClass(ApostasyEntity.class, getBoundingBox().expand(range), e -> true)
                .stream()
                .filter(Entity::isAlive)
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(this)))
                .filter(e -> e.squaredDistanceTo(this) <= r2)
                .orElse(null);
    }

    private float wrap(float a) {
        a %= 360f;
        if (a < -180f) a += 360f;
        if (a > 180f) a -= 360f;
        return a;
    }

    private float stepAngle(float from, float to, float maxStep) {
        float d = wrap(to - from);
        float s = MathHelper.clamp(d, -maxStep, maxStep);
        return from + s;
    }

    @Override public boolean isPushable() { return false; }
    @Override public void pushAwayFrom(Entity entity) {}
    @Override public void takeKnockback(double s, double x, double z) {}
    @Override public boolean isPushedByFluids() { return false; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override protected void initGoals() {}

    public boolean isShooting() { return shooting; }
    public int getShootStartTick() { return shootStartTick; }
    public float getFullBodyYaw() { return getDataTracker().get(FULL_YAW); }
    public float getHeadPitchDeg() { return getDataTracker().get(HEAD_PITCH); }
    public float getRecoilRad() { return recoilRad; }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        this.blood = nbt.getInt("Blood");
        this.shooting = nbt.getBoolean("Shooting");
        this.hitApplied = nbt.getBoolean("HitApplied");
        this.shootStartTick = nbt.getInt("ShootStart");
        if (nbt.containsUuid("BeamTgt")) this.beamTargetUuid = nbt.getUuid("BeamTgt");
        this.desiredYaw = nbt.getFloat("DesYaw");
        this.desiredPitch = nbt.getFloat("DesPitch");
        this.getDataTracker().set(FULL_YAW, nbt.getFloat("TrackYaw"));
        this.getDataTracker().set(HEAD_PITCH, nbt.getFloat("TrackPitch"));
        this.recoilRad = nbt.getFloat("Recoil");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Blood", this.blood);
        nbt.putBoolean("Shooting", this.shooting);
        nbt.putBoolean("HitApplied", this.hitApplied);
        nbt.putInt("ShootStart", this.shootStartTick);
        if (this.beamTargetUuid != null) nbt.putUuid("BeamTgt", this.beamTargetUuid);
        nbt.putFloat("DesYaw", this.desiredYaw);
        nbt.putFloat("DesPitch", this.desiredPitch);
        nbt.putFloat("TrackYaw", this.getDataTracker().get(FULL_YAW));
        nbt.putFloat("TrackPitch", this.getDataTracker().get(HEAD_PITCH));
        nbt.putFloat("Recoil", this.recoilRad);
    }

    private PlayState controller(AnimationState<ArcangelEntity> state) {
        AnimationController<ArcangelEntity> controller = state.getController();


        boolean shootingNow = this.shooting && (this.age - shootStartTick < SHOOT_IMPACT_TICKS);

        if (shootingNow) {

            controller.forceAnimationReset();
            controller.setAnimation(SHOOT);
        } else {

            controller.setAnimation(IDLE);
        }

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
}
