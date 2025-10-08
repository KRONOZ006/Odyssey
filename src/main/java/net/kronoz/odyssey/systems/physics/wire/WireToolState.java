package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public final class WireToolState {
    private WireToolState(){}

    private static final List<Entry> WIRES = new ArrayList<>();
    private static final WireDef DEF = WireDef.defaultCable(Identifier.of("odyssey","textures/effects/wire.png"));

    public static Entry.Pending pending = null;

    public static void setPending(BlockPos pos, Direction face, Block block){
        pending = new Entry.Pending(pos.toImmutable(), face, block);
    }
    public static boolean hasPending(){ return pending != null; }
    public static void clearPending(){ pending = null; }

    public static void spawnWire(BlockPos aPos, Direction aFace, Block aBlock,
                                 BlockPos bPos, Direction bFace, Block bBlock){
        WIRES.add(new Entry(
                Anchor.blockFace(aPos, aFace, aBlock),
                Anchor.blockFace(bPos, bFace, bBlock)
        ));
    }

    public static void renderAll(MatrixStack ms, VertexConsumerProvider buffers, int light){
        var mc = MinecraftClient.getInstance();
        if (mc==null || mc.world==null) return;

        final double REMOVE_AFTER_SECONDS = 6.0;
        final long now = System.currentTimeMillis();
        Iterator<Entry> it = WIRES.iterator();

        while (it.hasNext()){
            Entry e = it.next();

            boolean aAlive = e.a.isStillSameBlock(mc.world);
            boolean bAlive = e.b.isStillSameBlock(mc.world);

            Vec3d a = e.a.world();
            Vec3d b = e.b.world();

            WireManager.ensure(e.id, DEF, a, b);
            WireSim sim = WireManager.get(e.id);
            if (sim != null) sim.setPinned(aAlive, bAlive);

            WireManager.stepAndRender(e.id, a, aAlive, b, bAlive, ms, buffers, light, OverlayTexture.DEFAULT_UV);

            if (!aAlive || !bAlive){
                if (e.detachedAt == 0L) e.detachedAt = now;
                if (!aAlive && !bAlive && (now - e.detachedAt) > (long)(REMOVE_AFTER_SECONDS*1000)){
                    it.remove();
                }
            } else {
                e.detachedAt = 0L;
            }
        }
    }

    public static final class Entry {
        public final UUID id = UUID.randomUUID();
        public final Anchor a, b;
        public long detachedAt = 0L;
        public Entry(Anchor a, Anchor b){ this.a=a; this.b=b; }

        public static final class Pending {
            public final BlockPos pos;
            public final Direction face;
            public final Block block;
            public Pending(BlockPos pos, Direction face, Block block){ this.pos=pos; this.face=face; this.block=block; }
        }
    }

    public static final class Anchor {
        enum Kind { FREE, BLOCK_FACE }
        final Kind kind;
        final Vec3d point;
        final BlockPos pos;
        final Direction face;
        final Block originalBlock;

        private Anchor(Vec3d p){ kind=Kind.FREE; point=p; pos=null; face=null; originalBlock=null; }
        private Anchor(BlockPos pos, Direction face, Block block){ kind=Kind.BLOCK_FACE; this.pos=pos; this.face=face; this.originalBlock=block; point=null; }

        public static Anchor free(Vec3d p){ return new Anchor(p); }
        public static Anchor blockFace(BlockPos pos, Direction face, Block block){ return new Anchor(pos,face,block); }

        public boolean isStillSameBlock(net.minecraft.world.World world){
            if (kind==Kind.FREE) return true;
            return world.getBlockState(pos).getBlock() == originalBlock;
        }

        public Vec3d world(){
            if (kind==Kind.FREE) return point;
            return Vec3d.ofCenter(pos).add(
                    face.getOffsetX()*0.501,
                    face.getOffsetY()*0.501,
                    face.getOffsetZ()*0.501
            );
        }
    }
}
