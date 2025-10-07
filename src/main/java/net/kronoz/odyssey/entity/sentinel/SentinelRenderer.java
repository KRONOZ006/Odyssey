package net.kronoz.odyssey.entity.sentinel;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.specialty.DynamicGeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class SentinelRenderer extends DynamicGeoEntityRenderer<SentinelEntity> {
    public SentinelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SentinelModel());
    }


    @Override
    protected boolean boneRenderOverride(MatrixStack poseStack, GeoBone bone, VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int colour) {

        if (bone.getName().equals("eye")) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/entity/sentinel.png")));

            if (bone.isHidden())
                return false;

            for (GeoCube cube : bone.getCubes()) {
                poseStack.push();
                renderCube(poseStack, cube, vertexConsumer, 15728640, OverlayTexture.DEFAULT_UV, Color.WHITE.getColor());
                poseStack.pop();
            }
            return true;
        }
        return false;
    }




    @Override
    public RenderLayer getRenderType(SentinelEntity animatable, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }




}
