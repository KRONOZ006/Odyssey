package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.systems.cinematics.runtime.CutsceneManager;
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
    private static final Identifier TEX = Identifier.of("odyssey","textures/effect/shockwave_anim.png");

    private static final int FRAME_COLS = 4;
    private static final int FRAME_ROWS = 2;
    private static final int TOTAL_FRAMES = FRAME_COLS * FRAME_ROWS;
    private static final int TICKS_PER_FRAME = 2; // speed

    public ShockwaveRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(ShockwaveEntity e, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        float t = e.getProgress(tickDelta);
        if (e.isRemoved()) return;

        float r = Math.max(0.25f, e.getCurrentRadius(tickDelta));
        float alpha = Math.max(0.15f, 1.0f - t); // never fully invisible

        int frame = (int)Math.floor((e.age + tickDelta) / TICKS_PER_FRAME) % TOTAL_FRAMES;
        int col = frame % FRAME_COLS;
        int row = frame / FRAME_COLS;

        float du = 1f / FRAME_COLS;
        float dv = 1f / FRAME_ROWS;
        float u0 = col * du, u1 = u0 + du;
        float v0 = row * dv, v1 = v0 + dv;

        matrices.push();
        matrices.translate(0, (e.getPlaneY() - e.getY()) + 0.03, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();

        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        int fullbright = 0x00F000F0;

        vc.vertex(pose, -r, -r, 0).color(1f,1f,1f,alpha).texture(u0,v0).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose, -r,  r, 0).color(1f,1f,1f,alpha).texture(u0,v1).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose,  r,  r, 0).color(1f,1f,1f,alpha).texture(u1,v1).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);
        vc.vertex(pose,  r, -r, 0).color(1f,1f,1f,alpha).texture(u1,v0).overlay(OverlayTexture.DEFAULT_UV).light(fullbright).normal(entry,0,0,1);

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public Identifier getTexture(ShockwaveEntity entity) { return TEX; }
}
