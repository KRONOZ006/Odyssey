package net.kronoz.odyssey.entity.arcangel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.entity.VeilLightCompat;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import org.joml.Vector4f;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.specialty.DynamicGeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class ArcangelRenderer extends DynamicGeoEntityRenderer<ArcangelEntity> {

    private static final String LASER_BONE = "truelaser";

    private final Int2ObjectMap<float[]> lastBonePos = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Boolean> boneSeenThisFrame = new Int2ObjectOpenHashMap<>();

    private static final float L_R = 1.0f, L_G = 0.0f, L_B = 0.0f;
    private static final float L_BRIGHT = 12.0f;
    private static final float L_RADIUS = 56.0f;

    public ArcangelRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx) {
        super(ctx, new ArcangelModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    public Identifier getTextureLocation(ArcangelEntity animatable) {
        return new ArcangelModel().getTextureResource(animatable);
    }

    @Override
    protected boolean boneRenderOverride(MatrixStack poseStack,
                                         GeoBone bone,
                                         VertexConsumerProvider bufferSource,
                                         VertexConsumer buffer,
                                         float partialTick,
                                         int packedLight,
                                         int packedOverlay,
                                         int colour) {

        if (LASER_BONE.equals(bone.getName())) {
            var m = poseStack.peek().getPositionMatrix();
            // origin (0,0,0) of this bone in world
            Vector4f p = new Vector4f(0f, 0f, 0f, 1f).mul(m);
            int id = this.getAnimatable().getId();
            lastBonePos.put(id, new float[]{p.x, p.y, p.z});
            boneSeenThisFrame.put(id, Boolean.TRUE);
        }

        boolean isEmissive = bone.getName().equals("gem") ||
                bone.getName().equals("gemlight") ||
                bone.getName().startsWith("laserring") ||
                bone.getName().equals("laser");

        VertexConsumer vc;
        if (isEmissive) {
            vc = bufferSource.getBuffer(
                    RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/entity/arcangel.png"))
            );
            packedLight = 15728880;
        } else {
            vc = bufferSource.getBuffer(RenderLayer.getEntityCutout(getTextureLocation(this.getAnimatable())));
        }

        if (!bone.isHidden()) {
            poseStack.push();
            for (GeoCube cube : bone.getCubes()) {
                renderCube(poseStack, cube, vc, packedLight, OverlayTexture.DEFAULT_UV, colour);
            }
            poseStack.pop();
        }

        return true;
    }

    @Override
    public void render(ArcangelEntity entity, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider buffers, int packedLight) {

        boneSeenThisFrame.put(entity.getId(), Boolean.FALSE);

        super.render(entity, entityYaw, tickDelta, matrices, buffers, packedLight);

        applyVeilLightFor(entity);
    }

    private void applyVeilLightFor(ArcangelEntity e) {
        boolean shooting = e.getDataTracker().get(ArcangelEntity.SHOOTING);

        if (!shooting) {
            killLight(e);
            return;
        }

        Boolean seen = boneSeenThisFrame.get(e.getId());
        float[] pos = lastBonePos.get(e.getId());

        if (seen == null || !seen || pos == null) {
            killLight(e);
            return;
        }

        float wx = pos[0], wy = pos[1], wz = pos[2];
        float brightness = Math.min(L_BRIGHT, OdysseyConfig.effectiveMaxLightBrightness());
        float radius = Math.min(L_RADIUS, OdysseyConfig.effectiveMaxPointRadius());
        VeilLightCompat.updateWithLifetime(e.getId(), wx, wy, wz, L_R, L_G, L_B, brightness, radius, 4);
    }

    private void killLight(ArcangelEntity e) {
        VeilLightCompat.remove(e.getId(), "arcangel-no-laser");
        lastBonePos.remove(e.getId());
        boneSeenThisFrame.remove(e.getId());
    }
}

