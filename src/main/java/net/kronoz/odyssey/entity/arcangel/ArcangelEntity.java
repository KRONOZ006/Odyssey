package net.kronoz.odyssey.entity.arcangel;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;

public class ArcangelEntity extends HostileEntity implements GeoAnimatable {
    public static final TrackedData<Float> FULL_YAW =
            DataTracker.registerData(ArcangelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> HEAD_PITCH =
            DataTracker.registerData(ArcangelEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    private float desiredYaw;
    private float desiredPitch;

    private static final float YAW_DEG_PER_TICK = 1.2f;
    private static final float HEAD_LERP = 0.35f;

    public ArcangelEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setNoGravity(false);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
                .add(EntityAttributes.GENERIC_ARMOR, 20.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
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
            // Find nearest Apostasy every tick
            ApostasyEntity target = getWorld()
                    .getEntitiesByClass(ApostasyEntity.class, getBoundingBox().expand(128.0), e -> true)
                    .stream()
                    .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(this)))
                    .orElse(null);

            if (target != null) {
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();
                double dy = target.getEyeY() - (this.getY() + this.getStandingEyeHeight());

                float yaw = (float) (MathHelper.atan2(dz, dx) * (180f / Math.PI)) - 90f;
                float distHoriz = MathHelper.sqrt((float) (dx * dx + dz * dz));
                float pitch = (float) (-(MathHelper.atan2(dy, distHoriz) * (180f / Math.PI)));

                desiredYaw = wrapAngle(yaw);
                desiredPitch = MathHelper.clamp(pitch, -60f, 60f);
            }

            float currentFullYaw = getDataTracker().get(FULL_YAW);
            float currentHeadPitch = getDataTracker().get(HEAD_PITCH);

            float newFullYaw = approachAngle(currentFullYaw, desiredYaw, YAW_DEG_PER_TICK);
            float newHeadPitch = MathHelper.lerp(HEAD_LERP, currentHeadPitch, desiredPitch);

            getDataTracker().set(FULL_YAW, wrapAngle(newFullYaw));
            getDataTracker().set(HEAD_PITCH, newHeadPitch);
        }
    }

    private float wrapAngle(float a) {
        a = a % 360f;
        if (a < -180f) a += 360f;
        if (a > 180f) a -= 360f;
        return a;
    }

    private float approachAngle(float from, float to, float maxDeltaDegPerTick) {
        float delta = wrapAngle(to - from);
        float step = MathHelper.clamp(delta, -maxDeltaDegPerTick, maxDeltaDegPerTick);
        return from + step;
    }
    /* ======== IMMOBILE (no push/KB/impulses) & INVULNÉRABLE, WITH GRAVITY ======== */
    @Override public boolean isPushable() { return false; }
    @Override public void pushAwayFrom(net.minecraft.entity.Entity e) { }
    @Override public void takeKnockback(double s, double x, double z) { }
    @Override public void addVelocity(double x, double y, double z) { }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) {return false;}
    @Override public boolean isPersistent() {return true;}

    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean isAttackable() { return false; }

    /* ======== AI ======== */
    @Override protected void initGoals() { }

    /* ======== GeckoLib ======== */
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animCache; }
    @Override public double getTick(Object o) { return this.age; }
}
