package net.kronoz.odyssey.entity.thrasher;

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
import software.bernie.geckolib.renderer.specialty.DynamicGeoEntityRenderer;

public class ThrasherRenderer extends DynamicGeoEntityRenderer<ThrasherEntity> {
    public ThrasherRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ThrasherModel());
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

      
        boolean isEmissive =
                bone.getName().equals("rightaxeb") ||
                bone.getName().equals("leftaxeb") ||
                bone.getName().equals("rightsawpart") ||
                bone.getName().equals("leftsawpart") ||
                bone.getName().equals("righttopbladeb") ||
                bone.getName().equals("lefttopbladeb") ||
                bone.getName().equals("rightbottombladeb") ||
                bone.getName().equals("leftbottombladeb");


        VertexConsumer vertexConsumer;
        if (isEmissive) {
            vertexConsumer = bufferSource.getBuffer(
                    RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/entity/thrasher.png"))
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
    public Identifier getTextureLocation(ThrasherEntity e) {
        return ((ThrasherModel)this.getGeoModel()).getTextureResource(e);
    }


}
