package net.kronoz.odyssey.entity;

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

public class ShockwaveRenderer extends EntityRenderer<ShockwaveEntity> {
    private static final Identifier[] FRAMES = new Identifier[12];
    static {
        for (int i = 0; i < FRAMES.length; i++) {
            FRAMES[i] = Identifier.of("odyssey","textures/effects/energy_shockwave_" + i + ".png");
        }
    }
    private static final int TICKS_PER_FRAME = 2;

    public ShockwaveRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(ShockwaveEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        float t = e.getProgress(tickDelta);
        if (t >= 1f || e.isRemoved()) return;

        float r = Math.max(0.25f, e.getCurrentRadius(tickDelta));
        float alpha = 1.0f - t;

        int frame = ((int)((e.age + tickDelta) / TICKS_PER_FRAME)) % FRAMES.length;
        Identifier tex = FRAMES[frame];

        matrices.push();
        matrices.translate(0, e.getYOffset(), 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();

        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityTranslucent(tex));
        float u0=0f,v0=0f,u1=1f,v1=1f;

        vc.vertex(pose, -r, -r, 0).color(1f,1f,1f,alpha).texture(u0,v0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry,0,0,1);
        vc.vertex(pose, -r,  r, 0).color(1f,1f,1f,alpha).texture(u0,v1).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry,0,0,1);
        vc.vertex(pose,  r,  r, 0).color(1f,1f,1f,alpha).texture(u1,v1).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry,0,0,1);
        vc.vertex(pose,  r, -r, 0).color(1f,1f,1f,alpha).texture(u1,v0).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry,0,0,1);

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public Identifier getTexture(ShockwaveEntity entity) { return FRAMES[0]; }
}
