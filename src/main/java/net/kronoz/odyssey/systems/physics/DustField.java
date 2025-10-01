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
//if you wanna know how this works just GIT GUD
public final class DustField {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int OVERLAY     = OverlayTexture.DEFAULT_UV;

    private static final double DRAG         = 0.985;
    private static final double ROT_DRAG     = 0.995;
    private static final double CURL_EPS     = 0.25;
    private static final double NOISE_FORCE  = 0.12;
    private static final double TWINKLE      = 0.03;

    private static final double LIFE_MIN = 3.0;
    private static final double LIFE_MAX = 7.5;
    private static final double SIZE_MIN = 0.02;
    private static final double SIZE_MAX = 0.045;

    private static final Vec3d BASE_DIR = new Vec3d(0.55, -1.0, 0.35).normalize();
    private static final double BASE_SPEED = 0.035;

    private final Node[] nodes;
    private final Random rng = new Random(0xD17D5A11L);

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
        Vec3d p, v;
        float yaw, pitch, roll;
        float yawVel, pitchVel, rollVel;
        double age, life, size;

        static Node dead() {
            Node n = new Node();
            n.alive = false; n.p = Vec3d.ZERO; n.v = Vec3d.ZERO;
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
        Vec3d base = BASE_DIR.multiply(BASE_SPEED * (0.85 + rng.nextDouble()*0.3));
        Vec3d micro = new Vec3d(
                (rng.nextDouble()-0.5)*0.01,
                (rng.nextDouble()-0.5)*0.01,
                (rng.nextDouble()-0.5)*0.01
        );
        n.v = base.add(micro);
        n.life = LIFE_MIN + rng.nextDouble()*(LIFE_MAX - LIFE_MIN);
        n.age = 0.0;
        n.size = SIZE_MIN + rng.nextDouble()*(SIZE_MAX - SIZE_MIN);
        n.yaw   = (float)(rng.nextDouble()*Math.PI*2);
        n.pitch = (float)((rng.nextDouble()-0.5)*0.4);
        n.roll  = (float)((rng.nextDouble()-0.5)*0.4);
        n.yawVel   = (float)((rng.nextDouble()-0.5)*0.6);
        n.pitchVel = (float)((rng.nextDouble()-0.5)*0.6);
        n.rollVel  = (float)((rng.nextDouble()-0.5)*0.6);
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

        double t = worldTime * 0.015;
        for (Node n : nodes) {
            if (!n.alive) continue;

            n.age += dt;
            if (n.age >= n.life) {
                n.alive = false; alive--; continue;
            }

            Vec3d curl = curl3(
                    n.p.x*0.35 + t*0.06,
                    n.p.y*0.35 + t*0.06,
                    n.p.z*0.35 + t*0.06
            ).multiply(NOISE_FORCE);

            Vec3d tw = new Vec3d(
                    (rng.nextDouble()-0.5)*TWINKLE,
                    (rng.nextDouble()-0.5)*TWINKLE,
                    (rng.nextDouble()-0.5)*TWINKLE
            );

            n.v = n.v.add(curl.multiply(dt)).add(tw.multiply(dt));
            n.v = n.v.multiply(DRAG);

            n.p = n.p.add(n.v.multiply(dt));

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

    private Vec3d curl3(double x, double y, double z) {
        double e = CURL_EPS;
        double Ny_z1 = noise3(x, y, z+e), Ny_z0 = noise3(x, y, z-e);
        double Nz_y1 = noise3(x, y+e, z), Nz_y0 = noise3(x, y-e, z);
        double Nz_x1 = noise3(x+e, y, z), Nz_x0 = noise3(x-e, y, z);
        double Nx_z1 = noise3(x, y, z+e), Nx_z0 = noise3(x, y, z-e);
        double Nx_y1 = noise3(x, y+e, z), Nx_y0 = noise3(x, y-e, z);
        double Ny_x1 = noise3(x+e, y, z), Ny_x0 = noise3(x-e, y, z);
        double cx = (Nz_y1 - Nz_y0) - (Ny_z1 - Ny_z0);
        double cy = (Nx_z1 - Nx_z0) - (Nz_x1 - Nz_x0);
        double cz = (Ny_x1 - Ny_x0) - (Nx_y1 - Nx_y0);
        double L = Math.sqrt(cx*cx+cy*cy+cz*cz) + 1e-9;
        return new Vec3d(cx/L, cy/L, cz/L);
    }
    private double noise3(double x, double y, double z) {
        int xi = fastFloor(x), yi = fastFloor(y), zi = fastFloor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = s(xf), v = s(yf), w = s(zf);
        double n000 = h(xi, yi, zi),   n100 = h(xi+1, yi, zi);
        double n010 = h(xi, yi+1, zi), n110 = h(xi+1, yi+1, zi);
        double n001 = h(xi, yi, zi+1), n101 = h(xi+1, yi, zi+1);
        double n011 = h(xi, yi+1, zi+1), n111 = h(xi+1, yi+1, zi+1);
        double x00 = lerp(u, n000, n100), x10 = lerp(u, n010, n110);
        double x01 = lerp(u, n001, n101), x11 = lerp(u, n011, n111);
        double y0 = lerp(v, x00, x10),    y1 = lerp(v, x01, x11);
        return lerp(w, y0, y1) * 2.0 - 1.0;
    }
    private static int fastFloor(double x){ int i=(int)x; return x<i?i-1:i; }
    private static double s(double t){ return t*t*(3-2*t); }
    private static double lerp(double t,double a,double b){ return a + t*(b-a); }
    private static double h(int x,int y,int z){
        long v = (long)x*374761393L ^ (long)y*668265263L ^ (long)z*700001L;
        v = (v ^ (v>>13))*1274126177L; v ^= (v>>16);
        return (v & 0xFFFFFFFFL) / 4294967296.0;
    }
}
