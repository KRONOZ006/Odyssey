package net.kronoz.odyssey.systems.physics.jetpack;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

public final class JetpackSmokeField {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int OVERLAY = OverlayTexture.DEFAULT_UV;
    private static final double CURL_EPS = 0.25;
    private static final double SAMPLE_STEP = 0.10;

    private final Random rng = new Random(0xA77F_5EEDL);

    public static final class Settings {
        public int capacity = 900;
        public double emitPerSecond = 480.0;
        public double lifeMin = 0.8, lifeMax = 2.0;
        public double sizeMin = 0.045, sizeMax = 0.085;
        public double speedMin = 0.9, speedMax = 2.2;
        public double drag = 0.92;
        public double lateralVisc = 0.96;
        public double curlForce = 1.35;
        public double gravity = -0.02;
        public double rise = 0.22;
        public double linger = 1.3;
        public double collideSlide = 0.75;
        public double coneAngleRad = Math.toRadians(16);
        public double spawnJitter = 0.04;
        public double worldRadius = 48.0;
        public double lodCull = 96.0;
    }

    public final Settings cfg;
    private final Node[] nodes;
    private int alive = 0, nextDead = 0;
    private double emitAcc = 0.0;

    private static final class Node {
        boolean alive;
        Vec3d p, v;
        float yaw, pitch, roll;
        float yawVel, pitchVel, rollVel;
        double age, life, size;
        // color over life computed on render
        static Node dead(){ Node n=new Node(); n.alive=false; n.p=Vec3d.ZERO; n.v=Vec3d.ZERO; return n; }
    }

    public JetpackSmokeField(Settings s) {
        this.cfg = s;
        this.nodes = new Node[s.capacity];
        for (int i=0;i<s.capacity;i++) nodes[i]=Node.dead();
    }

    public void burst(double particles, Vec3d pos, Vec3d dir) {
        emitAcc += particles;
        while (emitAcc >= 1.0 && alive < nodes.length) {
            int start = nextDead;
            do {
                Node n = nodes[nextDead];
                if (!n.alive) {
                    spawnInto(n, pos, dir);
                    alive++; emitAcc -= 1.0;
                    nextDead = (nextDead + 1) % nodes.length;
                    break;
                }
                nextDead = (nextDead + 1) % nodes.length;
            } while (nextDead != start);
            if (start == nextDead) break;
        }
    }

    private void spawnInto(Node n, Vec3d origin, Vec3d dir) {
        Vec3d jetDir = sampleCone(dir.normalize(), cfg.coneAngleRad);
        Vec3d jitter = new Vec3d(
                (rng.nextDouble()*2-1)*cfg.spawnJitter,
                (rng.nextDouble()*2-1)*cfg.spawnJitter,
                (rng.nextDouble()*2-1)*cfg.spawnJitter
        );
        n.p = origin.add(jitter);

        double sp = cfg.speedMin + rng.nextDouble()*(cfg.speedMax - cfg.speedMin);
        n.v = jetDir.multiply(sp);

        n.life = cfg.lifeMin + rng.nextDouble()*(cfg.lifeMax - cfg.lifeMin);
        n.age = 0.0;
        n.size = cfg.sizeMin + rng.nextDouble()*(cfg.sizeMax - cfg.sizeMin);

        n.yaw   = (float)(rng.nextDouble()*Math.PI*2);
        n.pitch = (float)((rng.nextDouble()-0.5)*0.6);
        n.roll  = (float)((rng.nextDouble()-0.5)*0.6);
        n.yawVel   = (float)((rng.nextDouble()-0.5)*3.0);
        n.pitchVel = (float)((rng.nextDouble()-0.5)*3.0);
        n.rollVel  = (float)((rng.nextDouble()-0.5)*3.0);

        n.alive = true;
    }

    public void update(double dt, World world, long time, Vec3d attractUp) {
        if (dt <= 0) return;

        double t = time * 0.02;
        for (int i=0;i<nodes.length;i++) {
            Node n = nodes[i];
            if (!n.alive) continue;

            n.age += dt;
            if (n.age >= n.life * cfg.linger) { n.alive=false; alive--; continue; }
            if (n.p.squaredDistanceTo(Vec3d.ZERO) > cfg.worldRadius*cfg.worldRadius*16) { n.alive=false; alive--; continue; }

            Vec3d curl = curl3(
                    n.p.x*0.45 + t*0.09 + i*0.0017,
                    n.p.y*0.45 + t*0.09 + i*0.0021,
                    n.p.z*0.45 + t*0.09 + i*0.0013
            ).multiply(cfg.curlForce);

            Vec3d up = attractUp.multiply(cfg.rise);
            Vec3d g = new Vec3d(0, cfg.gravity, 0);

            n.v = n.v.add(curl.multiply(dt)).add(up.multiply(dt)).add(g.multiply(dt));
            n.v = new Vec3d(n.v.x*cfg.lateralVisc, n.v.y*cfg.drag, n.v.z*cfg.lateralVisc);

            Vec3d next = n.p.add(n.v.multiply(dt));
            n.p = collideWorld(n.p, next, world);

            n.yaw += n.yawVel*dt;   n.yawVel   *= 0.98;
            n.pitch += n.pitchVel*dt; n.pitchVel *= 0.98;
            n.roll += n.rollVel*dt;  n.rollVel  *= 0.98;
        }
    }

    public void render(MatrixStack ms, VertexConsumer vc, Vec3d camBase, float tickDelta, World world) {
        Matrix4f m4 = ms.peek().getPositionMatrix();

        for (Node n : nodes) {
            if (!n.alive) continue;
            if (camBase.squaredDistanceTo(n.p) > cfg.lodCull*cfg.lodCull) continue;

            double lt = MathHelper.clamp(n.age / n.life, 0.0, cfg.linger);
            float a = alphaOverLife((float)lt);
            if (a < 0.01f) continue;

            float hs = (float)n.size * 0.5f;

            float cx = (float)(n.p.x - camBase.x);
            float cy = (float)(n.p.y - camBase.y);
            float cz = (float)(n.p.z - camBase.z);

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

            float[] col = colorOverLife((float)lt);
            float r = col[0], g = col[1], b = col[2];

            Vector3f C = new Vector3f(cx,cy,cz);
            Vector3f p000 = new Vector3f(C).sub(RX).sub(RY).sub(RZ);
            Vector3f p100 = new Vector3f(C).add(RX).sub(RY).sub(RZ);
            Vector3f p110 = new Vector3f(C).add(RX).add(RY).sub(RZ);
            Vector3f p010 = new Vector3f(C).sub(RX).add(RY).sub(RZ);
            Vector3f p001 = new Vector3f(C).sub(RX).sub(RY).add(RZ);
            Vector3f p101 = new Vector3f(C).add(RX).sub(RY).add(RZ);
            Vector3f p111 = new Vector3f(C).add(RX).add(RY).add(RZ);
            Vector3f p011 = new Vector3f(C).sub(RX).add(RY).add(RZ);

            putQuad(m4, ms, vc, r,g,b,a, 0,1,1,0, p100,p101,p111,p110, X);
            putQuad(m4, ms, vc, r,g,b,a, 0,1,1,0, p000,p010,p011,p001, new Vector3f(X).negate());
            putQuad(m4, ms, vc, r,g,b,a, 0,1,1,0, p010,p110,p111,p011, Y);
            putQuad(m4, ms, vc, r,g,b,a, 0,1,1,0, p000,p001,p101,p100, new Vector3f(Y).negate());
            putQuad(m4, ms, vc, r,g,b,a, 0,1,1,0, p101,p001,p011,p111, Z);
            putQuad(m4, ms, vc, r,g,b,a, 0,1,1,0, p100,p110,p010,p000, new Vector3f(Z).negate());
        }
    }

    private static float smooth(float t){ return t*t*(3f-2f*t); }
    private static float clamp01(float v){ return v<0?0:Math.min(1,v); }

    private static float[] colorOverLife(float t) {
        float len = 1.0f;
        float k0 = clamp01(t / 0.15f);                 // flame → plasma
        float k1 = clamp01((t-0.15f) / (0.50f-0.15f)); // plasma → smoke
        float k2 = clamp01((t-0.50f) / (len-0.50f));   // smoke fade

        float r0=1.00f, g0=0.78f, b0=0.15f; // yellow/orange
        float r1=0.60f, g1=0.25f, b1=0.90f; // purple
        float r2=0.62f, g2=0.62f, b2=0.62f; // grey

        float rA = r0 + (r1-r0)*smooth(k1);
        float gA = g0 + (g1-g0)*smooth(k1);
        float bA = b0 + (b1-b0)*smooth(k1);

        float r = rA + (r2-rA)*smooth(k2);
        float g = gA + (g2-gA)*smooth(k2);
        float b = bA + (b2-bA)*smooth(k2);
        return new float[]{r,g,b};
    }

    private static float alphaOverLife(float t) {
        float rise = clamp01(t / 0.12f);
        float fade = clamp01(1.0f - (float)Math.pow(Math.max(0, t-0.5f) / 0.5f, 1.1));
        return 0.10f + 0.90f * rise * fade;
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

    private Vec3d collideWorld(Vec3d cur, Vec3d next, World world) {
        Vec3d delta = next.subtract(cur);
        int steps = Math.max(1, (int)Math.ceil(delta.length() / SAMPLE_STEP));
        Vec3d p = cur;

        for (int i = 1; i <= steps; i++) {
            double t = (double)i / (double)steps;
            Vec3d probe = cur.lerp(next, t);
            BlockPos bp = BlockPos.ofFloored(probe);
            if (!world.isChunkLoaded(bp)) { p = probe; continue; }
            BlockState st = world.getBlockState(bp);
            if (!st.isAir()) {
                VoxelShape sh = st.getCollisionShape(world, bp);
                if (!sh.isEmpty()) {
                    Vec3d back = cur.lerp(next, Math.max(0, t - 0.10));
                    Direction face = nearestFace(probe, bp);
                    Vec3d nrm = new Vec3d(face.getUnitVector().x(), face.getUnitVector().y(), face.getUnitVector().z());
                    Vec3d slide = projectOntoPlane(next.subtract(back), nrm).multiply(cfg.collideSlide);
                    return back.add(slide).add(nrm.multiply(0.008));
                }
            }
            p = probe;
        }
        return p;
    }

    private static Vec3d projectOntoPlane(Vec3d v, Vec3d n) {
        double dot = v.x*n.x + v.y*n.y + v.z*n.z;
        return new Vec3d(v.x - dot*n.x, v.y - dot*n.y, v.z - dot*n.z);
    }
    private static Direction nearestFace(Vec3d p, BlockPos bp) {
        double cx = bp.getX()+0.5, cy = bp.getY()+0.5, cz = bp.getZ()+0.5;
        double dx = p.x-cx, dy = p.y-cy, dz = p.z-cz;
        double ax=Math.abs(dx), ay=Math.abs(dy), az=Math.abs(dz);
        if (ay >= ax && ay >= az) return dy>0?Direction.UP:Direction.DOWN;
        if (ax >= az) return dx>0?Direction.EAST:Direction.WEST;
        return dz>0?Direction.SOUTH:Direction.NORTH;
    }

    private static Vec3d sampleCone(Vec3d baseDir, double ang) {
        double u = Math.random(), v = Math.random();
        double theta = 2*Math.PI*u;
        double cosAng = Math.cos(ang);
        double z = cosAng + (1 - cosAng) * v;
        double s = Math.sqrt(Math.max(0, 1 - z*z));
        Vec3d local = new Vec3d(s*Math.cos(theta), z, s*Math.sin(theta));
        Vec3d a = Math.abs(baseDir.x) < 0.9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
        Vec3d t1 = baseDir.crossProduct(a).normalize();
        Vec3d t2 = t1.crossProduct(baseDir).normalize();
        return t1.multiply(local.x).add(baseDir.multiply(local.y)).add(t2.multiply(local.z)).normalize();
    }

    private Vec3d curl3(double x,double y,double z){
        double e=CURL_EPS;
        double Ny_z1=noise3(x,y,z+e), Ny_z0=noise3(x,y,z-e);
        double Nz_y1=noise3(x,y+e,z), Nz_y0=noise3(x,y-e,z);
        double Nz_x1=noise3(x+e,y,z), Nz_x0=noise3(x-e,y,z);
        double Nx_z1=noise3(x,y,z+e), Nx_z0=noise3(x,y,z-e);
        double Nx_y1=noise3(x,y+e,z), Nx_y0=noise3(x,y-e,z);
        double Ny_x1=noise3(x+e,y,z), Ny_x0=noise3(x-e,y,z);
        double cx=(Nz_y1-Nz_y0)-(Ny_z1-Ny_z0);
        double cy=(Nx_z1-Nx_z0)-(Nz_x1-Nz_x0);
        double cz=(Ny_x1-Ny_x0)-(Nx_y1-Nx_y0);
        double L=Math.sqrt(cx*cx+cy*cy+cz*cz)+1e-9;
        return new Vec3d(cx/L, cy/L, cz/L);
    }
    private static int fastFloor(double x){ int i=(int)x; return x<i?i-1:i; }
    private static double s(double t){ return t*t*(3-2*t); }
    private static double lerp(double t,double a,double b){ return a + t*(b-a); }
    private static double hash(int x,int y,int z){
        long v=(long)x*374761393L + (long)y*668265263L + (long)z*700001L;
        v=(v^(v>>13))*1274126177L; v^=(v>>16);
        return (v & 0xFFFFFFFFL) / 4294967296.0;
    }
    private double noise3(double x,double y,double z){
        int xi=fastFloor(x), yi=fastFloor(y), zi=fastFloor(z);
        double xf=x-xi, yf=y-yi, zf=z-zi;
        double u=s(xf), v=s(yf), w=s(zf);
        double n000=hash(xi,yi,zi), n100=hash(xi+1,yi,zi);
        double n010=hash(xi,yi+1,zi), n110=hash(xi+1,yi+1,zi);
        double n001=hash(xi,yi,zi+1), n101=hash(xi+1,yi,zi+1);
        double n011=hash(xi,yi+1,zi+1), n111=hash(xi+1,yi+1,zi+1);
        double x00=lerp(u,n000,n100), x10=lerp(u,n010,n110);
        double x01=lerp(u,n001,n101), x11=lerp(u,n011,n111);
        double y0=lerp(v,x00,x10), y1=lerp(v,x01,x11);
        return lerp(w,y0,y1)*2.0-1.0;
    }
}
