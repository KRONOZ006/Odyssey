package net.kronoz.odyssey.entity.apostasy;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.OverlayTexture;
import software.bernie.geckolib.renderer.specialty.DynamicGeoEntityRenderer;

import java.awt.Color;

public class ApostasyRenderer extends DynamicGeoEntityRenderer<ApostasyEntity> {

    public ApostasyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ApostasyModel());
        this.shadowRadius = 0.6f;
    }

    @Override
    public Identifier getTextureLocation(ApostasyEntity animatable) {
        return Identifier.of(Odyssey.MODID, "textures/entity/apostasy.png");
    }

    @Override
    protected boolean boneRenderOverride(
            MatrixStack poseStack,
            GeoBone bone,
            VertexConsumerProvider bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        String name = bone.getName();

        if ("eye".equals(name) || name.startsWith("glow")) {
            VertexConsumer glowBuffer = bufferSource.getBuffer(
                    RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/entity/apostasy.png"))
            );

            if (bone.isHidden())
                return true;

            for (GeoCube cube : bone.getCubes()) {
                poseStack.push();
                renderCube(poseStack, cube, glowBuffer,
                        15728640,
                        OverlayTexture.DEFAULT_UV,
                        Color.WHITE.getRGB());
                poseStack.pop();
            }
            return true;
        }

        return false;
    }
}
