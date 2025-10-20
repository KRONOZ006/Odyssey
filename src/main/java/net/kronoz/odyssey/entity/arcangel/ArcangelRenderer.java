package net.kronoz.odyssey.entity.arcangel;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.sentinel.SentinelEntity;
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

public class ArcangelRenderer extends DynamicGeoEntityRenderer<ArcangelEntity> {
    public ArcangelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ArcangelModel());
        this.shadowRadius = 0.7f;



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


        boolean isEmissive = bone.getName().equals("gem") ||
                bone.getName().equals("gemlight") ||
                bone.getName().equals("laserring1") ||
                bone.getName().equals("laserring2") ||
                bone.getName().equals("laserring3") ||
                bone.getName().equals("laserring4") ||
                bone.getName().equals("laserring5") ||
                bone.getName().equals("laserring6") ||
                bone.getName().equals("laserring7") ||
                bone.getName().equals("laserring8") ||
                bone.getName().equals("laserring9") ||
                bone.getName().equals("laserring10") ||
                bone.getName().equals("laserring11") ||
                bone.getName().equals("laserring12") ||
                bone.getName().equals("laserring13") ||
                bone.getName().equals("laserring14") ||
                bone.getName().equals("laserring15") ||
                bone.getName().equals("laserring16") ||
                bone.getName().equals("laserring17") ||
                bone.getName().equals("laserring18") ||

                bone.getName().equals("laser");


        VertexConsumer vertexConsumer;
        if (isEmissive) {
            vertexConsumer = bufferSource.getBuffer(
                    RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/entity/arcangel.png"))
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
    public Identifier getTextureLocation(ArcangelEntity entity) {
       return Identifier.of("odyssey","textures/entity/arcangel.png");
    }



    @Override
    public RenderLayer getRenderType(ArcangelEntity animatable, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }



}
