package net.kronoz.odyssey.entity;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.kronoz.odyssey.entity.LiftPlatformEntity;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

public class ModEntityRenderers {
    public static void register() {
        EntityRendererRegistry.register(ModEntities.LIFT_PLATFORM, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.LIFT_PART_COLLIDER, EmptyEntityRenderer::new);
    }
}
