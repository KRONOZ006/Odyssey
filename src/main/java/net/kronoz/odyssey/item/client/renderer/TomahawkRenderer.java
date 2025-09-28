package net.kronoz.odyssey.item.client.renderer;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.specialty.DynamicGeoItemRenderer;
import software.bernie.geckolib.util.Color;

import java.util.UUID;

public class TomahawkRenderer extends DynamicGeoItemRenderer<TomahawkItem> {

    private static final Identifier MODEL_ID   = Identifier.of(Odyssey.MODID, "tomahawk");
    private static final String     BONE_NAME  = "cloth_0";

    private static final boolean DEBUG_DRAW_BONE = false;
    private static final Identifier DEBUG_TEX    = Identifier.of(Odyssey.MODID, "textures/cloth/tomahawk_cloth.png");
    private static final RenderLayer DEBUG_LAYER = RenderLayer.getEntityTranslucent(DEBUG_TEX);
    private static final int FULL_LIGHT = 0x00F000F0;
    private static final int WHITE = Color.WHITE.getColor();

    public TomahawkRenderer() {
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
        if (bone.isHidden() || !BONE_NAME.equals(bone.getName())) return false;

        ItemStack current = this.getCurrentItemStack();
        if (!(current.getItem() instanceof TomahawkItem)) return false;

        BonePose pose = computeBonePose(ms.peek().getPositionMatrix(), bone);

        MinecraftClient mc = MinecraftClient.getInstance();

        if (DEBUG_DRAW_BONE) {
            VertexConsumer vc = buffers.getBuffer(DEBUG_LAYER);
            for (GeoCube cube : bone.getCubes()) {
                ms.push();
                renderCube(ms, cube, vc, FULL_LIGHT, OverlayTexture.DEFAULT_UV, WHITE);
                ms.pop();
            }
            return true;
        }
        return false;
    }


    private static final class BonePose {
        Vec3d worldPos;
        Quaternionf orientation;
        Vector3f rightWS, upWS, forwardWS;
    }

    private static BonePose computeBonePose(Matrix4f m, GeoBone bone) {
        float lx = bone.getPosX() / 16f;
        float ly = bone.getPosY() / 16f;
        float lz = bone.getPosZ() / 16f;

        org.joml.Vector4f v = new org.joml.Vector4f(lx, ly, lz, 1f).mul(m);

        Vector3f xCol = new Vector3f(m.m00(), m.m10(), m.m20()).normalize();
        Vector3f yCol = new Vector3f(m.m01(), m.m11(), m.m21()).normalize();
        Vector3f zCol = new Vector3f(m.m02(), m.m12(), m.m22()).normalize();

        org.joml.Matrix3f rot = new org.joml.Matrix3f(
                xCol.x, yCol.x, zCol.x,
                xCol.y, yCol.y, zCol.y,
                xCol.z, yCol.z, zCol.z
        );
        Quaternionf q = new Quaternionf().setFromNormalized(rot);

        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vec3d worldPos = new Vec3d(cam.x + v.x, cam.y + v.y, cam.z + v.z);

        BonePose out = new BonePose();
        out.worldPos = worldPos;
        out.orientation = q;
        out.rightWS = xCol;
        out.upWS = yCol;
        out.forwardWS = zCol;
        return out;
    }
}
