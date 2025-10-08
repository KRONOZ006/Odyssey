package net.kronoz.odyssey.entity.apostasy;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.GeoModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ApostasyModel extends GeoModel<ApostasyEntity> {
    @Override public Identifier getModelResource(ApostasyEntity e){ return Identifier.of(Odyssey.MODID,"geo/entity/apostasy.geo.json"); }
    @Override public Identifier getTextureResource(ApostasyEntity e){ return Identifier.of(Odyssey.MODID,"textures/entity/apostasy.png"); }
    @Override public Identifier getAnimationResource(ApostasyEntity e){ return Identifier.of(Odyssey.MODID,"animations/entity/apostasy.animation.json"); }
    private final String[] aimBones = new String[] { "mainhead" };
    @Override
    public void setCustomAnimations(ApostasyEntity e, long id, AnimationState<ApostasyEntity> s) {
        GeoBone ring1 = getAnimationProcessor().getBone("ring_1");
        GeoBone ring2 = getAnimationProcessor().getBone("ring_2");
        GeoBone ring3 = getAnimationProcessor().getBone("ring_3");

        ApostasyRingAnimator.Result r = ApostasyRingAnimator.sample(e.getId(), e.age);
        if (ring1 != null) { ring1.setRotX(r.r1x); ring1.setRotY(r.r1y); ring1.setRotZ(r.r1z); }
        if (ring2 != null) { ring2.setRotX(r.r2x); ring2.setRotY(r.r2y); ring2.setRotZ(r.r2z); }
        if (ring3 != null) { ring3.setRotX(r.r3x); ring3.setRotY(r.r3y); ring3.setRotZ(r.r3z); }

        aimGunsAtPlayer(e, "ring1gun", 8);
        aimGunsAtPlayer(e, "ring2gun", 12);
        aimGunsAtPlayer(e, "ring3gun", 8);


            var player = e.getWorld().getClosestPlayer(e, 64.0);
            if (player == null || player.isSpectator()) return;

            double ex = e.getX();
            double ey = e.getY() + e.getStandingEyeHeight();
            double ez = e.getZ();

            double tx = player.getX();
            double ty = player.getEyeY();
            double tz = player.getZ();

            double dx = tx - ex;
            double dy = ty - ey;
            double dz = tz - ez;

            float yaw  = (float)(Math.atan2(dz, dx) + Math.toRadians(-90));
            float pitch= (float)(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

            for (String name : aimBones) {
                GeoBone b = getAnimationProcessor().getBone(name);
                if (b == null) continue;

                b.setRotY(-yaw);
                b.setRotX(-pitch);
            }


    }

    private void aimGunsAtPlayer(ApostasyEntity e, String prefix, int count) {
        PlayerEntity p = e.getWorld() != null ? e.getWorld().getClosestPlayer(e, 96.0) : null;
        if (p == null || p.isSpectator() || p.isCreative()) {
            for (int i = 1; i <= count; i++) {
                GeoBone b = getAnimationProcessor().getBone(prefix + i);
                if (b != null) { b.setRotX(0f); b.setRotY(0f); }
            }
            return;
        }

        Vec3d from = e.getEyePos();
        Vec3d to   = p.getEyePos();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx*dx + dz*dz);

        float targetYawDeg = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float localYawDeg  = MathHelper.wrapDegrees(targetYawDeg - e.getYaw());
        float yawRad       = (float)Math.toRadians(localYawDeg);
        float pitchRad     = (float)Math.atan2(dy, flat);

        float invYaw   = -yawRad;
        float invPitch = pitchRad;

        for (int i = 1; i <= count; i++) {
            GeoBone b = getAnimationProcessor().getBone(prefix + i);
            if (b != null) {
                b.setRotY(invYaw);
                b.setRotX(-invPitch);
            }
        }
    }

}
