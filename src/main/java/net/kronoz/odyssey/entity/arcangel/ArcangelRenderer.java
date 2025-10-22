package net.kronoz.odyssey.entity.arcangel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.kronoz.odyssey.Odyssey;
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

    private final Int2ObjectMap<LightRenderHandle<PointLightData>> handles = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<PointLightData> datas = new Int2ObjectOpenHashMap<>();

    private final Int2ObjectMap<float[]> lastBonePos = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Boolean> boneSeenThisFrame = new Int2ObjectOpenHashMap<>();

    private static final float L_R = 1.0f, L_G = 0.0f, L_B = 0.0f; // red
    private static final float L_BRIGHT = 20.2f;
    private static final float L_RADIUS = 70.5f;

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
        var sys = VeilRenderSystem.renderer();
        boolean shooting = e.getDataTracker().get(ArcangelEntity.SHOOTING);

        if (sys == null || sys.getLightRenderer() == null || !shooting) {
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

        LightRenderHandle<PointLightData> h = handles.get(e.getId());
        PointLightData d = datas.get(e.getId());

        if (h == null || d == null || !h.isValid()) {
            d = new PointLightData()
                    .setBrightness(L_BRIGHT)
                    .setColor(L_R, L_G, L_B)
                    .setRadius(L_RADIUS);
            d.setPosition(wx, wy, wz);
            h = sys.getLightRenderer().addLight(d);
            handles.put(e.getId(), h);
            datas.put(e.getId(), d);
        } else {
            d.setBrightness(L_BRIGHT);
            d.setColor(L_R, L_G, L_B);
            d.setRadius(L_RADIUS);
            d.setPosition(wx, wy, wz);
            h.markDirty();
        }
    }

    private void killLight(ArcangelEntity e) {
        LightRenderHandle<PointLightData> h = handles.remove(e.getId());
        if (h != null && h.isValid()) h.close();
        datas.remove(e.getId());
        lastBonePos.remove(e.getId());
        boneSeenThisFrame.remove(e.getId());
    }
}
