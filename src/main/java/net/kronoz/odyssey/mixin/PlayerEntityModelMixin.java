package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.client.bridge.WallRunAccess;
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
        if (st == null || !st.active() || st.normal == null) return;

        PlayerEntityModel<?> m = (PlayerEntityModel<?>)(Object)this;

        Vec3d look = p.getRotationVec(1).multiply(1, 0, 1);
        if (look.lengthSquared() < 1e-6) look = new Vec3d(1, 0, 0);
        Vec3d right = new Vec3d(look.z, 0, -look.x).normalize();
        boolean wallOnRight = right.dotProduct(st.normal) >= 0.0;

        if (wallOnRight) {
            m.head.pitch = H_P;  m.head.yaw = H_Y;  m.head.roll = H_R;
            m.body.pitch = B_P;  m.body.yaw = B_Y;  m.body.roll = B_R;

            m.rightArm.pitch = LA_P; m.rightArm.yaw = LA_Y; m.rightArm.roll = LA_R;
            m.leftArm.pitch  = RA_P; m.leftArm.yaw  = RA_Y; m.leftArm.roll  = RA_R;

            m.rightLeg.pitch = RL_P; m.rightLeg.yaw = RL_Y; m.rightLeg.roll = LL_R;
            m.leftLeg.pitch  = LL_P; m.leftLeg.yaw  = LL_Y; m.leftLeg.roll  = RL_R;
        } else {
            m.head.pitch =  H_P;  m.head.yaw = -H_Y;  m.head.roll = -H_R;
            m.body.pitch =  B_P;  m.body.yaw =  B_Y;  m.body.roll = -B_R;

            m.rightArm.pitch = RA_P; m.rightArm.yaw = -RA_Y; m.rightArm.roll = -RA_R;
            m.leftArm.pitch  = LA_P; m.leftArm.yaw  = -LA_Y; m.leftArm.roll  = -LA_R;

            m.rightLeg.pitch = LL_P; m.rightLeg.yaw = -LL_Y; m.rightLeg.roll = -RL_R;
            m.leftLeg.pitch  = RL_P; m.leftLeg.yaw  = -RL_Y; m.leftLeg.roll  = -LL_R;
        }

        m.hat.copyTransform(m.head);
        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }
}
