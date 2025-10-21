package net.kronoz.odyssey.entity.souls;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.sentry.SentryEntity;
import net.kronoz.odyssey.entity.sentry.SentryModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.specialty.DynamicGeoEntityRenderer;

public class LoveRenderer extends DynamicGeoEntityRenderer<LoveEntity> {
    public LoveRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new LoveModel());
        this.shadowRadius = 0.5f;
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

      
        boolean isEmissive = bone.getName().equals("1") ||

                bone.getName().equals("2");


        VertexConsumer vertexConsumer;
        if (isEmissive) {
            vertexConsumer = bufferSource.getBuffer(
                    RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/entity/love.png"))
            );
            packedLight = 15728880;
        } else {
            vertexConsumer = bufferSource.getBuffer(RenderLayer.getEntityCutout(getTextureLocation(this.getAnimatable())));
        }

        if (!bone.isHidden()) {
            poseStack.push();
            for (GeoCube cube : bone.getCubes()) {
                renderCube(
                        poseStack,
                        cube,
                        vertexConsumer,
                        packedLight,
                        OverlayTexture.DEFAULT_UV,
                        colour
                );
            }
            poseStack.pop();
        }


        return true;
    }

    @Override
    public Identifier getTextureLocation(LoveEntity e) {
        return ((LoveModel)this.getGeoModel()).getTextureResource(e);
    }


}
