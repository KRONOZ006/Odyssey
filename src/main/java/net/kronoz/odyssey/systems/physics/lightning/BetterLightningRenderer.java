package net.kronoz.odyssey.systems.physics.lightning;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LightningEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

public class BetterLightningRenderer {

    private static final RenderLayer LAYER = RenderLayer.getLightning();

    public static void render(LightningEntity bolt, MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        final VertexConsumer vc = consumers.getBuffer(LAYER);
        final Random rng = new Random(bolt.getId() * 1103515245L);

        Vec3d[] spine = BoltBuilder.buildMainPathLocalFractal(100.0 + rng.nextDouble() * 12.0, 8, 1.0, rng);
        BoltBuilder.Branch[] branches = BoltBuilder.buildBranchesLocal(spine, 50 + rng.nextInt(10), rng);

        CameraBasis cam = cameraBasis();

        matrices.push();
        RenderSystem.disableCull();

        float baseThickness = 0.1f;
        drawStrip(vc, matrices, spine, baseThickness, cam, 1.0f, 1.0f, 1.0f, 1.0f);

        float branchThickness = baseThickness * 0.25f;
        for (BoltBuilder.Branch br : branches) {
            drawStrip(vc, matrices, br.points(), branchThickness, cam, 10.0f, 10.0f, 10.0f, 10.0f);
        }

        RenderSystem.enableCull();
        matrices.pop();
    }

    private static void drawStrip(VertexConsumer vc, MatrixStack m, Vec3d[] pts, float radius,
                                  CameraBasis cam, float cr, float cg, float cb, float ca) {
        if (pts == null || pts.length < 2) return;
        var entry = m.peek();

        Vec3d[] widths = new Vec3d[pts.length];

        Vec3d prevW = null;
        for (int i = 0; i < pts.length; i++) {
            Vec3d pPrev = (i > 0) ? pts[i - 1] : pts[i];
            Vec3d pCurr = pts[i];
            Vec3d pNext = (i < pts.length - 1) ? pts[i + 1] : pts[i];

            Vec3d tangent = pNext.subtract(pPrev);
            if (tangent.lengthSquared() < 1e-8) tangent = new Vec3d(1, 1, 1);

            Vector3f r = new Vector3f(cam.right());
            float dot = (float)(r.x * tangent.x + r.y * tangent.y + r.z * tangent.z);
            Vector3f w = new Vector3f(
                    r.x - dot * (float) tangent.x,
                    r.y - dot * (float) tangent.y,
                    r.z - dot * (float) tangent.z
            );

            float len = w.length();
            if (len < 1e-4f) {
                Vector3f u = new Vector3f(cam.up());
                dot = (float)(u.x * tangent.x + u.y * tangent.y + u.z * tangent.z);
                w.set(
                        u.x - dot * (float) tangent.x,
                        u.y - dot * (float) tangent.y,
                        u.z - dot * (float) tangent.z
                );
                len = w.length();
                if (len < 1e-4f) {
                    if (prevW != null) {
                        widths[i] = prevW;
                        continue;
                    }
                    w.set(1, 1, 1);
                    len = 1f;
                }
            }
            w.mul(radius / len);
            Vec3d ww = new Vec3d(w.x, w.y, w.z);

            if (prevW != null) {
                ww = new Vec3d(
                        (prevW.x + ww.x) * 0.75,
                        (prevW.y + ww.y) * 0.75,
                        (prevW.z + ww.z) * 0.75
                );
            }
            widths[i] = ww;
            prevW = ww;
        }

        for (int i = 0; i < pts.length - 1; i++) {
            Vec3d p0 = pts[i];
            Vec3d p1 = pts[i + 1];

            Vec3d dir = p1.subtract(p0);
            double dl = dir.length();
            if (dl > 1e-6) {
                dir = dir.multiply(1.0 / dl);
                double eps = Math.min(0.2, dl * 1.5);
                p0 = p0.subtract(dir.multiply(eps));
                p1 = p1.add(dir.multiply(eps));
            }

            Vec3d w0 = widths[i + 1];
            Vec3d w1 = widths[i + 1];

            put(vc, entry, p0.add(w0), cr, cg, cb, ca);
            put(vc, entry, p0.subtract(w0), cr, cg, cb, ca);
            put(vc, entry, p1.subtract(w1), cr, cg, cb, ca);

            put(vc, entry, p0.add(w0), cr, cg, cb, ca);
            put(vc, entry, p1.subtract(w1), cr, cg, cb, ca);
            put(vc, entry, p1.add(w1), cr, cg, cb, ca);
        }
    }

    private static void put(VertexConsumer vc, MatrixStack.Entry entry, Vec3d p,
                            float r, float g, float b, float a) {
        vc.vertex(entry.getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
                .color(255, 255, 255, (int)(a * 255))
                .texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0x00F000F0)
                .normal(entry, 0, 1, 0);
    }


    // ====== camera basis ======
    private static CameraBasis cameraBasis() {
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        Quaternionf q = new Quaternionf(cam.getRotation());
        Vector3f right = new Vector3f(1, 0, 0); q.transform(right);
        Vector3f up    = new Vector3f(0, 1, 0); q.transform(up);
        Vector3f fwd   = new Vector3f(0, 0, 1); q.transform(fwd);
        return new CameraBasis(right, up, fwd);
    }
    private record CameraBasis(Vector3f right, Vector3f up, Vector3f fwd) {}
}
