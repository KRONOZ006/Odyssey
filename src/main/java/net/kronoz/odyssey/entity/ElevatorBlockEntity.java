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
import net.minecraft.world.World;

import java.util.*;

public class ElevatorBlockEntity extends BlockEntity {
    public static class Part { public BlockPos off; public BlockState state; public Part(BlockPos o, BlockState s){ off=o; state=s; } }

    private final List<Part> parts = new ArrayList<>();
    private UUID platformId = null;
    private double speed = 0.006;
    private BlockPos origin = BlockPos.ORIGIN;

    public ElevatorBlockEntity(BlockPos pos, BlockState state){ super(ModBlockEntities.ELEVATOR_BE,pos,state); }
    private World w(){ return Objects.requireNonNull(getWorld()); }
    public static void serverTick(World w, BlockPos pos, BlockState st, ElevatorBlockEntity be){}

    public void start(ServerWorld sw, int dir){
        origin = this.pos;
        scan(sw);
        if(parts.isEmpty()) return;
        removeBlocks(sw);
        LiftPlatformEntity p = ensurePlatform(sw);
        if(p==null) return;
        p.configure(origin, copyParts(), speed, dir);
        markDirty();
    }

    private List<LiftPlatformEntity.Part> copyParts(){
        List<LiftPlatformEntity.Part> out = new ArrayList<>();
        for(Part p: parts) out.add(new LiftPlatformEntity.Part(p.off, p.state));
        return out;
    }

    private void scan(ServerWorld sw){
        parts.clear();
        HashSet<BlockPos> vis = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(this.pos); vis.add(this.pos);
        Block self = w().getBlockState(this.pos).getBlock();
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
        Block self = w().getBlockState(this.pos).getBlock();
        for(Part p: parts){
            BlockPos at = origin.add(p.off);
            if(sw.getBlockState(at).getBlock()==self){
                sw.setBlockState(at, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    private LiftPlatformEntity ensurePlatform(ServerWorld sw){
        if(platformId==null || !(sw.getEntity(platformId) instanceof LiftPlatformEntity)){
            LiftPlatformEntity e = ModEntities.LIFT_PLATFORM.create(sw);
            if(e==null) return null;
            e.refreshPositionAndAngles(origin.getX()+0.5, origin.getY(), origin.getZ()+0.5, 0,0);
            sw.spawnEntity(e);
            platformId = e.getUuid();
            return e;
        }
        return (LiftPlatformEntity) sw.getEntity(platformId);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup){
        NbtList list = new NbtList();
        for(Part p: parts){
            NbtCompound t=new NbtCompound();
            t.putInt("x",p.off.getX()); t.putInt("y",p.off.getY()); t.putInt("z",p.off.getZ());
            t.put("state", NbtHelper.fromBlockState(p.state));
            list.add(t);
        }
        nbt.put("parts",list);
        if(platformId!=null) nbt.putUuid("pid",platformId);
        nbt.putDouble("spd",speed);
        nbt.putInt("ox", origin.getX()); nbt.putInt("oy", origin.getY()); nbt.putInt("oz", origin.getZ());
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup){
        parts.clear();
        if(nbt.contains("parts", NbtElement.LIST_TYPE)){
            NbtList list=nbt.getList("parts", NbtElement.COMPOUND_TYPE);
            for(int i=0;i<list.size();i++){
                NbtCompound t=list.getCompound(i);
                BlockPos off = new BlockPos(t.getInt("x"),t.getInt("y"),t.getInt("z"));
                BlockState st = NbtHelper.toBlockState(lookup.getWrapperOrThrow(RegistryKeys.BLOCK), t.getCompound("state"));
                parts.add(new Part(off, st));
            }
        }
        if(nbt.containsUuid("pid")) platformId=nbt.getUuid("pid");
        if(nbt.contains("spd")) speed=nbt.getDouble("spd");
        int ox = nbt.contains("ox")?nbt.getInt("ox"):this.pos.getX();
        int oy = nbt.contains("oy")?nbt.getInt("oy"):this.pos.getY();
        int oz = nbt.contains("oz")?nbt.getInt("oz"):this.pos.getZ();
        origin = new BlockPos(ox,oy,oz);
    }
}
