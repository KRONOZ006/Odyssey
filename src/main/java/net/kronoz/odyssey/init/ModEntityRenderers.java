package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

public class ModEntityRenderers {
    public static void register() {
        EntityRendererRegistry.register(ModEntities.LIFT_PLATFORM, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.LIFT_PART_COLLIDER, EmptyEntityRenderer::new);
    }
}
