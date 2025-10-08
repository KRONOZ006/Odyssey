package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public final class WireSim {
    public static final class Node {
        public Vec3d p, prev;
        public float invM;
        Node(Vec3d v, float inv){ p=v; prev=v; invM=inv; }
    }

    private final WireDef def;
    private final Node[] nodes;
    private double targetLen;
    private boolean pinStart = true, pinEnd = true;

    private static final double CONTACT_SLOP=0.0015, BAUMGARTE=0.34, DYN_FRICTION=0.30;

    public WireSim(WireDef def, Vec3d a, Vec3d b) {
        this.def = def;
        this.nodes = new Node[def.segments + 1];
        for (int i=0;i<nodes.length;i++){
            double t = (double)i/(nodes.length-1);
            nodes[i] = new Node(a.lerp(b, t), 1f);
        }
        setEndpoints(a,b);
    }

    public void setPinned(boolean startPinned, boolean endPinned){
        this.pinStart = startPinned;
        this.pinEnd   = endPinned;
        nodes[0].invM = startPinned ? 0f : 1f;
        nodes[nodes.length-1].invM = endPinned ? 0f : 1f;
    }

    public void setEndpoints(Vec3d a, Vec3d b){
        double base = Math.max(1e-5, a.distanceTo(b));
        double slack = def.baseSlack + def.sagPerMeter * base;
        this.targetLen = base * (1.0 + slack);
        if (pinStart){ nodes[0].p=a; nodes[0].prev=a; }
        if (pinEnd){ int i=nodes.length-1; nodes[i].p=b; nodes[i].prev=b; }
    }

    public Node[] nodes(){ return nodes; }

    public void step(World world, Vec3d a, Vec3d b){
        setEndpoints(a,b);

        double maxMove = 0.0;
        for (Node n : nodes) {
            double L2 = n.p.subtract(n.prev).lengthSquared();
            if (L2 > maxMove) maxMove = L2;
        }
        double move = Math.sqrt(maxMove);
        int extra = move > (def.halfWidth * 0.6) ? 2 : 0;
        int totalSub = Math.min(6, def.substeps + extra);
        float h = 1.0f / totalSub;

        for (int s=0; s<totalSub; s++){
            integrate(h);
            for (int k=0;k<def.iters;k++){
                distanceConstraints();
                lengthConstraint();
                bendSmoothing(def.bendK);
                if (pinStart){ nodes[0].p=a; }
                if (pinEnd){ nodes[nodes.length-1].p=b; }
            }
            collideBlocks(world);
        }
    }

    private void integrate(float dt){
        final double g = def.gravity * dt * dt;
        final double damp = Math.min(0.98, def.damping);
        for (Node n : nodes){
            if (n.invM == 0f) continue;
            Vec3d cur = n.p;
            Vec3d v = cur.subtract(n.prev).multiply(1.0 - damp);
            n.prev = cur;
            n.p = cur.add(v.x, v.y - g, v.z);
        }
    }

    private void distanceConstraints(){
        double segLen = targetLen / (nodes.length-1);
        for (int i=0;i<nodes.length-1;i++){
            Node a = nodes[i], b = nodes[i+1];
            Vec3d d = b.p.subtract(a.p);
            double L = d.length(); if (L<=1e-9) continue;
            double diff = (L - segLen)/L;
            float wa=a.invM, wb=b.invM; float ws=wa+wb; if (ws==0f) continue;
            Vec3d corr = d.multiply(0.5*diff);
            if (wa>0) a.p = a.p.add(corr.multiply(+wa/ws*2));
            if (wb>0) b.p = b.p.add(corr.multiply(-wb/ws*2));
        }
    }

    private void lengthConstraint(){
        double total=0.0;
        for (int i=0;i<nodes.length-1;i++) total += nodes[i].p.distanceTo(nodes[i+1].p);
        if (total<1e-9) return;
        double scale = targetLen/total;
        if (Math.abs(scale-1.0)<1e-6) return;
        Vec3d s=nodes[0].p, e=nodes[nodes.length-1].p;
        for (int i=1;i<nodes.length-1;i++){
            Node n = nodes[i]; if (n.invM==0f) continue;
            double t=(double)i/(nodes.length-1);
            Vec3d tgt=s.lerp(e,t);
            Vec3d cur=n.p;
            n.p = tgt.add(cur.subtract(tgt).multiply(scale));
        }
    }

    private void bendSmoothing(float k){
        if (k <= 0f) return;
        for (int i=1;i<nodes.length-1;i++){
            Node a=nodes[i-1], b=nodes[i], c=nodes[i+1];
            if (b.invM == 0f) continue;
            Vec3d mid = a.p.add(c.p).multiply(0.5);
            b.p = b.p.multiply(1.0 - k).add(mid.multiply(k));
        }
    }

    private void collideBlocks(World world){
        if (world==null) return;
        final double r = Math.max(0.008, def.halfWidth * 0.95);

        for (int pass=0; pass<def.collidePasses; pass++) {
            for (Node n : nodes){
                if (n.invM==0f) continue;
                BlockPos base = BlockPos.ofFloored(n.p);

                for (int by=-1; by<=1; by++)
                    for (int bx=-1; bx<=1; bx++)
                        for (int bz=-1; bz<=1; bz++){
                            BlockPos p = base.add(bx,by,bz);
                            var state = world.getBlockState(p);
                            if (state.isAir()) continue;

                            VoxelShape shape = state.getCollisionShape(world, p, ShapeContext.absent());
                            if (shape.isEmpty()) continue;

                            iterateShapeBoxes(shape, p, (ax0,ay0,az0, ax1,ay1,az1) ->
                                    resolve(n, ax0,ay0,az0, ax1,ay1,az1, r));
                        }
            }
        }
    }

    @FunctionalInterface
    private interface BoxConsumer {
        void accept(double minX,double minY,double minZ,double maxX,double maxY,double maxZ);
    }

    private static void iterateShapeBoxes(VoxelShape shape, BlockPos pos, BoxConsumer consumer){
        boolean success = false;
        try {
            shape.forEachBox((x0, y0, z0, x1, y1, z1) -> consumer.accept(
                    pos.getX()+x0, pos.getY()+y0, pos.getZ()+z0,
                    pos.getX()+x1, pos.getY()+y1, pos.getZ()+z1
            ));
            success = true;
        } catch (Throwable ignored) {}

        if (!success) {
            try {
                for (Box local : shape.getBoundingBoxes()) {
                    Box bb = local.offset(pos);
                    consumer.accept(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
                }
            } catch (Throwable ignoredEvenMore) {}
        }
    }

    private void resolve(Node n,double ax0,double ay0,double az0,double ax1,double ay1,double az1,double r){
        double cx=clamp(n.p.x,ax0,ax1), cy=clamp(n.p.y,ay0,ay1), cz=clamp(n.p.z,az0,az1);
        double nx=n.p.x-cx, ny=n.p.y-cy, nz=n.p.z-cz;
        double d2=nx*nx+ny*ny+nz*nz;

        boolean embedded = (n.p.x > ax0 && n.p.x < ax1 &&
                n.p.y > ay0 && n.p.y < ay1 &&
                n.p.z > az0 && n.p.z < az1);

        if (d2 < 1e-12) {
            nx = 0; ny = 1; nz = 0;
            d2 = 1.0;
        }

        double dist=Math.sqrt(d2);
        if (dist >= r && !embedded) return;

        double nmx=nx/dist, nmy=ny/dist, nmz=nz/dist;

        double pen = embedded ? r : (r - dist);
        if (pen <= CONTACT_SLOP) return;

        double corr = BAUMGARTE * (pen - CONTACT_SLOP);
        if (embedded) { nmx = 0; nmy = 1; nmz = 0; corr = Math.max(corr, r * 0.35); }

        n.p = n.p.add(nmx*corr, nmy*corr, nmz*corr);

        Vec3d vel=n.p.subtract(n.prev);
        double vdotn=vel.x*nmx+vel.y*nmy+vel.z*nmz;
        if (vdotn<0.0) vel=vel.subtract(nmx*vdotn,nmy*vdotn,nmz*vdotn);
        n.prev=n.p.subtract(vel.multiply(1.0-DYN_FRICTION));
    }

    private static double clamp(double v,double lo,double hi){
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
