package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.block.custom.StasisPodBlock;
import net.kronoz.odyssey.entity.StasisPodBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class StasisPodBERenderer extends GeoBlockRenderer<StasisPodBlockEntity> {
    public StasisPodBERenderer(BlockEntityRendererFactory.Context ctx) { super(new StasisPodGeoModel()); }

    @Override
    public void render(StasisPodBlockEntity be, float dt, MatrixStack ms, VertexConsumerProvider buf, int light, int overlay) {
        if (be.getWorld() == null) return;
        var s = be.getCachedState();
        if (!s.contains(StasisPodBlock.PART) || s.get(StasisPodBlock.PART) != 4) return;
        Direction f = s.get(StasisPodBlock.FACING);
        ms.push();
        float yaw = switch (f) { case NORTH -> 0f; case EAST -> 90f; case SOUTH -> 180f; case WEST -> 270f; default -> 0f; };
        ms.translate(0.5, 0, 0.5);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        ms.translate(-0.5, 0, -0.5);
        super.render(be, dt, ms, buf, light, overlay);
        ms.pop();
    }
}
