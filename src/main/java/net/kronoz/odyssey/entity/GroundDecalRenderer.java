package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.block.custom.DecalLightClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

public class GroundDecalRenderer extends EntityRenderer<GroundDecalEntity> {
    private static final Identifier TEX = Identifier.of("odyssey", "textures/entity/ground_marker.png");

    public GroundDecalRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(GroundDecalEntity e, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int packedLight) {
        int duration = e.getDuration();
        float t = (e.age + tickDelta) / Math.max(1f, duration);
        if (t >= 1f) { DecalLightClient.remove(e.getId()); return; }

        float alpha = 1.0f - t;
        float scale = 1.0f + t;
        float angle = 360f * t;
        float r = e.getRadius() * scale;

        matrices.push();
        matrices.translate(0, 0.02, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        matrices.scale(scale, scale, 1f);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();

        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        float u0=0f,v0=0f,u1=1f,v1=1f;

        vc.vertex(pose, -r, -r, 0).color(1f,1f,1f,alpha).texture(u0,v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry, 0,0,1);
        vc.vertex(pose, -r,  r, 0).color(1f,1f,1f,alpha).texture(u0,v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry, 0,0,1);
        vc.vertex(pose,  r,  r, 0).color(1f,1f,1f,alpha).texture(u1,v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry, 0,0,1);
        vc.vertex(pose,  r, -r, 0).color(1f,1f,1f,alpha).texture(u1,v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry, 0,0,1);

        matrices.pop();

        float Lr = 1.0f, Lg = 0.0f, Lb = 0.0f;
        float brightness = 1.2f * (1.0f - t);
        float radius = 4.0f + 6.0f * t;
        DecalLightClient.update(e.getId(), e.getX(), e.getY() + 0.2, e.getZ(), Lr, Lg, Lb, brightness, radius);

        super.render(e, yaw, tickDelta, matrices, buffers, packedLight);
    }

    @Override
    public Identifier getTexture(GroundDecalEntity entity) { return TEX; }
}
