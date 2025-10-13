package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.systems.cam.CameraSway;
import net.kronoz.odyssey.systems.cam.RapidShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraShakeMixin {

    @Shadow private Vec3d pos;

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void ant$applyRapidJitter(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {

        RapidShake.update();
        if (!RapidShake.isActive() || focusedEntity == null) return;

        Vec3d fwd = focusedEntity.getRotationVec(tickDelta).normalize();
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x).normalize();
        Vec3d up = fwd.crossProduct(right).normalize();
        if (Double.isNaN(up.length()) || up.lengthSquared() < 1e-6) {
            up = new Vec3d(0, 1, 0);
        }

        double dx = RapidShake.getX();
        double dy = RapidShake.getY();

        Vec3d delta = right.multiply(dx).add(up.multiply(dy));
        this.pos = this.pos.add(delta);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void ant$applyShakeAndSway(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraSway.update(focusedEntity, tickDelta);
    }
}
