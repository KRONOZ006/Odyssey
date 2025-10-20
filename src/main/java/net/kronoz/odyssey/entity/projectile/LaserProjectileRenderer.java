package net.kronoz.odyssey.entity.projectile;

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

public class LaserProjectileRenderer extends EntityRenderer<LaserProjectileEntity> {
    private static final Identifier TEX = Identifier.of("odyssey","textures/effects/laser_beam.png");

    public LaserProjectileRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(LaserProjectileEntity e, float yaw, float tickDelta, MatrixStack ms,
                       VertexConsumerProvider buffers, int light) {
        ms.push();

        // place at entity, rotate to entity yaw/pitch (set from velocity)
        ms.translate(0, 0, 0);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(e.getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(e.getPitch()));

        // beam dimensions
        float len = 0.9f;     // along +Z after rotation
        float rad = 0.07f;

        MatrixStack.Entry entry = ms.peek();
        Matrix4f pose = entry.getPositionMatrix();
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        int fullbright = 0x00F000F0;
        float a = 1f;

        // simple rectangular prism (two quads) along Z
        // top/bottom not needed; two side quads are enough for a slim beam

        // quad 1 (X axis)
        vc.vertex(pose, -rad, -rad, 0).color(1,1,1,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose, -rad, -rad, len).color(1,1,1,a).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose,  rad,  rad, len).color(1,1,1,a).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose,  rad,  rad, 0).color(1,1,1,a).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);

        // quad 2 (Y axis)
        vc.vertex(pose,  rad, -rad, 0).color(1,1,1,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose,  rad, -rad, len).color(1,1,1,a).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose, -rad,  rad, len).color(1,1,1,a).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose, -rad,  rad, 0).color(1,1,1,a).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);

        ms.pop();
        super.render(e, yaw, tickDelta, ms, buffers, light);
    }

    @Override
    public Identifier getTexture(LaserProjectileEntity entity) { return TEX; }
}
