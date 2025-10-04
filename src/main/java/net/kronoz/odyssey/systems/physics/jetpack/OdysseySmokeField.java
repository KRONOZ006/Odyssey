package net.kronoz.odyssey.systems.physics.jetpack;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

public final class OdysseySmokeField {

    private static final double MAX_WORLD_RADIUS = 80.0;
    private static final double DRAG = 0.90;
    private static final double LATERAL_VISC = 0.94;
    private static final double BUOYANCY_BASE = 0.020;
    private static final double BUOYANCY_TEMP = 0.040;
    private static final double GRAVITY_RESIDUAL = 0.004;
    private static final double MIN_UP_SPEED = 0.008;

    private static final double CURL_EPS = 0.22;
    private static final double CURL_FORCE = 0.45;
    private static final double WIND_MAG = 0.010;

    private static final double SAMPLE_STEP = 0.10;
    private static final double WALL_PUSH = 0.65;

    private static final double LIFE_MIN = 2.5;
    private static final double LIFE_MAX = 5.0;
    private static final double SIZE_MIN = 0.02;
    private static final double SIZE_MAX = 0.05;

    private static final double CONE_ANGLE_RAD = Math.toRadians(16.0);

    private static final double AXIS_SPIRAL = 0.55;
    private static final double AXIS_PULL = 0.35;
    private static final double EARLY_SPRING = 16.0;
    private static final double EARLY_SPRING_TIME = 0.12;
    private static final double MAX_SPEED = 0.35;

    private double emitRate = 30.0;
    private double emitAccumulator = 0.0;
    private int aliveCount = 0;
    private int nextDeadIdx = 0;

    private final Node[] nodes;
    private final Random rng = new Random(0x9E3779B97F4A7C15L);

    private float endR=0.729f, endG=0.729f, endB=0.729f;
    private float midR=1.00f, midG=0.60f, midB=0.0f;
    private float startR=0.322f, startG=0.0f, startB=1.0f;

    private Vec3d thrustAxis = new Vec3d(0, 1, 0);
    private boolean emitting = true;

    public OdysseySmokeField(int capacity) {
        nodes = new Node[capacity];
        for (int i=0;i<capacity;i++) nodes[i] = Node.dead();
    }

    public OdysseySmokeField(int capacity, int r, int g, int b) {
        this(capacity);
        this.endR = MathHelper.clamp(r/255f,0,1);
        this.endG = MathHelper.clamp(g/255f,0,1);
        this.endB = MathHelper.clamp(b/255f,0,1);
    }

    public int aliveCount() { return aliveCount; }
    public OdysseySmokeField setEmitting(boolean e) { this.emitting = e; return this; }
    public OdysseySmokeField setAxis(Vec3d axisWS) {
        if (axisWS != null && axisWS.lengthSquared() > 1e-9) this.thrustAxis = axisWS.normalize();
        return this;
    }
    public OdysseySmokeField setJetAxis(Vec3d axisWS) { return setAxis(axisWS); }
    public double getAxisSpiral() { return AXIS_SPIRAL; }
    public double getAxisPull()   { return AXIS_PULL; }
    public boolean isEmpty() { return aliveCount == 0; }

    private static final class Node {
        boolean alive;
        Vec3d p, v;
        double temp, age, life, baseSize;
        float yaw, pitch, roll, yawVel, pitchVel, rollVel;
        static Node dead(){
            Node n = new Node();
            n.alive=false; n.p=Vec3d.ZERO; n.v=Vec3d.ZERO;
            n.temp=0; n.age=0; n.life=0; n.baseSize=0;
            return n;
        }
    }

    public void update(double dt, Vec3d originWS, World world, long worldTime) {
        if (dt <= 0.0) return;

        if (emitting && aliveCount < nodes.length) {
            emitAccumulator += emitRate * dt;
            while (emitAccumulator >= 1.0 && aliveCount < nodes.length) {
                int start = nextDeadIdx;
                do {
                    Node n = nodes[nextDeadIdx];
                    if (!n.alive) {
                        spawnInto(n, originWS);
                        aliveCount++;
                        emitAccumulator -= 1.0;
                        nextDeadIdx = (nextDeadIdx + 1) % nodes.length;
                        break;
                    }
                    nextDeadIdx = (nextDeadIdx + 1) % nodes.length;
                } while (nextDeadIdx != start);
                if (start == nextDeadIdx) break;
            }
        }

        stepAll(dt, originWS, world, worldTime);
    }

    private void spawnInto(Node n, Vec3d origin) {
        Vec3d dir = sampleConeDir(thrustAxis, CONE_ANGLE_RAD);
        n.p = origin;
        n.v = dir.multiply(0.18 + rng.nextDouble()*0.08).add(jitter3(0.01));
        n.life = LIFE_MIN + rng.nextDouble()*(LIFE_MAX - LIFE_MIN);
        n.baseSize = SIZE_MIN + rng.nextDouble()*(SIZE_MAX - SIZE_MIN);
        n.temp = 1.0;
        n.age = 0.0;
        n.yaw   = (float)(rng.nextDouble() * Math.PI * 2.0);
        n.pitch = (float)((rng.nextDouble()-0.5) * Math.toRadians(40));
        n.roll  = (float)((rng.nextDouble()-0.5) * Math.toRadians(40));
        n.yawVel   = (float)((rng.nextDouble()-0.5) * 1.5);
        n.pitchVel = (float)((rng.nextDouble()-0.5) * 1.2);
        n.rollVel  = (float)((rng.nextDouble()-0.5) * 1.2);
        n.alive = true;
    }

    private void stepAll(double dt, Vec3d origin, World world, long worldTime) {
        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            if (!n.alive) continue;

            n.age += dt;

            if (n.age >= n.life || n.p.squaredDistanceTo(origin) > (MAX_WORLD_RADIUS*MAX_WORLD_RADIUS)) {
                n.alive = false; aliveCount--; continue;
            }
            if (isInsideSolid(n.p, world)) { n.alive = false; aliveCount--; continue; }

            double t = (worldTime * 0.05) + i * 7.31;

            Vec3d curl = curl3(n.p.x*0.55 + t*0.08, n.p.y*0.50 + t*0.08, n.p.z*0.55 + t*0.08);
            Vec3d wind = windAt(n.p, worldTime, WIND_MAG);
            double buoy = BUOYANCY_BASE + Math.max(0.0, n.temp) * BUOYANCY_TEMP;

            Vec3d a = new Vec3d(
                    curl.x*CURL_FORCE + wind.x,
                    curl.y*CURL_FORCE + buoy - GRAVITY_RESIDUAL + wind.y*0.25,
                    curl.z*CURL_FORCE + wind.z
            );

            a = a.add(axisField(n, origin, dt));

            n.v = n.v.add(a.multiply(dt));

            double spd = n.v.length();
            if (spd > MAX_SPEED) n.v = n.v.multiply(MAX_SPEED / Math.max(spd, 1e-6));

            n.v = new Vec3d(n.v.x*LATERAL_VISC, n.v.y*DRAG, n.v.z*LATERAL_VISC);
            if (n.v.y < MIN_UP_SPEED) n.v = new Vec3d(n.v.x, MIN_UP_SPEED, n.v.z);

            Vec3d next = n.p.add(n.v.multiply(dt));
            Vec3d after = collideWorld(n.p, next, world);
            if (isInsideSolid(after, world)) { n.alive = false; aliveCount--; continue; }
            n.p = after;

            n.temp *= 0.996;
            n.yaw   += n.yawVel   * dt;
            n.pitch += n.pitchVel * dt;
            n.roll  += n.rollVel  * dt;
        }
    }

    private Vec3d axisField(Node n, Vec3d origin, double dt) {
        Vec3d a = thrustAxis;
        Vec3d rel = n.p.subtract(origin);
        double along = rel.dotProduct(a);
        Vec3d proj = a.multiply(along);
        Vec3d radial = rel.subtract(proj);

        double k = MathHelper.clamp(radial.length(), 0.0, 1.0);
        Vec3d pull = radial.lengthSquared() > 1e-9 ? radial.normalize().multiply(-AXIS_PULL * k) : Vec3d.ZERO;

        Vec3d basisU = Math.abs(a.x) < 0.9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
        Vec3d u = a.crossProduct(basisU).normalize();
        Vec3d v = a.crossProduct(u).normalize();
        double spin = AXIS_SPIRAL * (0.5 + 0.5 * Math.sin(along * 10.0));
        Vec3d swirl = u.multiply(-spin).add(v.multiply(spin));

        if (n.age < EARLY_SPRING_TIME) {
            double w = 1.0 - (n.age / EARLY_SPRING_TIME);
            Vec3d kick = a.multiply(EARLY_SPRING * w);
            return pull.add(swirl).add(kick);
        }
        return pull.add(swirl);
    }

    private boolean isInsideSolid(Vec3d p, World world) {
        BlockPos bp = BlockPos.ofFloored(p);
        BlockState st = world.getBlockState(bp);
        if (st.isAir()) return false;
        VoxelShape sh = st.getCollisionShape(world, bp);
        if (sh.isEmpty()) return false;
        var aabb = sh.getBoundingBox();
        double lx = p.x - bp.getX(), ly = p.y - bp.getY(), lz = p.z - bp.getZ();
        return aabb != null && aabb.contains(lx, ly, lz);
    }

    private Vec3d collideWorld(Vec3d cur, Vec3d next, World world) {
        Vec3d delta = next.subtract(cur);
        int steps = Math.max(1, (int)Math.ceil(delta.length() / SAMPLE_STEP));
        Vec3d p = cur;

        for (int i = 1; i <= steps; i++) {
            double t = (double)i / (double)steps;
            Vec3d probe = cur.lerp(next, t);

            BlockPos bp = BlockPos.ofFloored(probe);
            BlockState st = world.getBlockState(bp);
            if (!st.isAir()) {
                VoxelShape sh = st.getCollisionShape(world, bp);
                if (!sh.isEmpty()) {
                    Vec3d back = cur.lerp(next, Math.max(0, t - 0.10));
                    Direction face = nearestFace(probe, bp);
                    Vec3d nrm = new Vec3d(face.getUnitVector().x(), face.getUnitVector().y(), face.getUnitVector().z());
                    Vec3d slide = projectOntoPlane(next.subtract(back), nrm).multiply(0.72);
                    p = back.add(slide).add(nrm.multiply(WALL_PUSH * 0.012));
                    return p;
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
        double cx = bp.getX() + 0.5, cy = bp.getY() + 0.5, cz = bp.getZ() + 0.5;
        double dx = p.x - cx, dy = p.y - cy, dz = p.z - cz;
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private Vec3d windAt(Vec3d p, long time, double mag) {
        double tt = time * 0.02;
        double nx = fbm(p.x*0.08 + tt*0.05, p.y*0.05, p.z*0.08) - 0.5;
        double nz = fbm(p.x*0.08, p.y*0.05 + tt*0.05, p.z*0.08) - 0.5;
        return new Vec3d(nx * mag, 0.0, nz * mag);
    }

    private double fbm(double x, double y, double z) {
        double s = 0.0, a = 0.5;
        for (int i = 0; i < 3; i++) {
            s += noise3(x, y, z) * a;
            x *= 2.0; y *= 2.0; z *= 2.0;
            a *= 0.5;
        }
        return s;
    }

    private Vec3d curl3(double x, double y, double z) {
        double ex = CURL_EPS, ey = CURL_EPS, ez = CURL_EPS;
        double Ny_z1 = noise3(x, y, z + ez), Ny_z0 = noise3(x, y, z - ez);
        double Nz_y1 = noise3(x, y + ey, z), Nz_y0 = noise3(x, y - ey, z);
        double Nz_x1 = noise3(x + ex, y, z), Nz_x0 = noise3(x - ex, y, z);
        double Nx_z1 = noise3(x, y, z + ez), Nx_z0 = noise3(x, y, z - ez);
        double Nx_y1 = noise3(x, y + ey, z), Nx_y0 = noise3(x, y - ey, z);
        double Ny_x1 = noise3(x + ex, y, z), Ny_x0 = noise3(x - ex, y, z);
        double cx = (Nz_y1 - Nz_y0) - (Ny_z1 - Ny_z0);
        double cy = (Nx_z1 - Nx_z0) - (Nz_x1 - Nz_x0);
        double cz = (Ny_x1 - Ny_x0) - (Nx_y1 - Nx_y0);
        double len = Math.sqrt(cx*cx + cy*cy + cz*cz) + 1e-8;
        return new Vec3d(cx/len, cy/len, cz/len);
    }

    private double noise3(double x, double y, double z) {
        int xi = fastFloor(x), yi = fastFloor(y), zi = fastFloor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = smooth(xf), v = smooth(yf), w = smooth(zf);
        double n000 = hash(xi,   yi,   zi);
        double n100 = hash(xi+1, yi,   zi);
        double n010 = hash(xi,   yi+1, zi);
        double n110 = hash(xi+1, yi+1, zi);
        double n001 = hash(xi,   yi,   zi+1);
        double n101 = hash(xi+1, yi,   zi+1);
        double n011 = hash(xi,   yi+1, zi+1);
        double n111 = hash(xi+1, yi+1, zi+1);
        double x00 = lerp(u, n000, n100);
        double x10 = lerp(u, n010, n110);
        double x01 = lerp(u, n001, n101);
        double x11 = lerp(u, n011, n111);
        double y0 = lerp(v, x00, x10);
        double y1 = lerp(v, x01, x11);
        return lerp(w, y0, y1) * 2.0 - 1.0;
    }

    private static int fastFloor(double x) { int i = (int)x; return x < i ? i-1 : i; }
    private static double smooth(double t){ return t*t*(3 - 2*t); }
    private static double lerp(double t, double a, double b){ return a + t*(b-a); }
    private static double hash(int x, int y, int z) {
        long h = (long)x * 374761393L + (long)y * 668265263L + (long)z * 700001L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h ^= (h >> 16);
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    private Vec3d sampleConeDir(Vec3d axis, double angRad) {
        Vec3d a = axis.normalize();
        Vec3d tmp = Math.abs(a.x) < 0.9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
        Vec3d u = a.crossProduct(tmp).normalize();
        Vec3d v = a.crossProduct(u).normalize();

        double u1 = rng.nextDouble();
        double u2 = rng.nextDouble();
        double theta = 2.0 * Math.PI * u1;
        double cosAng = Math.cos(angRad);
        double z = cosAng + (1 - cosAng) * u2;
        double s = Math.sqrt(Math.max(0.0, 1 - z*z));
        double x = s * Math.cos(theta);
        double y = s * Math.sin(theta);

        return u.multiply(x).add(v.multiply(y)).add(a.multiply(z)).normalize();
    }

    private Vec3d jitter3(double s) {
        return new Vec3d((Math.random()*2-1)*s, (Math.random()*2-1)*s, (Math.random()*2-1)*s);
    }

    public void render(MatrixStack ms, VertexConsumer vc, Vec3d cameraBase, float tickDelta) {
        Matrix4f m4 = ms.peek().getPositionMatrix();
        final int FULL_BRIGHT = 0x00F000F0;
        final int OVERLAY = OverlayTexture.DEFAULT_UV;

        for (Node n : nodes) {
            if (!n.alive) continue;

            double lifeT = n.age / n.life;

            float cR, cG, cB;
            if (lifeT < 0.45) {
                double t = lifeT / 0.45;
                cR = (float)lerp(t, midR, startR);
                cG = (float)lerp(t, midG, startG);
                cB = (float)lerp(t, midB, startB);
            } else {
                double t = (lifeT - 0.45) / 0.55;
                cR = (float)lerp(t, startR, endR);
                cG = (float)lerp(t, startG, endG);
                cB = (float)lerp(t, startB, endB);
            }

            float rise = (float)MathHelper.clamp(lifeT * 1.2, 0.0, 1.0);
            float fall = (float)Math.pow(1.0 - MathHelper.clamp(lifeT, 0.0, 1.0), 1.15);
            float alpha = MathHelper.clamp(0.08f + 0.85f*rise*fall, 0f, 0.9f);

            float edge = (float)(n.baseSize * (0.9 + lifeT*2.0));
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

            X = new Vector3f(cyw*X.x + 0*X.y + -syw*X.z, X.y, syw*X.x + 0*X.y + cyw*X.z);
            Y = new Vector3f(cyw*Y.x + 0*Y.y + -syw*Y.z, Y.y, syw*Y.x + 0*Y.y + cyw*Y.z);
            Z = new Vector3f(cyw*Z.x + 0*Z.y + -syw*Z.z, Z.y, syw*Z.x + 0*Z.y + cyw*Z.z);

            X = new Vector3f(X.x,  cpi*X.y - spi*X.z,  spi*X.y + cpi*X.z);
            Y = new Vector3f(Y.x,  cpi*Y.y - spi*Y.z,  spi*Y.y + cpi*Y.z);
            Z = new Vector3f(Z.x,  cpi*Z.y - spi*Z.z,  spi*Z.y + cpi*Z.z);

            X = new Vector3f( cro*X.x - sro*X.y, sro*X.x + cro*X.y, X.z);
            Y = new Vector3f( cro*Y.x - sro*Y.y, sro*Y.x + cro*Y.y, Y.z);
            Z = new Vector3f( cro*Z.x - sro*Z.y, sro*Z.x + cro*Z.y, Z.z);

            Vector3f RX = new Vector3f(X).mul(hs);
            Vector3f RY = new Vector3f(Y).mul(hs);
            Vector3f RZ = new Vector3f(Z).mul(hs);

            Vector3f C = new Vector3f(cx,cy,cz);
            Vector3f p000 = new Vector3f(C).sub(RX).sub(RY).sub(RZ);
            Vector3f p100 = new Vector3f(C).add(RX).sub(RY).sub(RZ);
            Vector3f p110 = new Vector3f(C).add(RX).add(RY).sub(RZ);
            Vector3f p010 = new Vector3f(C).sub(RX).add(RY).sub(RZ);

            Vector3f p001 = new Vector3f(C).sub(RX).sub(RY).add(RZ);
            Vector3f p101 = new Vector3f(C).add(RX).sub(RY).add(RZ);
            Vector3f p111 = new Vector3f(C).add(RX).add(RY).add(RZ);
            Vector3f p011 = new Vector3f(C).sub(RX).add(RY).add(RZ);

            putQuad(m4, ms, vc, cR,cG,cB,alpha, 0,1,1,0, p100, p101, p111, p110, X);
            putQuad(m4, ms, vc, cR,cG,cB,alpha, 0,1,1,0, p000, p010, p011, p001, new Vector3f(X).negate());
            putQuad(m4, ms, vc, cR,cG,cB,alpha, 0,1,1,0, p010, p110, p111, p011, Y);
            putQuad(m4, ms, vc, cR,cG,cB,alpha, 0,1,1,0, p000, p001, p101, p100, new Vector3f(Y).negate());
            putQuad(m4, ms, vc, cR,cG,cB,alpha, 0,1,1,0, p101, p001, p011, p111, Z);
            putQuad(m4, ms, vc, cR,cG,cB,alpha, 0,1,1,0, p100, p110, p010, p000, new Vector3f(Z).negate());
        }
    }

    private void putQuad(Matrix4f m4, MatrixStack ms, VertexConsumer vc,
                         float r, float g, float b, float a,
                         float u0, float v1, float u1, float v0,
                         Vector3f P0, Vector3f P1, Vector3f P2, Vector3f P3, Vector3f N) {
        final int packedLight = 0x00F000F0;
        final int packedOverlay = OverlayTexture.DEFAULT_UV;
        int nx = (int)Math.signum(N.x()); int ny = (int)Math.signum(N.y()); int nz = (int)Math.signum(N.z());
        vc.vertex(m4, P0.x, P0.y, P0.z).color(r,g,b,a).texture(u0, v1).overlay(packedOverlay).light(packedLight).normal(ms.peek(), nx, ny, nz);
        vc.vertex(m4, P1.x, P1.y, P1.z).color(r,g,b,a).texture(u1, v1).overlay(packedOverlay).light(packedLight).normal(ms.peek(), nx, ny, nz);
        vc.vertex(m4, P2.x, P2.y, P2.z).color(r,g,b,a).texture(u1, v0).overlay(packedOverlay).light(packedLight).normal(ms.peek(), nx, ny, nz);
        vc.vertex(m4, P3.x, P3.y, P3.z).color(r,g,b,a).texture(u0, v0).overlay(packedOverlay).light(packedLight).normal(ms.peek(), nx, ny, nz);
    }
}
