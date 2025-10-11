package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.init.ModBlockEntities;
import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

public class SlidingDoorBlockEntity extends BlockEntity {
    public static class Part { public BlockPos off; public BlockState state; public Part(BlockPos o, BlockState s){ off=o; state=s; } }

    private final List<Part> parts = new ArrayList<>();
    private UUID platformId = null;

    private BlockPos home = BlockPos.ORIGIN;
    private BlockPos lastOrigin = BlockPos.ORIGIN;
    private Direction axis = Direction.EAST;
    private int sideSign = +1;
    private double speed = 0.03;
    private boolean isOpen = false;

    public SlidingDoorBlockEntity(BlockPos pos, BlockState state){ super(ModBlockEntities.SLIDING_DOOR_BE,pos,state); }
    public static void serverTick(World w, BlockPos p, BlockState s, SlidingDoorBlockEntity be){}

    private World w(){ return Objects.requireNonNull(getWorld()); }

    public void toggle(ServerWorld sw, Direction facing){
        if(home.equals(BlockPos.ORIGIN)) home = this.pos;
        scan(sw);
        if(parts.isEmpty()) return;

        axis = facing.rotateYClockwise();

        removeBlocks(sw);

        SlidePlatformEntity e = ensure(sw);
        if(e == null) return;

        int dx = axis.getAxis()==Direction.Axis.X ? axis.getOffsetX() * (isOpen ? -sideSign : sideSign) : 0;
        int dz = axis.getAxis()==Direction.Axis.Z ? axis.getOffsetZ() * (isOpen ? -sideSign : sideSign) : 0;

        BlockPos runOrigin = isOpen ? lastOrigin : this.pos;

        if(!isOpen){
            e.configureHorizontalInfinite(runOrigin, copy(), speed, dx, dz);
        }else{
            e.configureHorizontalToTarget(runOrigin, copy(), speed, dx, dz, home);
        }

        isOpen = !isOpen;
        lastOrigin = runOrigin;

        markDirty();
    }

    private List<SlidePlatformEntity.Part> copy(){
        List<SlidePlatformEntity.Part> out=new ArrayList<>();
        for(Part p: parts) out.add(new SlidePlatformEntity.Part(p.off,p.state));
        return out;
    }

    private void scan(ServerWorld sw){
        parts.clear();
        HashSet<BlockPos> vis=new HashSet<>();
        ArrayDeque<BlockPos> q=new ArrayDeque<>();
        q.add(this.pos); vis.add(this.pos);
        Block self=w().getBlockState(this.pos).getBlock();
        while(!q.isEmpty()){
            BlockPos c=q.pollFirst();
            BlockState s=sw.getBlockState(c);
            if(s.getBlock()!=self) continue;
            parts.add(new Part(c.subtract(this.pos), s));
            for(BlockPos n: new BlockPos[]{c.north(),c.south(),c.east(),c.west(),c.up(),c.down()}){
                if(!vis.contains(n) && sw.getBlockState(n).getBlock()==self){ vis.add(n); q.add(n); }
            }
        }
    }

    private void removeBlocks(ServerWorld sw){
        Block self=w().getBlockState(this.pos).getBlock();
        for(Part p: parts){
            BlockPos at=this.pos.add(p.off);
            if(sw.getBlockState(at).getBlock()==self) sw.setBlockState(at, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private SlidePlatformEntity ensure(ServerWorld sw){
        if(platformId==null || !(sw.getEntity(platformId) instanceof SlidePlatformEntity)){
            SlidePlatformEntity e = ModEntities.SLIDE_PLATFORM.create(sw);
            if(e==null) return null;
            e.refreshPositionAndAngles(this.pos.getX()+0.5, this.pos.getY(), this.pos.getZ()+0.5, 0, 0);
            sw.spawnEntity(e);
            platformId=e.getUuid();
            return e;
        }
        return (SlidePlatformEntity) sw.getEntity(platformId);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup l){
        NbtList list=new NbtList();
        for(Part p: parts){
            NbtCompound t=new NbtCompound();
            t.putInt("x",p.off.getX()); t.putInt("y",p.off.getY()); t.putInt("z",p.off.getZ());
            t.put("state", NbtHelper.fromBlockState(p.state));
            list.add(t);
        }
        nbt.put("parts",list);
        if(platformId!=null) nbt.putUuid("pid",platformId);

        nbt.putInt("homeX",home.getX()); nbt.putInt("homeY",home.getY()); nbt.putInt("homeZ",home.getZ());
        nbt.putInt("lastX",lastOrigin.getX()); nbt.putInt("lastY",lastOrigin.getY()); nbt.putInt("lastZ",lastOrigin.getZ());
        nbt.putInt("axisH", axis.getHorizontal());
        nbt.putInt("sideSign", sideSign);
        nbt.putDouble("speed", speed);
        nbt.putBoolean("isOpen", isOpen);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup l){
        parts.clear();
        if(nbt.contains("parts", NbtElement.LIST_TYPE)){
            NbtList list=nbt.getList("parts", NbtElement.COMPOUND_TYPE);
            for(int i=0;i<list.size();i++){
                NbtCompound t=list.getCompound(i);
                BlockPos off=new BlockPos(t.getInt("x"),t.getInt("y"),t.getInt("z"));
                BlockState st=NbtHelper.toBlockState(l.getWrapperOrThrow(RegistryKeys.BLOCK), t.getCompound("state"));
                parts.add(new Part(off,st));
            }
        }
        platformId = nbt.containsUuid("pid") ? nbt.getUuid("pid") : null;

        home = new BlockPos(nbt.getInt("homeX"), nbt.getInt("homeY"), nbt.getInt("homeZ"));
        lastOrigin = new BlockPos(nbt.getInt("lastX"), nbt.getInt("lastY"), nbt.getInt("lastZ"));
        axis = Direction.fromHorizontal(nbt.getInt("axisH"));
        sideSign = nbt.contains("sideSign") ? nbt.getInt("sideSign") : +1;
        speed = nbt.contains("speed") ? nbt.getDouble("speed") : 0.03;
        isOpen = nbt.getBoolean("isOpen");
    }
}
