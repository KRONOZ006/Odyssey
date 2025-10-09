package net.kronoz.odyssey.item.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.kronoz.odyssey.entity.GrappleHookEntity;
import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class GrappleHookRenderer extends EntityRenderer<GrappleHookEntity> {

    public static void register() {
        EntityRendererRegistry.register(ModEntities.GRAPPLE_HOOK, GrappleHookRenderer::new);
    }

    public GrappleHookRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(GrappleHookEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
    }

    @Override public Identifier getTexture(GrappleHookEntity entity) { return null; }
}
