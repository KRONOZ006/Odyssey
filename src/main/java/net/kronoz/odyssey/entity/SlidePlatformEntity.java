package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class SlidePlatformEntity extends Entity {
    public static class Part { public BlockPos off; public BlockState state; public Part(BlockPos o, BlockState s){ off=o; state=s; } }
    private double movedAccum = 0.0;

    public static final TrackedData<Float> CX = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> CY = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> CZ = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> VX = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> VZ = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Integer> DW = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> DL = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> DH = DataTracker.registerData(SlidePlatformEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final List<Part> parts = new ArrayList<>();
    private final Map<BlockPos, UUID> displayIds = new HashMap<>();
    private final Map<BlockPos, UUID> colliderIds = new HashMap<>();

    private boolean moving=false;
    private Vec3d base=Vec3d.ZERO;
    private BlockPos origin = BlockPos.ORIGIN;
    private double speed = 0.03;
    private int stepX=1, stepZ=0;
    private int remaining=0;

    public SlidePlatformEntity(EntityType<? extends Entity> type, World world){ super(type, world); this.noClip=true; }
    @Override protected void initDataTracker(DataTracker.Builder b){
        b.add(CX,0f); b.add(CY,0f); b.add(CZ,0f); b.add(VX,0f); b.add(VZ,0f); b.add(DW,1); b.add(DL,1); b.add(DH,1);
    }

    public void configureHorizontal(BlockPos originBlock, List<Part> p, double spd, int dx, int dz, int dist){
        origin = originBlock;
        base = new Vec3d(origin.getX(), origin.getY(), origin.getZ());
        setPos(base.x+0.5, base.y, base.z+0.5);
        clearDisplays();
        clearColliders();
        parts.clear(); parts.addAll(p);
        speed = spd;
        stepX = Integer.signum(dx); stepZ = Integer.signum(dz);
        remaining = Math.abs(dist);
        moving = remaining>0;
        recalcAABB(); ensureDisplays(); ensureColliders(); updateAll(true, 0.0, 0.0);
        if(!getWorld().isClient){
            int[] d = dims();
            var dt = getDataTracker();
            dt.set(CX,(float)(base.x+0.5)); dt.set(CY,(float)base.y); dt.set(CZ,(float)(base.z+0.5));
            dt.set(VX,0f); dt.set(VZ,0f);
            dt.set(DW,d[0]); dt.set(DL,d[1]); dt.set(DH,d[2]);
        }
    }

    @Override public void tick(){
        super.tick();
        if(getWorld().isClient) return;

        if(moving && remaining > 0){
            double mvx = stepX * speed;
            double mvz = stepZ * speed;

            if(willHitSolidAhead(mvx, mvz)){
                reifyAndDiscard();
                return;
            }

            Vec3d step = new Vec3d(mvx, 0.0, mvz);

            Box next = getBoundingBox().offset(step).expand(-0.01, 0.0, -0.01);

            boolean blocked = getWorld().getBlockCollisions(this, next).iterator().hasNext();
            if(!blocked){
                base = base.add(step);
                setPos(base.x+0.5, base.y, base.z+0.5);
                recalcAABB();
                updateAll(false, mvx, mvz);

                movedAccum += Math.abs(mvx) + Math.abs(mvz);
                while(movedAccum >= 1.0 && remaining > 0){
                    movedAccum -= 1.0;
                    remaining--;
                }
            } else {
                reifyAndDiscard();
                return;
            }

            if(remaining <= 0){
                reifyAndDiscard();
                return;
            }
        } else {
            updateAll(false, 0.0, 0.0);
            reifyAndDiscard();
            return;
        }

        int[] d = dims();
        var dt = getDataTracker();
        dt.set(CX,(float)(base.x+0.5)); dt.set(CY,(float)base.y); dt.set(CZ,(float)(base.z+0.5));
        dt.set(VX,(float)(stepX*speed)); dt.set(VZ,(float)(stepZ*speed));
        dt.set(DW,d[0]); dt.set(DL,d[1]); dt.set(DH,d[2]);
    }

    private boolean willHitSolidAhead(double mvx, double mvz){
        final double ahead = 0.51;
        for(Part p : parts){
            double nx = base.x + p.off.getX() + (mvx > 0 ? ahead : (mvx < 0 ? -ahead : 0));
            double nz = base.z + p.off.getZ() + (mvz > 0 ? ahead : (mvz < 0 ? -ahead : 0));
            int bx = (int)Math.floor(nx);
            int by = (int)Math.floor(base.y + p.off.getY());
            int bz = (int)Math.floor(nz);
            var st = getWorld().getBlockState(new BlockPos(bx, by, bz));
            if(!st.isAir() && !st.getCollisionShape(getWorld(), new BlockPos(bx,by,bz)).isEmpty()){
                return true;
            }
        }
        return false;
    }


    private int[] dims(){
        if(parts.isEmpty()) return new int[]{1,1,1};
        int minX=999999,minY=999999,minZ=999999,maxX=-999999,maxY=-999999,maxZ=-999999;
        for(Part p: parts){
            int x=p.off.getX(), y=p.off.getY(), z=p.off.getZ();
            if(x<minX)minX=x; if(y<minY)minY=y; if(z<minZ)minZ=z;
            if(x>maxX)maxX=x; if(y>maxY)maxY=y; if(z>maxZ)maxZ=z;
        }
        int w=(maxX-minX)+1, l=(maxZ-minZ)+1, h=(maxY-minY)+1;
        if(w<1)w=1; if(l<1)l=1; if(h<1)h=1;
        return new int[]{w,l,h};
    }

    private void ensureDisplays(){
        if(getWorld().isClient) return;
        for(Part p: parts){
            if(!displayIds.containsKey(p.off)){
                DisplayEntity.BlockDisplayEntity disp=new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, getWorld());
                disp.setBlockState(p.state==null?Blocks.IRON_BLOCK.getDefaultState():p.state);
                double x=base.x+p.off.getX(), y=base.y+p.off.getY(), z=base.z+p.off.getZ();
                disp.refreshPositionAndAngles(x,y,z,0,0);
                getWorld().spawnEntity(disp);
                displayIds.put(p.off, disp.getUuid());
            }
        }
    }

    private void ensureColliders(){
        if(getWorld().isClient) return;
        for(Part p: parts){
            if(!colliderIds.containsKey(p.off)){
                SlidePartColliderEntity col = ModEntities.SLIDE_PART_COLLIDER.create((ServerWorld)getWorld());
                if(col==null) continue;
                double x=base.x+p.off.getX(), y=base.y+p.off.getY(), z=base.z+p.off.getZ();
                col.refreshPositionAndAngles(x+0.5,y+0.5,z+0.5,0,0);
                double eps=0.025;
                col.setBoundingBox(new Box(x+eps, y, z+eps, x+1-eps, y+1, z+1-eps));
                ((ServerWorld)getWorld()).spawnEntity(col);
                colliderIds.put(p.off, col.getUuid());
            }
        }
    }

    private void updateAll(boolean snap, double vx, double vz){
        if(getWorld().isClient) return;
        for(Part p: parts){
            UUID id=displayIds.get(p.off);
            if(id!=null){
                Entity e=((ServerWorld)getWorld()).getEntity(id);
                if(e instanceof DisplayEntity.BlockDisplayEntity d){
                    double x=base.x+p.off.getX(), y=base.y+p.off.getY(), z=base.z+p.off.getZ();
                    d.setPos(x,y,z);
                }
            }
            UUID cid=colliderIds.get(p.off);
            if(cid!=null){
                Entity e=((ServerWorld)getWorld()).getEntity(cid);
                if(e instanceof SlidePartColliderEntity c){
                    double x=base.x+p.off.getX(), y=base.y+p.off.getY(), z=base.z+p.off.getZ();
                    c.setPos(x+0.5,y+0.5,z+0.5);
                    double eps=0.01;
                    c.setBoundingBox(new Box(x+eps,y,z+eps,x+1-eps,y+1,z+1-eps));
                    c.setStep(vx, vz);
                }
            }
        }
        prune(parts, displayIds); prune(parts, colliderIds);
    }

    private static void prune(List<Part> parts, Map<BlockPos,UUID> map){
        HashSet<BlockPos> keep=new HashSet<>(); for(Part p: parts) keep.add(p.off);
        map.entrySet().removeIf(e->!keep.contains(e.getKey()));
    }

    private void clearDisplays(){
        if(getWorld().isClient) return;
        for(UUID id: displayIds.values()){
            Entity e=((ServerWorld)getWorld()).getEntity(id);
            if(e!=null) e.discard();
        }
        displayIds.clear();
    }

    private void clearColliders(){
        if(getWorld().isClient) return;
        for(UUID id: colliderIds.values()){
            Entity e=((ServerWorld)getWorld()).getEntity(id);
            if(e!=null) e.discard();
        }
        colliderIds.clear();
    }

    private void recalcAABB(){
        if(parts.isEmpty()){
            setBoundingBox(new Box(base.x,base.y,base.z,base.x+1,base.y+1,base.z+1));
            refreshPositionAndAngles(base.x+0.5, base.y, base.z+0.5, 0,0);
            return;
        }
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(Part p: parts){
            double x1=base.x+p.off.getX(), y1=base.y+p.off.getY(), z1=base.z+p.off.getZ();
            if(x1<minX)minX=x1; if(y1<minY)minY=y1; if(z1<minZ)minZ=z1;
            if(x1+1>maxX)maxX=x1+1; if(y1+1>maxY)maxY=y1+1; if(z1+1>maxZ)maxZ=z1+1;
        }
        setBoundingBox(new Box(minX,minY,minZ,maxX,maxY,maxZ));
        refreshPositionAndAngles(base.x+0.5, base.y, base.z+0.5, 0,0);
    }

    private void reifyAndDiscard(){
        if(!(getWorld() instanceof ServerWorld sw)){ discard(); return; }
        for(Part p: parts){
            BlockPos at = new BlockPos((int)Math.floor(base.x+p.off.getX()), (int)Math.floor(base.y+p.off.getY()), (int)Math.floor(base.z+p.off.getZ()));
            sw.setBlockState(at, p.state==null?Blocks.IRON_BLOCK.getDefaultState():p.state, 3);
        }
        clearDisplays();
        clearColliders();
        discard();
    }

    @Override protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt){
        origin=new BlockPos(nbt.getInt("ox"),nbt.getInt("oy"),nbt.getInt("oz"));
        base=new Vec3d(nbt.getDouble("bx"), nbt.getDouble("by"), nbt.getDouble("bz"));
        speed=nbt.getDouble("spd"); stepX=nbt.getInt("sx"); stepZ=nbt.getInt("sz"); remaining=nbt.getInt("rem");
        setPos(base.x+0.5, base.y, base.z+0.5);
        ensureDisplays(); ensureColliders(); updateAll(true,0,0);
    }
    @Override protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt){
        nbt.putInt("ox",origin.getX()); nbt.putInt("oy",origin.getY()); nbt.putInt("oz",origin.getZ());
        nbt.putDouble("bx",base.x); nbt.putDouble("by",base.y); nbt.putDouble("bz",base.z);
        nbt.putDouble("spd",speed); nbt.putInt("sx",stepX); nbt.putInt("sz",stepZ); nbt.putInt("rem",remaining);
    }
}
