package net.kronoz.odyssey.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public class SlidePartColliderEntity extends Entity {
    private double stepX=0, stepZ=0;
    public SlidePartColliderEntity(EntityType<? extends Entity> type, World world){ super(type, world); this.noClip=true; }
    @Override protected void initDataTracker(DataTracker.Builder builder){}

    public void setStep(double vx, double vz){ this.stepX=vx; this.stepZ=vz; }

    @Override public void tick(){
        super.tick();
        if(getWorld().isClient) return;
        Box b=getBoundingBox();
        double topY=b.maxY;

        List<Entity> list=getWorld().getOtherEntities(this, b.expand(0.05, 1.8, 0.05));
        for(Entity e : list){
            if(!(e instanceof net.minecraft.entity.player.PlayerEntity)) continue;
            Box pb=e.getBoundingBox();
            if(!pb.intersects(b)) continue;

            double feet=pb.minY;
            if(feet >= topY - 0.05){
                double nx=e.getX()+stepX, ny=e.getY(), nz=e.getZ()+stepZ; // carry with door
                if(e instanceof ServerPlayerEntity sp) sp.networkHandler.requestTeleport(nx,ny,nz,sp.getYaw(),sp.getPitch()); else e.requestTeleport(nx,ny,nz);
                continue;
            }

            double oxp=b.maxX-pb.minX, oxn=pb.maxX-b.minX, ozp=b.maxZ-pb.minZ, ozn=b.maxZ-b.minZ;
            double ox=Math.min(oxp,oxn), oz=Math.min(ozp,ozn);
            double nx=e.getX(), ny=e.getY(), nz=e.getZ(), pad=0.003;
            if(ox<oz){ if(oxp<oxn) nx+=oxp+pad; else nx-=oxn+pad; }
            else { if(ozp<ozn) nz+=ozp+pad; else nz-=ozn+pad; }

            if(e instanceof ServerPlayerEntity sp) sp.networkHandler.requestTeleport(nx,ny,nz,sp.getYaw(),sp.getPitch()); else e.requestTeleport(nx,ny,nz);
        }
    }

    @Override protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt){}
    @Override protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt){}
}
