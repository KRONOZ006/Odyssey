package net.kronoz.odyssey.entity.sentinel;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.sentinel.SentinelEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class SentinelModel extends GeoModel<SentinelEntity> {
    @Override
    public Identifier getModelResource(SentinelEntity a) { return Identifier.of(Odyssey.MODID, "geo/entity/sentinel.geo.json"); }
    @Override
    public Identifier getTextureResource(SentinelEntity a) { return Identifier.of(Odyssey.MODID, "textures/entity/sentinel.png"); }
    @Override
    public Identifier getAnimationResource(SentinelEntity a) { return Identifier.of(Odyssey.MODID, "animations/entity/sentinel.animation.json"); }


    @Override
    public void setCustomAnimations(SentinelEntity anim, long instanceId, AnimationState<SentinelEntity> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        GeoBone eye  = getAnimationProcessor().getBone("eye");
        GeoBone full = getAnimationProcessor().getBone("full");

        if (head != null) {
            head.setRotY(anim.getDesiredHeadYaw());
            head.setRotX(anim.getDesiredHeadPitch());
        }
        if (eye != null) {
            eye.setRotY(0f);
            eye.setRotX(0f);
        }

        if (full != null) {
            Vec3d v = anim.getVelocity();
            double speed = v.length();
            double speedThresh = 0.03;
            float maxTilt = (float)Math.toRadians(15.0);

            float curPitch = full.getRotX();
            float curRoll  = full.getRotZ();

            if (speed > speedThresh) {
                Vec3d fwd = Vec3d.fromPolar(0, anim.getYaw()).normalize();
                Vec3d right = new Vec3d(-fwd.z, 0, fwd.x).normalize();
                double localZ = v.dotProduct(fwd);
                double localX = v.dotProduct(right);

                float targetPitch = (float)(-maxTilt * clamp01(Math.abs(localZ)) * Math.signum(localZ));
                float targetRoll  = (float)( maxTilt * clamp01(Math.abs(localX)) * Math.signum(localX));

                float s = 0.12f;
                full.setRotX(curPitch + (targetPitch - curPitch) * s);
                full.setRotZ(curRoll  + (targetRoll  - curRoll)  * s);
            } else {
                float s = 0.12f;
                full.setRotX(curPitch + (0f - curPitch) * s);
                full.setRotZ(curRoll  + (0f - curRoll)  * s);
            }
        }
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
