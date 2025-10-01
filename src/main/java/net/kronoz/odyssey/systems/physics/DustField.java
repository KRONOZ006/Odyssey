package net.kronoz.odyssey.systems.physics;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

public final class DustField {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int OVERLAY     = OverlayTexture.DEFAULT_UV;

    private static final double LIFE_MIN = 20.0;
    private static final double LIFE_MAX = 40.5;
    private static final double SIZE_MIN = 0.02;
    private static final double SIZE_MAX = 0.04;

    private static final Vec3d  BASE_DIR   = new Vec3d(0.0, -1.0, 0.0);
    private static final double BASE_SPEED = 0.075;

    private static final double SPREAD_MAX = BASE_SPEED * 0.55;
    private static final double SPREAD_GAMMA = 3.0;
    private static final double SPEED_JITTER = 0.20;
    private static final double SPREAD_JITTER = 0.30;

    private static final double ROT_DRAG = 0.995;

    private final Node[] nodes;
    private final Random rng = new Random();

    private final float r, g, b;
    private final double emitPerSecond;
    private double emitAcc = 0.0;
    private int alive = 0;
    private int nextDead = 0;

    public DustField(int capacity, int r255, int g255, int b255, double emitRatePerSecond) {
        this.r = MathHelper.clamp(r255 / 255f, 0f, 1f);
        this.g = MathHelper.clamp(g255 / 255f, 0f, 1f);
        this.b = MathHelper.clamp(b255 / 255f, 0f, 1f);
        this.emitPerSecond = Math.max(0.0, emitRatePerSecond);
        this.nodes = new Node[capacity];
        for (int i = 0; i < capacity; i++) nodes[i] = Node.dead();
    }

    private static final class Node {
        boolean alive;
        Vec3d p;
        Vec3d radialDirXZ;
        double downSpeed;
        double radialMaxSpeed;
        float yaw, pitch, roll;
        float yawVel, pitchVel, rollVel;

        double age, life, size;

        static Node dead() {
            Node n = new Node();
            n.alive = false; n.p = Vec3d.ZERO;
            n.radialDirXZ = Vec3d.ZERO;
            n.downSpeed = 0; n.radialMaxSpeed = 0;
            n.yaw = n.pitch = n.roll = 0f;
            n.yawVel = n.pitchVel = n.rollVel = 0f;
            n.age = 0; n.life = 0; n.size = 0;
            return n;
        }
    }

    private void spawn(Node n, Vec3d origin) {
        Vec3d j = new Vec3d(
                (rng.nextDouble()-0.5)*0.35,
                (rng.nextDouble()-0.5)*0.35,
                (rng.nextDouble()-0.5)*0.35
        );
        n.p = origin.add(j);

        double ang = rng.nextDouble() * Math.PI * 2.0;
        n.radialDirXZ = new Vec3d(Math.cos(ang), 0.0, Math.sin(ang));

        double downMul   = 1.0 + (rng.nextDouble()*2.0 - 1.0) * SPEED_JITTER;
        double spreadMul = 1.0 + (rng.nextDouble()*2.0 - 1.0) * SPREAD_JITTER;
        n.downSpeed      = BASE_SPEED * downMul;
        n.radialMaxSpeed = SPREAD_MAX * spreadMul;

        n.life = LIFE_MIN + rng.nextDouble()*(LIFE_MAX - LIFE_MIN);
        n.age = 0.0;
        n.size = SIZE_MIN + rng.nextDouble()*(SIZE_MAX - SIZE_MIN);

        n.yaw   = (float)(rng.nextDouble()*Math.PI*2);
        n.pitch = (float)((rng.nextDouble()-0.5)*0.25);
        n.roll  = (float)((rng.nextDouble()-0.5)*0.25);
        n.yawVel   = (float)((rng.nextDouble()-0.5)*0.20);
        n.pitchVel = (float)((rng.nextDouble()-0.5)*0.20);
        n.rollVel  = (float)((rng.nextDouble()-0.5)*0.20);

        n.alive = true;
    }

    public void update(double dt, Vec3d origin, World world, long worldTime) {
        if (dt <= 0) return;

        if (alive < nodes.length && emitPerSecond > 0) {
            emitAcc += emitPerSecond * dt;
            while (emitAcc >= 1.0 && alive < nodes.length) {
                int start = nextDead;
                do {
                    Node n = nodes[nextDead];
                    if (!n.alive) {
                        spawn(n, origin);
                        alive++;
                        emitAcc -= 1.0;
                        nextDead = (nextDead + 1) % nodes.length;
                        break;
                    }
                    nextDead = (nextDead + 1) % nodes.length;
                } while (nextDead != start);
                if (start == nextDead) break;
            }
        }

        for (Node n : nodes) {
            if (!n.alive) continue;

            n.age += dt;
            if (n.age >= n.life) { n.alive = false; alive--; continue; }
            double t = MathHelper.clamp(n.age / n.life, 0.0, 1.0);
            double g = t*t*(3.0 - 2.0*t);
            g = Math.pow(g, SPREAD_GAMMA);

            Vec3d vDown   = BASE_DIR.multiply(n.downSpeed);
            Vec3d vLateral= n.radialDirXZ.multiply(n.radialMaxSpeed * g);
            Vec3d v       = vDown.add(vLateral);

            n.p = n.p.add(v.multiply(dt));

            n.yaw   += n.yawVel   * dt; n.yawVel   *= ROT_DRAG;
            n.pitch += n.pitchVel * dt; n.pitchVel *= ROT_DRAG;
            n.roll  += n.rollVel  * dt; n.rollVel  *= ROT_DRAG;
        }
    }

    public void render(MatrixStack ms, VertexConsumer vc, Vec3d cameraBase, float tickDelta, World world) {
        Matrix4f m4 = ms.peek().getPositionMatrix();

        for (Node n : nodes) {
            if (!n.alive) continue;

            double t = MathHelper.clamp(n.age / n.life, 0.0, 1.0);
            float fade = (float)(Math.min(1.0, t*3.0) * Math.pow(1.0 - t, 1.5));
            int lx = world.getLightLevel(net.minecraft.world.LightType.BLOCK, net.minecraft.util.math.BlockPos.ofFloored(n.p));
            int ls = world.getLightLevel(net.minecraft.world.LightType.SKY,   net.minecraft.util.math.BlockPos.ofFloored(n.p));
            float lfac = MathHelper.clamp((lx*0.8f + ls*0.5f)/30f, 0.08f, 1.0f);

            float a = MathHelper.clamp(0.06f + 0.22f * fade * lfac, 0f, 0.35f);
            float edge = (float)n.size;
            float hs = edge * 0.5f;

            float cx = (float)(n.p.x - cameraBase.x);
            float cy = (float)(n.p.y - cameraBase.y);
            float cz = (float)(n.p.z - cameraBase.z);

            float cyw = (float)Math.cos(n.yaw), syw = (float)Math.sin(n.yaw);
            float cpi = (float)Math.cos(n.pitch), spi = (float)Math.sin(n.pitch);
            float cro = (float)Math.cos(n.roll),  sro = (float)Math.sin(n.roll);

            Vector3f X = new Vector3f(1,0,0);
            Vector3f Y = new Vector3f(0,1,0);
            Vector3f Z = new Vector3f(0,0,1);

            X = new Vector3f(cyw*X.x - syw*X.z, X.y, syw*X.x + cyw*X.z);
            Y = new Vector3f(cyw*Y.x - syw*Y.z, Y.y, syw*Y.x + cyw*Y.z);
            Z = new Vector3f(cyw*Z.x - syw*Z.z, Z.y, syw*Z.x + cyw*Z.z);

            X = new Vector3f(X.x,  cpi*X.y - spi*X.z,  spi*X.y + cpi*X.z);
            Y = new Vector3f(Y.x,  cpi*Y.y - spi*Y.z,  spi*Y.y + cpi*Y.z);
            Z = new Vector3f(Z.x,  cpi*Z.y - spi*Z.z,  spi*Z.y + cpi*Z.z);

            X = new Vector3f( cro*X.x - sro*X.y, sro*X.x + cro*X.y, X.z);
            Y = new Vector3f( cro*Y.x - sro*Y.y, sro*Y.x + cro*Y.y, Y.z);
            Z = new Vector3f( cro*Z.x - sro*Z.y, sro*Z.x + cro*Z.y, Z.z);

            Vector3f RX = new Vector3f(X).mul(hs);
            Vector3f RY = new Vector3f(Y).mul(hs);
            Vector3f RZ = new Vector3f(Z).mul(hs);

            float rr = r, gg = g, bb = b;

            Vector3f C = new Vector3f(cx,cy,cz);
            Vector3f p000 = new Vector3f(C).sub(RX).sub(RY).sub(RZ);
            Vector3f p100 = new Vector3f(C).add(RX).sub(RY).sub(RZ);
            Vector3f p110 = new Vector3f(C).add(RX).add(RY).sub(RZ);
            Vector3f p010 = new Vector3f(C).sub(RX).add(RY).sub(RZ);
            Vector3f p001 = new Vector3f(C).sub(RX).sub(RY).add(RZ);
            Vector3f p101 = new Vector3f(C).add(RX).sub(RY).add(RZ);
            Vector3f p111 = new Vector3f(C).add(RX).add(RY).add(RZ);
            Vector3f p011 = new Vector3f(C).sub(RX).add(RY).add(RZ);

            putQuad(m4, ms, vc, rr,gg,bb,a, 0,1,1,0, p100,p101,p111,p110, X);
            putQuad(m4, ms, vc, rr,gg,bb,a, 0,1,1,0, p000,p010,p011,p001, new Vector3f(X).negate());
            putQuad(m4, ms, vc, rr,gg,bb,a, 0,1,1,0, p010,p110,p111,p011, Y);
            putQuad(m4, ms, vc, rr,gg,bb,a, 0,1,1,0, p000,p001,p101,p100, new Vector3f(Y).negate());
            putQuad(m4, ms, vc, rr,gg,bb,a, 0,1,1,0, p101,p001,p011,p111, Z);
            putQuad(m4, ms, vc, rr,gg,bb,a, 0,1,1,0, p100,p110,p010,p000, new Vector3f(Z).negate());
        }
    }

    private void putQuad(Matrix4f m4, MatrixStack ms, VertexConsumer vc,
                         float r, float g, float b, float a,
                         float u0, float v1, float u1, float v0,
                         Vector3f P0, Vector3f P1, Vector3f P2, Vector3f P3, Vector3f N) {
        int nx = (int)Math.signum(N.x()); int ny = (int)Math.signum(N.y()); int nz = (int)Math.signum(N.z());
        vc.vertex(m4, P0.x, P0.y, P0.z).color(r,g,b,a).texture(u0, v1).overlay(OVERLAY).light(FULL_BRIGHT).normal(ms.peek(), nx, ny, nz);
        vc.vertex(m4, P1.x, P1.y, P1.z).color(r,g,b,a).texture(u1, v1).overlay(OVERLAY).light(FULL_BRIGHT).normal(ms.peek(), nx, ny, nz);
        vc.vertex(m4, P2.x, P2.y, P2.z).color(r,g,b,a).texture(u1, v0).overlay(OVERLAY).light(FULL_BRIGHT).normal(ms.peek(), nx, ny, nz);
        vc.vertex(m4, P3.x, P3.y, P3.z).color(r,g,b,a).texture(u0, v0).overlay(OVERLAY).light(FULL_BRIGHT).normal(ms.peek(), nx, ny, nz);
    }
}
