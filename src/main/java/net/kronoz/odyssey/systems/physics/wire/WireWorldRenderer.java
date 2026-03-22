package net.kronoz.odyssey.systems.physics.wire;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public final class WireWorldRenderer {
    public static void init(){
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MatrixStack ms = ctx.matrixStack();
            VertexConsumerProvider.Immediate buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            WireClientMirror.renderAll(ms, buffers, 0x00F000F0);
            WireToolState.renderPreview(ms, buffers);
            buffers.draw();
        });
    }
}
