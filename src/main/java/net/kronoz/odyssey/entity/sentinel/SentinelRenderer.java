package net.kronoz.odyssey.entity.sentinel;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SentinelRenderer extends GeoEntityRenderer<SentinelEntity> {
    public SentinelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SentinelModel());
    }

    @Override
    public RenderLayer getRenderType(SentinelEntity animatable, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
