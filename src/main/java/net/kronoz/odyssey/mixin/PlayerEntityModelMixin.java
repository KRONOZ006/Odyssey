package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.client.bridge.WallRunAccess;
import net.kronoz.odyssey.movement.MovementVisuals;
import net.kronoz.odyssey.movement.WallRun;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> {

    private static float r(double deg){ return (float)(deg * Math.PI / 180.0); }

    private static final float H_P = r(3.60386f), H_Y = r(10.13026f), H_R = r(-1.48295f);
    private static final float B_P = r(2.5f),     B_Y = r(0.0f),      B_R = r(5.0f);

    private static final float RA_P = r(-9.76758f), RA_Y = r(-2.15393f), RA_R = r(-12.31594f);
    private static final float LA_P = r(57.74852f), LA_Y = r(-6.08234f), LA_R = r(29.92948f);

    private static final float RL_P = r(-3.86412f), RL_Y = r(1.03449f), RL_R = r(16.9651f);
    private static final float LL_P = r(0.26511f),  LL_Y = r(-10.68729f), LL_R = r(33.90084f);

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void odyssey$applyWallRunPose(LivingEntity entity, float limbAngle, float limbDistance,
                                          float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity p)) return;
        if (!(p instanceof WallRunAccess acc)) return;
        WallRun.WallState st = acc.odyssey$getWallState();
        float poseWeight = MovementVisuals.wallPoseWeight();
        if ((st == null || !st.active() || st.normal == null) && poseWeight <= 0.001f) return;

        PlayerEntityModel<?> m = (PlayerEntityModel<?>)(Object)this;
        boolean wallOnRight;
        if (st != null && st.active() && st.normal != null) {
            Vec3d look = p.getRotationVec(1).multiply(1, 0, 1);
            if (look.lengthSquared() < 1e-6) look = new Vec3d(1, 0, 0);
            Vec3d right = new Vec3d(look.z, 0, -look.x).normalize();
            wallOnRight = right.dotProduct(st.normal) >= 0.0;
        } else {
            wallOnRight = MovementVisuals.wallPoseSide() >= 0.0f;
        }

        float blend = Math.max(0.0f, Math.min(1.0f, poseWeight));

        if (wallOnRight) {
            blendPart(m.head.pitch, H_P, blend, value -> m.head.pitch = value);
            blendPart(m.head.yaw, H_Y, blend, value -> m.head.yaw = value);
            blendPart(m.head.roll, H_R, blend, value -> m.head.roll = value);
            blendPart(m.body.pitch, B_P, blend, value -> m.body.pitch = value);
            blendPart(m.body.yaw, B_Y, blend, value -> m.body.yaw = value);
            blendPart(m.body.roll, B_R, blend, value -> m.body.roll = value);

            blendPart(m.rightArm.pitch, LA_P, blend, value -> m.rightArm.pitch = value);
            blendPart(m.rightArm.yaw, LA_Y, blend, value -> m.rightArm.yaw = value);
            blendPart(m.rightArm.roll, LA_R, blend, value -> m.rightArm.roll = value);
            blendPart(m.leftArm.pitch, RA_P, blend, value -> m.leftArm.pitch = value);
            blendPart(m.leftArm.yaw, RA_Y, blend, value -> m.leftArm.yaw = value);
            blendPart(m.leftArm.roll, RA_R, blend, value -> m.leftArm.roll = value);

            blendPart(m.rightLeg.pitch, RL_P, blend, value -> m.rightLeg.pitch = value);
            blendPart(m.rightLeg.yaw, RL_Y, blend, value -> m.rightLeg.yaw = value);
            blendPart(m.rightLeg.roll, LL_R, blend, value -> m.rightLeg.roll = value);
            blendPart(m.leftLeg.pitch, LL_P, blend, value -> m.leftLeg.pitch = value);
            blendPart(m.leftLeg.yaw, LL_Y, blend, value -> m.leftLeg.yaw = value);
            blendPart(m.leftLeg.roll, RL_R, blend, value -> m.leftLeg.roll = value);
        } else {
            blendPart(m.head.pitch, H_P, blend, value -> m.head.pitch = value);
            blendPart(m.head.yaw, -H_Y, blend, value -> m.head.yaw = value);
            blendPart(m.head.roll, -H_R, blend, value -> m.head.roll = value);
            blendPart(m.body.pitch, B_P, blend, value -> m.body.pitch = value);
            blendPart(m.body.yaw, B_Y, blend, value -> m.body.yaw = value);
            blendPart(m.body.roll, -B_R, blend, value -> m.body.roll = value);

            blendPart(m.rightArm.pitch, RA_P, blend, value -> m.rightArm.pitch = value);
            blendPart(m.rightArm.yaw, -RA_Y, blend, value -> m.rightArm.yaw = value);
            blendPart(m.rightArm.roll, -RA_R, blend, value -> m.rightArm.roll = value);
            blendPart(m.leftArm.pitch, LA_P, blend, value -> m.leftArm.pitch = value);
            blendPart(m.leftArm.yaw, -LA_Y, blend, value -> m.leftArm.yaw = value);
            blendPart(m.leftArm.roll, -LA_R, blend, value -> m.leftArm.roll = value);

            blendPart(m.rightLeg.pitch, LL_P, blend, value -> m.rightLeg.pitch = value);
            blendPart(m.rightLeg.yaw, -LL_Y, blend, value -> m.rightLeg.yaw = value);
            blendPart(m.rightLeg.roll, -RL_R, blend, value -> m.rightLeg.roll = value);
            blendPart(m.leftLeg.pitch, RL_P, blend, value -> m.leftLeg.pitch = value);
            blendPart(m.leftLeg.yaw, -RL_Y, blend, value -> m.leftLeg.yaw = value);
            blendPart(m.leftLeg.roll, -LL_R, blend, value -> m.leftLeg.roll = value);
        }

        m.hat.copyTransform(m.head);
        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }

    private interface FloatSetter {
        void set(float value);
    }

    private static void blendPart(float current, float target, float blend, FloatSetter setter) {
        setter.set(current + (target - current) * blend);
    }
}
