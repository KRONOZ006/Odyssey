package net.kronoz.odyssey.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kronoz.odyssey.entity.LiftPlatformEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientElevatorAssist {

    private static final class S {
        double cx,cy,cz,vy; int w,l,h; boolean init=false;
    }
    private static final Map<UUID,S> SMOOTH = new HashMap<>();

    public static void init(){
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerEntity p = MinecraftClient.getInstance().player;
            if(p==null || p.getWorld()==null) return;

            var world = p.getWorld();
            var nearby = world.getEntitiesByClass(
                    LiftPlatformEntity.class,
                    p.getBoundingBox().expand(48, 24, 48),
                    e -> true
            );

            for(var lp : nearby){
                var dt = lp.getDataTracker();
                float rCX = dt.get(LiftPlatformEntity.CX);
                float rCY = dt.get(LiftPlatformEntity.CY);
                float rCZ = dt.get(LiftPlatformEntity.CZ);
                float rVY = dt.get(LiftPlatformEntity.VY);
                int rW = dt.get(LiftPlatformEntity.DW);
                int rL = dt.get(LiftPlatformEntity.DL);
                int rH = dt.get(LiftPlatformEntity.DH);

                S s = SMOOTH.computeIfAbsent(lp.getUuid(), k -> new S());
                if(!s.init){
                    s.cx=rCX; s.cy=rCY; s.cz=rCZ; s.vy=rVY; s.w=rW; s.l=rL; s.h=rH; s.init=true;
                }else{
                    double a=0.8;
                    s.cx = a*s.cx + (1-a)*rCX;
                    s.cy = a*s.cy + (1-a)*rCY;
                    s.cz = a*s.cz + (1-a)*rCZ;
                    s.vy = a*s.vy + (1-a)*rVY;
                    s.w=rW; s.l=rL; s.h=rH;
                }

                double halfW = s.w/2.0, halfL = s.l/2.0;
                Box top = new Box(s.cx-halfW, s.cy+s.h-1, s.cz-halfL, s.cx+halfW, s.cy+s.h, s.cz+halfL).expand(0.01,0.02,0.01);

                var pb = p.getBoundingBox();
                boolean horiz = pb.maxX > top.minX && pb.minX < top.maxX && pb.maxZ > top.minZ && pb.minZ < top.maxZ;
                double feet = pb.minY;
                boolean onTop = horiz && feet >= top.minY-0.5 && feet <= top.maxY+0.25;

                if(onTop){
                    // exact surface (no +0.001)
                    double targetFeet = top.maxY;
                    double gap = targetFeet - feet;

                    final double SNAP_TOL = 0.001;   // snap if within ~1/20th block
                    final double LIFT_CAP = 1.00;    // max up correction per tick
                    final double DROP_CAP = 1.00;    // max down correction per tick

                    if(gap > 0){
                        if(gap <= SNAP_TOL){
                            // snap exactly to the surface (perfect height)
                            p.setPosition(p.getX(), p.getY() + gap, p.getZ());
                        }else{
                            // still approach smoothly, capped
                            double lift = Math.min(gap * 0.6, LIFT_CAP);
                            p.setPosition(p.getX(), p.getY() + lift, p.getZ());
                        }
                    }else if(gap < 0){
                        // if we’re hovering above, gently settle
                        double drop = Math.min(-gap * 0.5, DROP_CAP);
                        p.setPosition(p.getX(), p.getY() - drop, p.getZ());
                    }

                    // blend vertical velocity with platform vy
                    double curVy = p.getVelocity().y;
                    double nvy = Math.max(s.vy, curVy*0.85);
                    p.setVelocity(p.getVelocity().x, nvy, p.getVelocity().z);
                    p.fallDistance = 0f;
                }
            }
        });
    }
}
