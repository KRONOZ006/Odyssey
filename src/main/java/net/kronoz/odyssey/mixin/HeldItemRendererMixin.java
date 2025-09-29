package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.render.SlotItemResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void odyssey$renderFP(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                  Hand hand, float swingProgress, ItemStack heldStack, float equipProgress,
                                  MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (player == null) return;

        final Arm arm = (hand == Hand.MAIN_HAND) ? player.getMainArm() : player.getMainArm().getOpposite();
        final boolean left = (arm == Arm.LEFT);
        final float side = left ? -1f : 1f;

        final Identifier partItemId = SlotItemResolver.resolve(left ? "left_arm" : "right_arm", player);
        final boolean hasPart = partItemId != null && Registries.ITEM.containsId(partItemId);

        if (!OdysseyConfig.enableFirstPersonOverride && !hasPart) return;

        final float equip = 1.0f - equipProgress;
        final float swing = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        final float swingAmp = swing * Math.max(0f, OdysseyConfig.swingIntensity);
        final float equipAmp = equip * Math.max(0f, OdysseyConfig.equipIntensity);
        final float pitchNorm = MathHelper.clamp(pitch / 90f, -1f, 1f);

        final ItemRenderer ir = MinecraftClient.getInstance().getItemRenderer();

        matrices.push();

        boolean drewHeldViaSocket = false;

        if (hasPart && OdysseyConfig.renderArmOverlay) {
            Item partItem = Registries.ITEM.get(partItemId);
            ItemStack partStack = new ItemStack(partItem);

            matrices.push();
            applyOverlayPose(matrices, side, swingAmp, equipAmp, pitchNorm);
            ir.renderItem(player, partStack,
                    left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                    left, matrices, vcp, player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
            matrices.pop();

            drewHeldViaSocket = true;
        }

        if (!drewHeldViaSocket && heldStack != null && !heldStack.isEmpty()) {
            matrices.push();
            applyHeldPose(matrices, side, swingAmp, equipAmp, pitchNorm);
            ir.renderItem(player, heldStack,
                    left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                    left, matrices, vcp, player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
            matrices.pop();
        }

        matrices.pop();
        ci.cancel();
    }

    private static void applyHeldPose(MatrixStack m, float side, float swingAmp, float equipAmp, float pitchNorm) {
        final float PITCH_CLAMP = 0.35f;
        float limitedPitch = MathHelper.clamp(pitchNorm, -PITCH_CLAMP, PITCH_CLAMP);
        float pitchAbs = Math.abs(limitedPitch);

        float baseX = side * OdysseyConfig.heldBaseX
                + side * (OdysseyConfig.swingX * swingAmp + OdysseyConfig.equipX * equipAmp);
        float baseY = OdysseyConfig.heldBaseY
                + (OdysseyConfig.swingY * swingAmp + OdysseyConfig.equipY * equipAmp);
        float baseZ = OdysseyConfig.heldBaseZ
                + (OdysseyConfig.swingZ * swingAmp + OdysseyConfig.equipZ * equipAmp);
        m.translate(baseX, baseY, baseZ);

        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(OdysseyConfig.heldRotX));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * OdysseyConfig.heldRotY));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * OdysseyConfig.heldRotZ));

        float localDX = side * OdysseyConfig.inwardXMax * pitchAbs;
        float localDY = -OdysseyConfig.dropYMax * (swingAmp + pitchAbs);
        float localDZ =  OdysseyConfig.pushZMax * (swingAmp + pitchAbs);
        m.translate(localDX, localDY, localDZ);

        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-swingAmp * OdysseyConfig.armSwingRotXDeg));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees( side   * swingAmp * OdysseyConfig.armSwingRotYDeg));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees( side   * swingAmp * OdysseyConfig.armSwingRotZDeg));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * equipAmp * OdysseyConfig.armEquipRollDeg));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(limitedPitch * OdysseyConfig.armPitchXDeg));

        m.scale(OdysseyConfig.heldScale, OdysseyConfig.heldScale, OdysseyConfig.heldScale);
    }

    private static void applyOverlayPose(MatrixStack m, float side, float swingAmp, float equipAmp, float pitchNorm) {
        float x = side * OdysseyConfig.armBaseX;
        float y = OdysseyConfig.armBaseY - OdysseyConfig.dropYMax * swingAmp;
        float z = OdysseyConfig.armBaseZ + OdysseyConfig.pushZMax * swingAmp;

        m.translate(x, y, z);

        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(OdysseyConfig.armRotX));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * OdysseyConfig.armRotY));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * OdysseyConfig.armRotZ));

        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-swingAmp * OdysseyConfig.armSwingRotXDeg));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * swingAmp * OdysseyConfig.armSwingRotYDeg));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * swingAmp * OdysseyConfig.armSwingRotZDeg));

        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * equipAmp * OdysseyConfig.armEquipRollDeg));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchNorm * OdysseyConfig.armPitchXDeg));

        m.scale(OdysseyConfig.armScale, OdysseyConfig.armScale, OdysseyConfig.armScale);
    }
}
