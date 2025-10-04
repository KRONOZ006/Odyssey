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

    @Override
    public void tick(){
        super.tick();
        if(getWorld().isClient) return;

        Box b = getBoundingBox();
        double topY = b.maxY;

        List<Entity> list = getWorld().getOtherEntities(this, b.expand(0.05, 1.8, 0.05));
        for(Entity e : list){
            if(!(e instanceof net.minecraft.entity.player.PlayerEntity)) continue;

            Box pb = e.getBoundingBox();
            if(!pb.intersects(b)) continue;

            double feet = pb.minY;

            // 1) si le joueur est sur le dessus ou quasi → aucune poussée latérale
            if(feet >= topY - 0.001){
                // 2) filet anti-traverse: si légèrement en dessous du top, relève jusqu’à 0.2 max
                double gap = topY - feet;
                if(gap > 0 && gap <= 0.30){
                    double nx = e.getX(), ny = e.getY() + Math.min(gap, 0.20), nz = e.getZ();
                    if(e instanceof net.minecraft.server.network.ServerPlayerEntity sp)
                        sp.networkHandler.requestTeleport(nx, ny, nz, sp.getYaw(), sp.getPitch());
                    else
                        e.requestTeleport(nx, ny, nz);
                }
                continue;
            }

            // 3) résolution latérale neutre (sans biais est)
            double overlapXPos = b.maxX - pb.minX;
            double overlapXNeg = pb.maxX - b.minX;
            double overlapZPos = b.maxZ - pb.minZ;
            double overlapZNeg = pb.maxZ - b.minZ;

            double overlapX = Math.min(overlapXPos, overlapXNeg);
            double overlapZ = Math.min(overlapZPos, overlapZNeg);

            double nx = e.getX(), ny = e.getY(), nz = e.getZ();
            double PAD = 0.001;

            if(overlapX < overlapZ){
                if(overlapXPos < overlapXNeg) nx += overlapXPos + PAD;
                else nx -= overlapXNeg + PAD;
            }else{
                if(overlapZPos < overlapZNeg) nz += overlapZPos + PAD;
                else nz -= overlapZNeg + PAD;
            }

            if(e instanceof net.minecraft.server.network.ServerPlayerEntity sp)
                sp.networkHandler.requestTeleport(nx, ny, nz, sp.getYaw(), sp.getPitch());
            else
                e.requestTeleport(nx, ny, nz);
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
