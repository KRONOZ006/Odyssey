package net.kronoz.odyssey.entity.thrasher;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;

public class ThrasherEntity extends PathAwareEntity implements GeoEntity {
    protected float jumpStrength = 10 ;
    protected boolean inAir;
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    public ThrasherEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new MoveControl(this);
        this.experiencePoints = 0;
    }

    @Nullable
    public boolean isInAir() {
        return this.inAir;
    }

    public void setInAir(boolean inAir) {
        this.inAir = inAir;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 10.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0f);

    }



    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }


    private PlayState predicate(AnimationState<ThrasherEntity> s) {
        if (s.isMoving()) s.getController().setAnimation(RawAnimation.begin().then("walk", Animation.LoopType.LOOP));

        else s.getController().setAnimation(RawAnimation.begin().then("new", Animation.LoopType.LOOP));
        return PlayState.CONTINUE;

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }


    public void setJumpStrength(int strength) {

            this.jumping = true;
            this.jumpStrength = 0.4F + 0.4F * strength / 90.0F; // or your scaling

    }

    protected void putPlayerOnBack(PlayerEntity player) {
        if (!this.getWorld().isClient) {
            player.setYaw(this.getYaw());
            player.setPitch(this.getPitch());
            player.startRiding(this);
        }

    }

    @Override
    public Vec3d getPassengerRidingPos(Entity passenger) {

        Vec3d base = this.getPos();


        double forwardOffset = -0.2D;
        double heightOffset = 1.5D;
        double sideOffset = 0.0D;


        float yawRad = (float) Math.toRadians(-this.getYaw());
        double x = base.x + forwardOffset * Math.sin(yawRad) + sideOffset * Math.cos(yawRad);
        double y = base.y + heightOffset;
        double z = base.z + forwardOffset * Math.cos(yawRad) - sideOffset * Math.sin(yawRad);

        return new Vec3d(x, y, z);
    }







    @Override
    public void travel(Vec3d movementInput) {
        if (this.hasPassengers()) {
            LivingEntity rider = (LivingEntity) this.getFirstPassenger();

            float targetYaw = rider.getYaw();
            float lerpedYaw = lerpYaw(this.getYaw(), targetYaw, 0.05f); // 0.05 = heavy
            this.setYaw(lerpedYaw);


            this.bodyYaw = lerpedYaw;
            this.headYaw = lerpedYaw;

            double speed = this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            double sideways = rider.sidewaysSpeed * 0.5 * speed;
          double forward = rider.forwardSpeed * speed;
         if (forward <= 0) forward *= 0.25;

         if (this.isOnGround() && jumpStrength > 0) {
               this.jump(0.5f, new Vec3d(sideways, 0, forward));
          }




//
            Vec3d look = rider.getRotationVec(1f);
            look = new Vec3d(rider.getYaw(), 0, rider.getYaw()).normalize(); // ignore Y
        super.travel(look.multiply(speed));

        } else {
           super.travel(movementInput);
       }
    }



    protected void jump(float strength, Vec3d movementInput) {
        double d = (double)this.getJumpVelocity(strength);
        Vec3d vec3d = this.getVelocity();
        this.setVelocity(vec3d.x, 0, vec3d.z);
        this.setInAir(true);
        this.velocityDirty = true;
        if (movementInput.z > (double)0.0F) {
            float f = MathHelper.sin(this.getYaw() * ((float)Math.PI / 180F));
            float g = MathHelper.cos(this.getYaw() * ((float)Math.PI / 180F));
            this.setVelocity(this.getVelocity().add((double)(-0.4F * f * strength), (double)0.0F, (double)(0.4F * g * strength)));
        }

    }




    protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
        super.tickControlled(controllingPlayer, movementInput);
        Vec2f vec2f = this.getControlledRotation(controllingPlayer);
        this.setRotation(vec2f.y, vec2f.x);
        this.prevYaw = this.bodyYaw = this.headYaw = this.getYaw();
        if (this.isLogicalSideForUpdatingMovement()) {
            if (movementInput.z <= (double)0.0F) {

            }

            if (this.isOnGround()) {
                this.setInAir(false);
                if (this.jumpStrength > 0.0F && !this.isInAir()) {
                    this.jump(this.jumpStrength, movementInput);
                }

                this.jumpStrength = 0.0F;
            }
        }

        controllingPlayer.kill();

    }
    private float lerpYaw(float currentYaw, float targetYaw, float speed) {
        float delta = MathHelper.wrapDegrees(targetYaw - currentYaw); // ensures -180..180
        return currentYaw + delta * speed;
    }

    protected Vec2f getControlledRotation(LivingEntity controllingPassenger) {
        return new Vec2f(controllingPassenger.getPitch() * MathHelper.lerp(0.1f, 0.5f, 1f), controllingPassenger.getYaw());
    }

    protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
        if (this.isOnGround() && this.jumpStrength == 0.0F  && !this.jumping) {
            return Vec3d.ZERO;
        } else {
            float f = controllingPlayer.sidewaysSpeed * 0.5F;
            float g = controllingPlayer.forwardSpeed;
            controllingPlayer.kill();
            if (g <= 0.0F) {
                g *= 0.25F;
            }

            return new Vec3d((double)f, (double)0.0F, (double)g);
        }
    }




    protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
        return (float)this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
    }


    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.hasPassengers() && !this.isBaby()) {


                this.putPlayerOnBack(player);
                return ActionResult.success(this.getWorld().isClient);
            }
         else {
            return super.interactMob(player, hand);
        }
    }


}

