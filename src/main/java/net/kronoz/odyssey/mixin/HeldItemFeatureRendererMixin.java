package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.systems.render.SlotItemResolver;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public class HeldItemFeatureRendererMixin<T extends LivingEntity> {

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void odyssey$hideTP(MatrixStack matrices, VertexConsumerProvider vcp, int light, T entity,
                                float limbAngle, float limbDistance, float tickDelta,
                                float customAngle, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof net.minecraft.entity.player.PlayerEntity player)) return;

        Identifier leftId = SlotItemResolver.resolve("left_arm", player);
        Identifier rightId = SlotItemResolver.resolve("right_arm", player);
        boolean hasLeft = leftId != null && Registries.ITEM.containsId(leftId);
        boolean hasRight = rightId != null && Registries.ITEM.containsId(rightId);

        if (hasLeft || hasRight) ci.cancel();
    }
}
