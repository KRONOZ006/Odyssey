package net.kronoz.odyssey.entity.projectile;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class LaserProjectileRenderer extends EntityRenderer<LaserProjectileEntity> {
    private static final Identifier TEX = Identifier.of("minecraft","textures/misc/white.png");
    public LaserProjectileRenderer(EntityRendererFactory.Context ctx) { super(ctx); }
    @Override public Identifier getTexture(LaserProjectileEntity e) { return TEX; }

    @Override
    public void render(LaserProjectileEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEyes(getTexture(e)));

        Vec3d v = e.getVelocity();
        float speed = (float)v.length();
        float len = Math.max(0.6f, speed * 0.9f);

        float pulse = 0.10f + 0.02f*(float)Math.sin((e.age + tickDelta) * 0.6);
        float w = pulse;

        // get camera orientation
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        float camYaw = cam.getYaw() * ((float)Math.PI / 180f);
        float camPitch = cam.getPitch() * ((float)Math.PI / 180f);

        m.push();
        // rotate to always face camera yaw/pitch
        m.multiply(RotationAxis.POSITIVE_Y.rotation(-camYaw));
        m.multiply(RotationAxis.POSITIVE_X.rotation(camPitch));
        // stretch along forward (Z)
        m.translate(0.0, 0.0, len * 0.5f);
        m.scale(w, w, len);

        MatrixStack.Entry entry = m.peek();
        Matrix4f mat = entry.getPositionMatrix();
        putBeamCube(vc, entry, mat, 1f, 0.15f, 0.15f, 0.9f);

        m.pop();
        super.render(e, yaw, tickDelta, m, buffers, light);
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f m, float x,float y,float z,float r,float g,float b,float a){
        vc.vertex(m,x,y,z)
                .color(r,g,b,a)
                .texture(0,0)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0x00F000F0)
                .normal(entry,0,1,0);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f m,
                             float x1,float y1,float z1, float x2,float y2,float z2,
                             float x3,float y3,float z3, float x4,float y4,float z4,
                             float r,float g,float b,float a) {
        v(vc,entry,m,x1,y1,z1,r,g,b,a);
        v(vc,entry,m,x2,y2,z2,r,g,b,a);
        v(vc,entry,m,x3,y3,z3,r,g,b,a);
        v(vc,entry,m,x4,y4,z4,r,g,b,a);
    }

    private static void putBeamCube(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f m, float r,float g,float b,float a){
        float x0=-0.5f, x1=0.5f, y0=-0.5f, y1=0.5f, z0=-0.5f, z1=0.5f;
        quad(vc,entry,m, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b,a);
        quad(vc,entry,m, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, r,g,b,a);
        quad(vc,entry,m, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, r,g,b,a);
        quad(vc,entry,m, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,b,a);
        quad(vc,entry,m, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b,a);
        quad(vc,entry,m, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, r,g,b,a);
    }
}
