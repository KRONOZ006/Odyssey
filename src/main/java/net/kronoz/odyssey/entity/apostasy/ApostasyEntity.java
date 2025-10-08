package net.kronoz.odyssey.entity.apostasy;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;

public class ApostasyEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private ApostasyRingAnimator.Result ringPose;


    public ApostasyEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(8, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        if (!this.getWorld().isClient) {
            double g = sampleGroundYPlus(getX(), getY(), getZ(), 32);
            double ty = g + 4.0;
            double dy = ty - getY();
            double vy = MathHelper.clamp(dy * 0.25, -0.35, 0.35);
            setVelocity(getVelocity().x * 0.9, vy, getVelocity().z * 0.9);
            move(MovementType.SELF, getVelocity());
        }
        ringPose = ApostasyRingAnimator.sample(getId(), age);
    }

    public ApostasyRingAnimator.Result getRingPose() {
        return ringPose;
    }

    private double sampleGroundYPlus(double x, double y, double z, double maxDown) {
        var from = new Vec3d(x, y + 1.5, z);
        var to = new Vec3d(x, y - maxDown, z);
        var hit = this.getWorld().raycast(new net.minecraft.world.RaycastContext(
                from, to,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                this));
        if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return hit.getPos().y;
        return this.getWorld().getBottomY();
    }

    @Override
    public boolean hasNoGravity() { return true; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) { super.writeCustomDataToNbt(nbt); }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) { super.readCustomDataFromNbt(nbt); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
