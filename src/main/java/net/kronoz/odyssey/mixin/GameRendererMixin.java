package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.movement.MovementVisuals;
import net.kronoz.odyssey.systems.cinematics.runtime.CameraOverrideController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @ModifyVariable(method = "getFov", at = @At("HEAD"), argsOnly = true)
    private float odyssey$overrideFov(float original){
        CameraOverrideController ctrl = CameraOverrideController.I;
        if(ctrl.active){
            return ctrl.fov;
        }
        return original;
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void odyssey$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double base = cir.getReturnValueD();
        cir.setReturnValue(base * MovementVisuals.fovScale());
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void odyssey$roll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        float roll = MovementVisuals.rollDegrees();
        if (Math.abs(roll) > 0.01f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }
}