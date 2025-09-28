package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.render.Pose;
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

import java.util.Map;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    private static final Pose HELD_DEFAULT = new Pose(
            0.5f,  -0.28f, -1.30f,
            0.08f,  -0.05f, -0.14f,
            0.06f,  -0.06f, -0.12f,
            0.96f,  0.96f,  0.96f,
            -40f, 0f, 45f
    );

    private static final Pose OVERLAY_PART_DEFAULT = new Pose(
            0.32f,  -0.1f, -0.30f,
            0.06f,  -0.04f, -0.10f,
            0.05f,  -0.05f, -0.08f,
            0.94f,  0.94f,  0.94f,
            0f, 0f, 0f
    );

    private static final Map<Identifier, Pose> HELD_OVERRIDES = Map.of(
    );

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ody$fp(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                        Hand hand, float swingProgress, ItemStack heldStack, float equipProgress,
                        MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (player == null) return;

        final Arm arm  = (hand == Hand.MAIN_HAND) ? player.getMainArm() : player.getMainArm().getOpposite();
        final boolean left = (arm == Arm.LEFT);
        final float side = left ? -1f : 1f;
        final float equip = 1.0f - equipProgress;
        final float swing = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float)Math.PI);
        final ItemRenderer ir = MinecraftClient.getInstance().getItemRenderer();

        matrices.push();

        Identifier partItemId = SlotItemResolver.resolve(left ? "left_arm" : "right_arm", player);
        if (partItemId != null && Registries.ITEM.containsId(partItemId)) {
            Item partItem = Registries.ITEM.get(partItemId);
            ItemStack partStack = new ItemStack(partItem);
            applyPose(matrices, OVERLAY_PART_DEFAULT, side, equip, swing);
            ir.renderItem(player, partStack,
                    left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                    left, matrices, vcp, player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
            matrices.pop();
            matrices.push();
        }

        Pose pose = HELD_DEFAULT;
        if (heldStack != null && !heldStack.isEmpty()) {
            Identifier hid = Registries.ITEM.getId(heldStack.getItem());
            if (hid != null && HELD_OVERRIDES.containsKey(hid)) {
                pose = HELD_OVERRIDES.get(hid);
            }
            applyPose(matrices, pose, side, equip, swing);
            ir.renderItem(player, heldStack,
                    left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                    left, matrices, vcp, player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
        }

        matrices.pop();
        ci.cancel();
    }

    private static void applyPose(MatrixStack m, Pose p, float side, float equip, float swing) {
        float swingAmp = swing * 1.8f;
        float equipAmp = equip * 1.2f;

        float x = side * p.baseX + side * (p.swingX * swingAmp + p.equipX * equipAmp);
        float y = p.baseY + (p.swingY * swingAmp + p.equipY * equipAmp);
        float z = p.baseZ + (p.swingZ * swingAmp + p.equipZ * equipAmp);
        m.translate(x, y, z);

        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotXdeg));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * p.rotYdeg));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * p.rotZdeg));

        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-swingAmp * 60f));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * swingAmp * 30f));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * swingAmp * 25f));

        m.scale(p.sx, p.sy, p.sz);
    }


}
