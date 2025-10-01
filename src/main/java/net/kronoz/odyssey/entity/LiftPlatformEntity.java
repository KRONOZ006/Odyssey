package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class LiftPlatformEntity extends Entity {
    public static class Part { public BlockPos off; public BlockState state; public Part(BlockPos o, BlockState s){ off=o; state=s; } }

    private static final net.minecraft.block.Block STOP_MARKER = Blocks.COPPER_BLOCK;

    private final List<Part> parts = new ArrayList<>();
    private final Map<BlockPos, UUID> displayIds = new HashMap<>();
    private final Map<BlockPos, UUID> colliderIds = new HashMap<>();
    private boolean moving = false;
    private int dir = 0;
    private double speed = 0.006;
    private BlockPos origin = BlockPos.ORIGIN;
    private Vec3d base = Vec3d.ZERO;

    public LiftPlatformEntity(EntityType<? extends Entity> type, World world){ super(type, world); this.noClip=true; }
    @Override protected void initDataTracker(DataTracker.Builder builder){}

    public void configure(BlockPos originBlock, List<Part> p, double spd, int direction){
        origin = originBlock;
        base = new Vec3d(origin.getX(), origin.getY(), origin.getZ());
        setPos(base.x+0.5, base.y, base.z+0.5);
        clearDisplays();
        clearColliders();
        parts.clear();
        parts.addAll(p);
        speed = spd;
        dir = direction;
        recalcAABB();
        ensureDisplays();
        ensureColliders();
        updateVisualsAndColliders(true, 0.0);
        moving = true;
    }

    @Override public void tick(){
        super.tick();
        if(getWorld().isClient) return;
        Vec3d step = moving ? new Vec3d(0, dir*speed, 0) : Vec3d.ZERO;
        if(moving){
            Box unionNext = getBoundingBox().offset(step);
            if(getWorld().isSpaceEmpty(this, unionNext.expand(0.001))){
                base = base.add(step);
                setPos(base.x+0.5, base.y, base.z+0.5);
                recalcAABB();
                updateVisualsAndColliders(false, step.y);
                if(detectStopMarker((ServerWorld)getWorld())) moving = false;
            } else {
                reifyAndDiscard();
            }
        } else {
            updateVisualsAndColliders(false, 0.0);
        }
    }

    private boolean detectStopMarker(ServerWorld sw){
        int[][] dirs = new int[][]{{3,0},{-3,0},{0,3},{0,-3}};
        for(Part p: parts){
            int bx = (int)Math.floor(base.x + p.off.getX());
            int by = (int)Math.floor(base.y + p.off.getY());
            int bz = (int)Math.floor(base.z + p.off.getZ());
            BlockPos here = new BlockPos(bx,by,bz);
            for(int[] d: dirs){
                int dx = d[0], dz = d[1];
                BlockPos mpos = here.add(dx,0,dz);
                if(sw.getBlockState(mpos).isOf(STOP_MARKER)){
                    boolean clear = true;
                    int stepx = Integer.signum(dx), stepz = Integer.signum(dz);
                    for(int i=1;i<Math.max(Math.abs(dx),Math.abs(dz));i++){
                        BlockPos gap = here.add(stepx*i, 0, stepz*i);
                        if(!sw.getBlockState(gap).isAir()){ clear=false; break; }
                    }
                    if(clear) return true;
                }
            }
        }
        return false;
    }

    private void ensureDisplays(){
        if(getWorld().isClient) return;
        for(Part p: parts){
            if(!displayIds.containsKey(p.off)){
                DisplayEntity.BlockDisplayEntity disp = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, getWorld());
                disp.setBlockState(p.state==null?Blocks.IRON_BLOCK.getDefaultState():p.state);
                double x = base.x + p.off.getX();
                double y = base.y + p.off.getY();
                double z = base.z + p.off.getZ();
                disp.refreshPositionAndAngles(x, y, z, 0, 0);
                getWorld().spawnEntity(disp);
                displayIds.put(p.off, disp.getUuid());
            }
        }
    }

    private void ensureColliders(){
        if(getWorld().isClient) return;
        for(Part p: parts){
            if(!colliderIds.containsKey(p.off)){
                net.kronoz.odyssey.entity.LiftPartColliderEntity col = ModEntities.LIFT_PART_COLLIDER.create((ServerWorld)getWorld());
                if(col==null) continue;
                col.setParent(getUuid());
                double x = base.x + p.off.getX();
                double y = base.y + p.off.getY();
                double z = base.z + p.off.getZ();
                col.refreshPositionAndAngles(x+0.5, y, z+0.5, 0, 0);
                col.setBoundingBox(new Box(x, y, z, x+1, y+1, z+1));
                ((ServerWorld)getWorld()).spawnEntity(col);
                colliderIds.put(p.off, col.getUuid());
            }
        }
    }

    private void updateVisualsAndColliders(boolean snap, double stepY){
        if(getWorld().isClient) return;
        for(Part p: parts){
            UUID id = displayIds.get(p.off);
            if(id!=null){
                Entity e = ((ServerWorld)getWorld()).getEntity(id);
                if(e instanceof DisplayEntity.BlockDisplayEntity disp){
                    double x = base.x + p.off.getX();
                    double y = base.y + p.off.getY();
                    double z = base.z + p.off.getZ();
                    disp.setPos(x, y, z);
                }
            }
            UUID cid = colliderIds.get(p.off);
            if(cid!=null){
                Entity e = ((ServerWorld)getWorld()).getEntity(cid);
                if(e instanceof net.kronoz.odyssey.entity.LiftPartColliderEntity col){
                    double x = base.x + p.off.getX();
                    double y = base.y + p.off.getY();
                    double z = base.z + p.off.getZ();
                    col.setPos(x+0.5, y, z+0.5);
                    col.setBoundingBox(new Box(x, y, z, x+1, y+1, z+1));
                    col.setStepY(stepY);
                }
            }
        }
        pruneMissing(parts, displayIds);
        pruneMissing(parts, colliderIds);
    }

    private static <T> void pruneMissing(List<Part> parts, Map<BlockPos,UUID> map){
        Set<BlockPos> keep = new HashSet<>();
        for(Part p: parts) keep.add(p.off);
        Iterator<Map.Entry<BlockPos,UUID>> it = map.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<BlockPos,UUID> en = it.next();
            if(!keep.contains(en.getKey())) it.remove();
        }
    }

    private void reifyAndDiscard(){
        if(!(getWorld() instanceof ServerWorld sw)) { discard(); return; }
        for(Part p: parts){
            BlockPos at = new BlockPos((int)Math.floor(base.x + p.off.getX()), (int)Math.floor(base.y + p.off.getY()), (int)Math.floor(base.z + p.off.getZ()));
            sw.setBlockState(at, p.state==null?Blocks.IRON_BLOCK.getDefaultState():p.state, 3);
        }
        clearDisplays();
        clearColliders();
        discard();
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
            setBoundingBox(new Box(base.x, base.y, base.z, base.x+1, base.y+1, base.z+1));
            refreshPositionAndAngles(base.x+0.5, base.y, base.z+0.5, 0, 0);
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
        refreshPositionAndAngles(base.x+0.5, base.y, base.z+0.5, 0, 0);
    }

    @Override protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt){
        moving = nbt.getBoolean("mv");
        dir = nbt.getInt("dir");
        speed = nbt.getDouble("spd");
        origin = new BlockPos(nbt.getInt("ox"), nbt.getInt("oy"), nbt.getInt("oz"));
        base = new Vec3d(nbt.getDouble("bx"), nbt.getDouble("by"), nbt.getDouble("bz"));
        setPos(base.x+0.5, base.y, base.z+0.5);
        recalcAABB();
        ensureDisplays();
        ensureColliders();
        updateVisualsAndColliders(true, 0.0);
    }

    @Override protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt){
        nbt.putBoolean("mv", moving);
        nbt.putInt("dir", dir);
        nbt.putDouble("spd", speed);
        nbt.putInt("ox", origin.getX()); nbt.putInt("oy", origin.getY()); nbt.putInt("oz", origin.getZ());
        nbt.putDouble("bx", base.x); nbt.putDouble("by", base.y); nbt.putDouble("bz", base.z);
    }
}
