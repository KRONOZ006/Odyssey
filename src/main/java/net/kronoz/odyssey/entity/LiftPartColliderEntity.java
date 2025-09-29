package net.kronoz.odyssey.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class LiftPartColliderEntity extends Entity {
    private UUID parentId;
    private double stepY = 0.0;

    public LiftPartColliderEntity(EntityType<? extends Entity> type, World world){ super(type, world); this.noClip=true; }
    @Override protected void initDataTracker(DataTracker.Builder builder){}

    public void setParent(UUID id){ this.parentId = id; }
    public void setStepY(double dy){ this.stepY = dy; }

    @Override public void tick(){
        super.tick();
        if(getWorld().isClient) return;
        Box b = getBoundingBox();
        Box search = b.expand(0.05, 1.6, 0.05);
        List<Entity> list = getWorld().getOtherEntities(this, search);
        double top = b.maxY;
        for(Entity e: list){
            if(!(e instanceof PlayerEntity)) continue;
            Box pb = e.getBoundingBox();
            boolean horiz = pb.maxX > b.minX && pb.minX < b.maxX && pb.maxZ > b.minZ && pb.minZ < b.maxZ;
            double feet = pb.minY;
            boolean onTop = horiz && feet >= top - 0.7 && feet <= top + 0.25;
            if(onTop){
                double targetFeet = top + 0.002;
                double adjust = targetFeet - feet;
                double moveY = stepY + Math.max(adjust,0);
                if(stepY < 0) moveY = Math.max(stepY + adjust, stepY);
                double nx = e.getX(), ny = e.getY(), nz = e.getZ();
                if(moveY != 0) ny += moveY;
                if(e instanceof ServerPlayerEntity sp){
                    sp.networkHandler.requestTeleport(nx, ny, nz, sp.getYaw(), sp.getPitch());
                    sp.fallDistance = 0f;
                    sp.setOnGround(true);
                } else {
                    e.requestTeleport(nx, ny, nz);
                    e.fallDistance = 0f;
                    ((PlayerEntity)e).setOnGround(true);
                }
                Vec3d v = e.getVelocity();
                double newVy = stepY >= 0 ? Math.max(v.y, stepY) : Math.min(v.y, stepY);
                e.setVelocity(v.x, newVy, v.z);
                e.velocityModified = true;
                continue;
            }
            if(pb.intersects(b)){
                double pushUp = top - feet;
                if(pushUp > 0 && pushUp < 1.6){
                    double nx=e.getX(), ny=e.getY()+pushUp+0.002, nz=e.getZ();
                    if(e instanceof ServerPlayerEntity sp){
                        sp.networkHandler.requestTeleport(nx, ny, nz, sp.getYaw(), sp.getPitch());
                        sp.fallDistance=0f; sp.setOnGround(true);
                    } else {
                        e.requestTeleport(nx, ny, nz);
                        e.fallDistance=0f; ((PlayerEntity)e).setOnGround(true);
                    }
                    Vec3d v = e.getVelocity();
                    double newVy = stepY >= 0 ? Math.max(v.y, stepY) : Math.min(v.y, stepY);
                    e.setVelocity(v.x, newVy, v.z);
                    e.velocityModified = true;
                }
            }
        }
    }

    @Override protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt){
        if(nbt.containsUuid("pid")) parentId = nbt.getUuid("pid");
        stepY = nbt.getDouble("dy");
    }
    @Override protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt){
        if(parentId!=null) nbt.putUuid("pid", parentId);
        nbt.putDouble("dy", stepY);
    }
}
