package net.kronoz.odyssey.entity;

import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

public class DebrisBlockRenderer extends EntityRenderer<DebrisBlockEntity> {
    public DebrisBlockRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(DebrisBlockEntity e, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int packedLight) {
        var state = e.getBlockStateRender();
        if (state.getRenderType() != BlockRenderType.MODEL) return;

        float rx = e.getRollX();
        float ry = e.getRollY();
        float rz = e.getRollZ();

        matrices.push();
        matrices.translate(-0.5, 0.0, -0.5); // center the cube
        matrices.translate(0.5, 0.5, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
        matrices.translate(-0.5, -0.5, -0.5);

        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        brm.getModelRenderer().render(
                e.getWorld(), brm.getModel(state), state, BlockPos.ofFloored(e.getX(), e.getY(), e.getZ()),
                matrices, buffers.getBuffer(RenderLayer.getCutout()), false,
                e.getWorld().random, 0L, 0
        );

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, buffers, packedLight);
    }

    @Override
    public Identifier getTexture(DebrisBlockEntity entity) { return null; }
}
