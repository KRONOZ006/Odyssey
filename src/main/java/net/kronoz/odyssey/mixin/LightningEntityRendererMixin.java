package net.kronoz.odyssey.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kronoz.odyssey.systems.physics.lightning.BetterLightningRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LightningEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LightningEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningEntityRenderer.class)
public abstract class LightningEntityRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/entity/LightningEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ant$renderThinBetterLightning(LightningEntity entity, float entityYaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        ci.cancel();

        RenderSystem.enableBlend();
        BetterLightningRenderer.render(entity, matrices, consumers, tickDelta);
        RenderSystem.disableBlend();

    }
}
