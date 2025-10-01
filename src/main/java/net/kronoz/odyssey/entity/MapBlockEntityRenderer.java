package net.kronoz.odyssey.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MapBlockEntityRenderer implements BlockEntityRenderer<MapBlockEntity> {

    public MapBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public boolean rendersOutsideBoundingBox(MapBlockEntity be) {
        // Our map can be larger than a block (shader-driven), keep conservative
        return true;
    }

    @Override
    public void render(MapBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light, int overlay) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        BlockPos pos = be.getPos();
        double dx = pos.getX() - cam.x;
        double dy = pos.getY() - cam.y;
        double dz = pos.getZ() - cam.z;

        matrices.push();
        matrices.translate(dx, dy, dz);

        RenderLayer layer = OdysseyRenderTypes.mapHeightmap(false);
        VertexConsumer vc = buffers.getBuffer(layer);

        MatrixStack.Entry entry = matrices.peek();
        float y = 1.001f;

        vc.vertex(entry.getPositionMatrix(), 0f, y, 0f)
                .color(255, 255, 255, 255)
                .texture(0f, 0f)
                .overlay(overlay)
                .light(light)
                .normal(entry, 0, 1, 0);

        vc.vertex(entry.getPositionMatrix(), 1f, y, 0f)
                .color(255, 255, 255, 255)
                .texture(1f, 0f)
                .overlay(overlay)
                .light(light)
                .normal(entry, 0, 1, 0);

        vc.vertex(entry.getPositionMatrix(), 1f, y, 1f)
                .color(255, 255, 255, 255)
                .texture(1f, 1f)
                .overlay(overlay)
                .light(light)
                .normal(entry, 0, 1, 0);

        vc.vertex(entry.getPositionMatrix(), 0f, y, 1f)
                .color(255, 255, 255, 255)
                .texture(0f, 1f)
                .overlay(overlay)
                .light(light)
                .normal(entry, 0, 1, 0);

        matrices.pop();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

}
