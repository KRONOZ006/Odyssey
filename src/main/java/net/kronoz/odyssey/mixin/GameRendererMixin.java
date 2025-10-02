package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.systems.cinematics.runtime.CameraOverrideController;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Override le FOV quand une cutscene est active.
 */
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
}
