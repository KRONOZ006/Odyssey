package net.kronoz.odyssey.systems.cinematics.track;

import net.kronoz.odyssey.systems.cinematics.api.Curves;
import net.kronoz.odyssey.systems.cinematics.api.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CameraTrack {
    private final List<CameraKeyframe> keys = new ArrayList<>();

    public CameraTrack add(CameraKeyframe k){
        keys.add(k);
        keys.sort(Comparator.comparingDouble(CameraKeyframe::timeSec));
        return this;
    }

    public double duration(){
        return keys.isEmpty()?0:keys.getLast().timeSec();
    }

    public CameraPose sample(double t){
        if(keys.isEmpty()) return new CameraPose(Vec3d.ZERO,0,0,0,70f,false,false,false);
        if(t <= keys.getFirst().timeSec()) return resolve(keys.getFirst());
        if(t >= keys.getLast().timeSec())  return resolve(keys.getLast());

        CameraKeyframe a=null,b=null;
        for(int i=0;i<keys.size()-1;i++){
            var k0=keys.get(i);
            var k1=keys.get(i+1);
            if(t>=k0.timeSec() && t<=k1.timeSec()){ a=k0; b=k1; break; }
        }
        if(a==null||b==null) return resolve(keys.getLast());

        double span = b.timeSec() - a.timeSec();
        double lt = (t - a.timeSec()) / (span<=0?1:span);
        double et = (b.easing()!=null?b.easing(): Easing.LINEAR).apply(Math.max(0,Math.min(1,lt)));

        CameraPose pa = resolve(a);
        CameraPose pb = resolve(b);

        Vec3d p = Curves.lerp(pa.position(), pb.position(), et);
        if(b.pose().lockX()) p = new Vec3d(pa.position().x, p.y, p.z);
        if(b.pose().lockY()) p = new Vec3d(p.x, pa.position().y, p.z);
        if(b.pose().lockZ()) p = new Vec3d(p.x, p.y, pa.position().z);

        float yaw   = Curves.lerp(pa.yaw(),   pb.yaw(),   et);
        float pitch = Curves.lerp(pa.pitch(), pb.pitch(), et);
        float roll  = Curves.lerp(pa.roll(),  pb.roll(),  et);
        float fov   = Curves.lerp(pa.fov(),   pb.fov(),   et);

        return new CameraPose(p,yaw,pitch,roll,fov,b.pose().lockX(),b.pose().lockY(),b.pose().lockZ());
    }

    private CameraPose resolve(CameraKeyframe k){
        var base = k.pose();
        if(!k.relativeToPlayer()) return base;

        var mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if(p==null) return base;

        Box box = p.getBoundingBox();
        Vec3d dims = new Vec3d(box.getLengthX(), box.getLengthY(), box.getLengthZ());

        double s = Math.max(1e-3, k.relFactor());
        Vec3d delta = new Vec3d(
                base.position().x * dims.x * s,
                base.position().y * dims.y * s,
                base.position().z * dims.z * s
        );
        Vec3d pos = p.getPos().add(delta);

        return new CameraPose(
                pos,
                base.yaw(),
                base.pitch(),
                base.roll(),
                base.fov(),
                base.lockX(), base.lockY(), base.lockZ()
        );
    }
}
