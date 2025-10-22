package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.systems.cam.CameraSway;
import net.kronoz.odyssey.systems.cam.RapidShake;
import net.kronoz.odyssey.systems.cam.ScreenShake;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererShakeMixin {


    @Redirect(
            method = "renderWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V"),
            require = 0
    )
    private void ant$wrapBobView(GameRenderer instance, MatrixStack matrices, float tickDelta) {

        var cam = net.minecraft.client.MinecraftClient.getInstance().getCameraEntity();
        if (CameraSway.isActiveFor(cam)) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
                    CameraSway.getPitchDegFor(cam)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                    CameraSway.getRollDegFor(cam)));
        }

        if (RapidShake.isActive()) {
            float roll = RapidShake.getRollDeg();
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        }

    }

    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"), require = 0)
    private void ant$rollInHurtTilt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        var cam = net.minecraft.client.MinecraftClient.getInstance().getCameraEntity();
        if (CameraSway.isActiveFor(cam)) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
                    CameraSway.getPitchDegFor(cam)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                    CameraSway.getRollDegFor(cam)));
        }
    }
}
