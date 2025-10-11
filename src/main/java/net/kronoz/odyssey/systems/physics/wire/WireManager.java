package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WireManager {
    private WireManager(){}

    private static final Map<UUID, WireSim> SIMS = new HashMap<>();
    private static final Map<UUID, WireDef> DEFS = new HashMap<>();

    public static void ensure(UUID id, WireDef def, Vec3d a, Vec3d b){
        if (!SIMS.containsKey(id)) {
            SIMS.put(id, new WireSim(def, a, b, def.halfWidth));
            DEFS.put(id, def);
        }
    }

    public static WireSim get(UUID id){ return SIMS.get(id); }
    public static WireDef getDef(UUID id){ return DEFS.get(id); }
    public static void remove(UUID id){ SIMS.remove(id); DEFS.remove(id); }
    public static void clearAllClient(){ SIMS.clear(); DEFS.clear(); }

    public static void ensureFromRecordClient(WireRecord r){
        WireDef def = WireDef.defaultCable(r.defId);
        Vec3d a = WireToolMath.anchorCenter(r.a);
        Vec3d b = WireToolMath.anchorCenter(r.b);
        ensure(r.id, def, a, b);
        WireSim sim = get(r.id);
        if (sim != null) sim.setPinned(r.aPinned, r.bPinned);
    }

    public static void stepAndRender(UUID id,
                                     Vec3d a, boolean aPinned,
                                     Vec3d b, boolean bPinned,
                                     MatrixStack matrices,
                                     VertexConsumerProvider consumers,
                                     int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        WireSim sim = SIMS.get(id);
        WireDef def = DEFS.get(id);
        if (sim == null || def == null) return;

        sim.setPinned(aPinned, bPinned);
        sim.step(mc.world, a, b);

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        MatrixStack ms = new MatrixStack();
        ms.translate(-cam.x, -cam.y, -cam.z);
        MatrixStack.Entry entry = ms.peek();
        Matrix4f pm = entry.getPositionMatrix();

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(def.texture));
        renderTubeQuadPanels(def, sim, vc, pm, entry, light, overlay);
    }

    private static void renderTubeQuadPanels(WireDef def, WireSim sim,
                                             VertexConsumer vc, Matrix4f pm, MatrixStack.Entry entry,
                                             int light, int overlay) {
        WireSim.Node[] ns = sim.nodes();
        if (ns.length < 2) return;

        final int sides = Math.max(3, def.tubeSides);
        final float r   = def.halfWidth;
        final int LIGHT = 0x00F000F0;

        Vec3d[] T = new Vec3d[ns.length];
        for (int i=0;i<ns.length;i++){
            Vec3d t = (i==0)? ns[1].p.subtract(ns[0].p)
                    : (i==ns.length-1)? ns[i].p.subtract(ns[i-1].p)
                    : ns[i+1].p.subtract(ns[i-1].p);
            double L=t.length();
            T[i] = (L<1e-9)? new Vec3d(0,1,0) : t.multiply(1.0/L);
        }

        Vec3d[] N = new Vec3d[ns.length];
        Vec3d[] B = new Vec3d[ns.length];
        {
            Vec3d t0 = T[0];
            Vec3d helper = Math.abs(t0.y) > 0.92 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
            Vec3d n0 = helper.crossProduct(t0);
            double L = n0.length(); n0 = (L<1e-9)? new Vec3d(0,0,1) : n0.multiply(1.0/L);
            Vec3d b0 = t0.crossProduct(n0);
            N[0]=n0; B[0]=b0;
            for (int i=1;i<ns.length;i++){
                Vec3d ti=T[i];
                double dot = N[i-1].dotProduct(ti);
                Vec3d nProj = N[i-1].subtract(ti.multiply(dot));
                double Ln=nProj.length();
                Vec3d ni = (Ln<1e-9)? N[i-1] : nProj.multiply(1.0/Ln);
                Vec3d bi = ti.crossProduct(ni);
                N[i]=ni; B[i]=bi;
            }
        }

        Vec3d[][] P = new Vec3d[ns.length][sides];
        for (int i=0;i<ns.length;i++){
            for (int k=0;k<sides;k++){
                double a = (2*Math.PI * k) / sides;
                double ca=Math.cos(a), sa=Math.sin(a);
                Vec3d n = N[i].multiply(ca).add(B[i].multiply(sa));
                P[i][k] = ns[i].p.add(n.multiply(r));
            }
        }

        float invU = 1f / Math.max(1, ns.length-1);
        float invV = 1f / sides;

        for (int i=0;i<ns.length-1;i++){
            float u0 = i * invU, u1 = (i+1) * invU;
            for (int k=0;k<sides;k++){
                int k1 = (k+1) % sides;

                Vec3d a = P[i][k];
                Vec3d b = P[i][k1];
                Vec3d c = P[i+1][k1];
                Vec3d d = P[i+1][k];

                Vec3d e1 = b.subtract(a);
                Vec3d e2 = d.subtract(a);
                Vec3d n  = e1.crossProduct(e2);
                double Ln = n.length();
                if (Ln < 1e-9) n = new Vec3d(0,1,0);
                else n = n.multiply(1.0/Ln);

                float v0 = k * invV, v1 = (k+1) * invV;

                put(vc, pm, entry, a, u0, v0, n, overlay, LIGHT);
                put(vc, pm, entry, b, u0, v1, n, overlay, LIGHT);
                put(vc, pm, entry, c, u1, v1, n, overlay, LIGHT);

                put(vc, pm, entry, a, u0, v0, n, overlay, LIGHT);
                put(vc, pm, entry, c, u1, v1, n, overlay, LIGHT);
                put(vc, pm, entry, d, u1, v0, n, overlay, LIGHT);

                put(vc, pm, entry, a, u0, v0, n, overlay, LIGHT);
                put(vc, pm, entry, b, u0, v1, n, overlay, LIGHT);
                put(vc, pm, entry, d, u1, v0, n, overlay, LIGHT);

                put(vc, pm, entry, b, u0, v1, n, overlay, LIGHT);
                put(vc, pm, entry, c, u1, v1, n, overlay, LIGHT);
                put(vc, pm, entry, d, u1, v0, n, overlay, LIGHT);
            }
        }
    }

    private static void put(VertexConsumer vc, Matrix4f pm, MatrixStack.Entry entry,
                            Vec3d p, float u, float v, Vec3d n, int overlay, int light){
        vc.vertex(pm,(float)p.x,(float)p.y,(float)p.z);
        vc.color(1f,1f,1f,1f);
        vc.texture(u,v);
        vc.overlay(overlay);
        vc.light(light);
        vc.normal(entry,(float)n.x,(float)n.y,(float)n.z);
    }
}