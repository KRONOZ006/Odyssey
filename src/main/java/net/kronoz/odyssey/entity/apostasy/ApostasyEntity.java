package net.kronoz.odyssey.entity.apostasy;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;

public class ApostasyEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private static final TrackedData<Float> TRACK_HEAD_PITCH =
            DataTracker.registerData(ApostasyEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public ApostasyEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 20;
        this.setNoGravity(true);

    }
    @Override
    public boolean hasNoGravity() { return true; }
    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 220.0)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TRACK_HEAD_PITCH, 0f);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new ApostasyHeadLookGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        this.setBodyYaw(this.getYaw());
        this.setHeadYaw(this.getYaw());
        if (!this.horizontalCollision) this.setVelocity(0, getVelocity().y, 0);

        if (!this.getWorld().isClient) {
            this.setNoGravity(true);

            double desired = sampleGroundYPlus(this.getX(), this.getY(), this.getZ(), 32) + 4.0;
            double dy = desired - this.getY();
            double vy = MathHelper.clamp(dy * 0.25, -0.35, 0.35);
            this.setVelocity(this.getVelocity().x * 0.90, vy, this.getVelocity().z * 0.90);
            this.velocityDirty = true;
            this.move(MovementType.SELF, this.getVelocity());
        }
    }
    private double sampleGroundYPlus(double x, double y, double z, double maxDown) {
        var from = new Vec3d(x, y + 1.5, z);
        var to   = new Vec3d(x, y - maxDown, z);
        var hit = this.getWorld().raycast(new net.minecraft.world.RaycastContext(
                from, to,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                this));
        if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            return hit.getPos().y;
        }
        return this.getWorld().getBottomY();
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {}

    public void setHeadPitch(float pitchRad) { this.dataTracker.set(TRACK_HEAD_PITCH, pitchRad); }
    public float getHeadPitch() { return this.dataTracker.get(TRACK_HEAD_PITCH); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(DefaultAnimations.genericIdleController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
