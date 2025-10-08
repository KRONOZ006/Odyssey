package net.kronoz.odyssey.entity.apostasy;

import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.*;

public class ApostasyModel extends GeoModel<ApostasyEntity> {


    private final List<String> GUN_BONES = buildGunList();
    private final Map<String, Float> smoothedYaw = new HashMap<>();
    private final Map<String, Float> smoothedPitch = new HashMap<>();
    private static final boolean INVERT_GUN_YAW = true;
    private static final boolean INVERT_GUN_PITCH = true;
    private static final float SMOOTH = 0.15f;

    private static List<String> buildGunList() {
        ArrayList<String> list = new ArrayList<>();
        for (int i = 1; i <= 8;  i++)  list.add("ring1gun" + i);
        for (int i = 1; i <= 12; i++) list.add("ring2gun" + i);
        for (int i = 1; i <= 8;  i++)  list.add("ring3gun" + i);
        return list;
    }

    @Override
    public void setCustomAnimations(ApostasyEntity e, long id, AnimationState<ApostasyEntity> state) {
        super.setCustomAnimations(e, id, state);

        ApostasyRingAnimator.Result r = e.getRingPose();
        if (r != null) {
            GeoBone b1 = getAnimationProcessor().getBone("ring_1");
            GeoBone b2 = getAnimationProcessor().getBone("ring_2");
            GeoBone b3 = getAnimationProcessor().getBone("ring_3");
            if (b1 != null) { b1.setRotX(r.r1x); b1.setRotY(r.r1y); b1.setRotZ(r.r1z); }
            if (b2 != null) { b2.setRotX(r.r2x); b2.setRotY(r.r2y); b2.setRotZ(r.r2z); }
            if (b3 != null) { b3.setRotX(r.r3x); b3.setRotY(r.r3y); b3.setRotZ(r.r3z); }
        }

        var player = e.getWorld().getClosestPlayer(e, 96.0);
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

        float targetYaw = (float)(Math.atan2(dz, dx) + Math.toRadians(90));
        float targetPitch = (float)(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

        for (String name : GUN_BONES) {
            GeoBone b = getAnimationProcessor().getBone(name);
            if (b == null) continue;

            float cy = smoothedYaw.getOrDefault(name, 0f);
            float cp = smoothedPitch.getOrDefault(name, 0f);

            float ny = lerpAngleRad(cy, targetYaw, SMOOTH);
            float np = lerpAngleRad(cp, targetPitch, SMOOTH);

            smoothedYaw.put(name, ny);
            smoothedPitch.put(name, np);

            b.setRotY(INVERT_GUN_YAW ? -ny : ny);
            b.setRotX(INVERT_GUN_PITCH ? -np : np);
        }
    }

    private static float lerpAngleRad(float a, float b, float t) {
        float d = wrapToPi(b - a);
        return a + d * t;
    }

    private static float wrapToPi(float r) {
        while (r <= -MathHelper.PI) r += MathHelper.TAU;
        while (r >   MathHelper.PI) r -= MathHelper.TAU;
        return r;
    }

    @Override
    public net.minecraft.util.Identifier getModelResource(ApostasyEntity animatable) {
        return net.minecraft.util.Identifier.of("odyssey","geo/entity/apostasy.geo.json");
    }

    @Override
    public net.minecraft.util.Identifier getTextureResource(ApostasyEntity animatable) {
        return net.minecraft.util.Identifier.of("odyssey","textures/entity/apostasy.png");
    }

    @Override
    public net.minecraft.util.Identifier getAnimationResource(ApostasyEntity animatable) {
        return net.minecraft.util.Identifier.of("odyssey","animations/entity/apostasy.animation.json");
    }
}
