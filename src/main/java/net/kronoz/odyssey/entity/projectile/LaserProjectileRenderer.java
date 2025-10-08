package net.kronoz.odyssey.entity.projectile;

import net.kronoz.odyssey.entity.projectile.LaserProjectileEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class LaserProjectileRenderer extends EntityRenderer<LaserProjectileEntity> {
    private static final RenderLayer LAYER = RenderLayer.getLightning();

    public LaserProjectileRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(LaserProjectileEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        matrices.push();

        Vec3d v = e.getVelocity();
        double sp = v.length();
        if (sp > 1e-4) {
            float ry = (float)(Math.atan2(v.z, v.x) + Math.toRadians(90));
            float rx = (float)(-Math.atan2(v.y, Math.sqrt(v.x*v.x + v.z*v.z)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(ry));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(rx));
        }

        float half = 0.05f;
        float len = (float)Math.min(0.25 + sp * 0.12, 0.45); // un poil “étiré” avec la vitesse
        matrices.translate(0, 0, 0);

        VertexConsumer vc = buffers.getBuffer(LAYER);
        float r = 1f, g = 0.1f, b = 0.1f, a = 1.0f;

        float x0=-half, x1=half, y0=-half, y1=half, z0=-half, z1=half;

        putQuad(vc, matrices, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b,a); // front
        putQuad(vc, matrices, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, r,g,b,a); // back
        putQuad(vc, matrices, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, r,g,b,a); // top
        putQuad(vc, matrices, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1, r,g,b,a); // bottom
        putQuad(vc, matrices, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, r,g,b,a); // right
        putQuad(vc, matrices, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b,a); // left

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public Identifier getTexture(LaserProjectileEntity entity) { return null; }

    private static void putQuad(VertexConsumer vc, MatrixStack m,
                                float x0,float y0,float z0, float x1,float y1,float z1,
                                float x2,float y2,float z2, float x3,float y3,float z3,
                                float r,float g,float b,float a) {
        var entry = m.peek();
        vc.vertex(entry.getPositionMatrix(), x0,y0,z0).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0,1,0);
        vc.vertex(entry.getPositionMatrix(), x1,y1,z1).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0,1,0);
        vc.vertex(entry.getPositionMatrix(), x2,y2,z2).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0,1,0);
        vc.vertex(entry.getPositionMatrix(), x3,y3,z3).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0,1,0);
        vc.vertex(entry.getPositionMatrix(), x0,y0,z0).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0,1,0);
        vc.vertex(entry.getPositionMatrix(), x2,y2,z2).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0,1,0);
    }
}
