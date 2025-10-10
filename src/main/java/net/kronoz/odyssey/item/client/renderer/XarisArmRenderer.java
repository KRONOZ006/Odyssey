package net.kronoz.odyssey.item.client.renderer;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.XarisArm;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.specialty.DynamicGeoItemRenderer;
import software.bernie.geckolib.util.Color;

import java.util.Objects;

public class XarisArmRenderer extends DynamicGeoItemRenderer<XarisArm> {

    private static final Identifier MODEL_ID = Identifier.of(Odyssey.MODID, "xaris");

    private static final String BONE_NAME = "wrist_socket";

    private static final float LOCAL_OFF_X   = 0.00f;
    private static final float LOCAL_OFF_Y   = -0.20f;
    private static final float LOCAL_OFF_Z   = 0.00f;
    private static final float LOCAL_ROT_X_D = 0.50f;   // degrees
    private static final float LOCAL_ROT_Y_D = -0.30f;
    private static final float LOCAL_ROT_Z_D = -0.90f;
    private static final float LOCAL_SCALE   = 0.30f;

    private static final boolean DEBUG_DRAW_SOCKET_CUBES = false;

    private static final ThreadLocal<Boolean> IN_SOCKET_PASS = ThreadLocal.withInitial(() -> false);

    public XarisArmRenderer() {
        super(new DefaultedItemGeoModel<>(MODEL_ID));
    }

    @Override
    protected boolean boneRenderOverride(
            MatrixStack ms,
            GeoBone bone,
            VertexConsumerProvider buffers,
            VertexConsumer buffer,
            float pt,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        if (bone.isHidden() || !Objects.equals(BONE_NAME, bone.getName())) {
            return false;
        }

        ItemStack current = this.getCurrentItemStack();
        if (!(current.getItem() instanceof XarisArm)) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;

        ItemStack held = mc.player.getMainHandStack();
        if (held == null || held.isEmpty()) held = mc.player.getOffHandStack();
        if (held == null || held.isEmpty()) return false;

        if (held.getItem() instanceof XarisArm) return false;

        if (IN_SOCKET_PASS.get()) return false;

        IN_SOCKET_PASS.set(true);
        ms.push();
        try {
            if (LOCAL_OFF_X != 0 || LOCAL_OFF_Y != 0 || LOCAL_OFF_Z != 0) {
                ms.translate(LOCAL_OFF_X, LOCAL_OFF_Y, LOCAL_OFF_Z);
            }
            if (LOCAL_ROT_X_D != 0) ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(LOCAL_ROT_X_D));
            if (LOCAL_ROT_Y_D != 0) ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(LOCAL_ROT_Y_D));
            if (LOCAL_ROT_Z_D != 0) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(LOCAL_ROT_Z_D));
            if (LOCAL_SCALE != 1.0f) ms.scale(LOCAL_SCALE, LOCAL_SCALE, LOCAL_SCALE);

            ItemRenderer ir = mc.getItemRenderer();
            ir.renderItem(
                    mc.player,
                    held,
                    ModelTransformationMode.NONE,
                    false,
                    ms,
                    buffers,
                    mc.player.getWorld(),
                    packedLight,
                    OverlayTexture.DEFAULT_UV,
                    mc.player.getId()
            );
        } finally {
            ms.pop();
            IN_SOCKET_PASS.set(false);
        }

        if (DEBUG_DRAW_SOCKET_CUBES) {
            var vc = buffers.getBuffer(net.minecraft.client.render.RenderLayer.getLines());
            ms.push();
            for (GeoCube cube : bone.getCubes()) {
                renderCube(ms, cube, vc, packedLight, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
            }
            ms.pop();
        }
        if (bone.getName().equals("glow")) {
            VertexConsumer vertexConsumer = buffers.getBuffer(RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/item/xaris.png")));

            if (bone.isHidden())
                return false;

            for (GeoCube cube : bone.getCubes()) {
                ms.push();
                renderCube(ms, cube, vertexConsumer, 15728640, OverlayTexture.DEFAULT_UV, Color.WHITE.getColor());
                ms.pop();
            }
            return true;
        }


        return false;
    }
}
