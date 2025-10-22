package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.systems.cam.ScreenShake;
import net.kronoz.odyssey.systems.cinematics.runtime.CameraOverrideController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private float yaw;
    @Shadow private float pitch;
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow public abstract Vec3d getPos();
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void odyssey$applyAdditiveFollow(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci){
        CameraOverrideController ctrl = CameraOverrideController.I;
        if(!ctrl.active) return;

        Vec3d basePos = this.getPos();
        float baseYaw = this.getYaw();
        float basePitch = this.getPitch();

        if(ctrl.mode == CameraOverrideController.Mode.ABSOLUTE){
            double x = ctrl.posX, y = ctrl.posY, z = ctrl.posZ;
            if(ctrl.lockX) x = basePos.x;
            if(ctrl.lockY) y = basePos.y;
            if(ctrl.lockZ) z = basePos.z;

            this.setRotation(ctrl.yaw, ctrl.pitch);
            this.setPos(x, y, z);
            return;
        }

        Vec3d local = new Vec3d(ctrl.offsetX, ctrl.offsetY, ctrl.offsetZ);

        float yawRad = (float) Math.toRadians(-baseYaw);
        double cosY = Math.cos(yawRad), sinY = Math.sin(yawRad);
        Vec3d ry = new Vec3d(
                local.x * cosY - local.z * sinY,
                local.y,
                local.x * sinY + local.z * cosY
        );

        float pitchRad = (float) Math.toRadians(basePitch);
        double cosX = Math.cos(pitchRad), sinX = Math.sin(pitchRad);
        Vec3d rxy = new Vec3d(
                ry.x,
                ry.y * cosX - ry.z * sinX,
                ry.y * sinX + ry.z * cosX
        );

        double fx = basePos.x + rxy.x;
        double fy = basePos.y + rxy.y;
        double fz = basePos.z + rxy.z;

        if(ctrl.lockX) fx = basePos.x;
        if(ctrl.lockY) fy = basePos.y;
        if(ctrl.lockZ) fz = basePos.z;

        float finalYaw = baseYaw + ctrl.yawOffset;
        float finalPitch = MathHelper.clamp(basePitch + ctrl.pitchOffset, -90f, 90f);

        this.setRotation(finalYaw, finalPitch);
        this.setPos(fx, fy, fz);
    }

    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void odyssey$afterUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        ScreenShake.update();

        float shakeX = ScreenShake.getShakeX();
        float shakeY = ScreenShake.getShakeY();

        if (Math.abs(shakeX) < 1e-4f && Math.abs(shakeY) < 1e-4f) return;

        float cy = MathHelper.cos((float) Math.toRadians(this.yaw));
        float sy = MathHelper.sin((float) Math.toRadians(this.yaw));
        float cp = MathHelper.cos((float) Math.toRadians(this.pitch));
        float sp = MathHelper.sin((float) Math.toRadians(this.pitch));

        Vector3f right = new Vector3f(cy, 0, -sy).normalize();
        Vector3f up    = new Vector3f(-sy * sp, cp, -cy * sp).normalize();

        Vector3f delta = new Vector3f(right).mul(shakeX).add(new Vector3f(up).mul(shakeY));

        Camera self = (Camera) (Object) this;
        CameraAccessor acc = (CameraAccessor) self;
        acc.odyssey$setPos(self.getPos().x + delta.x, self.getPos().y + delta.y, self.getPos().z + delta.z);
    }
}
