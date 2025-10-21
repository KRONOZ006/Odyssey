package net.kronoz.odyssey.entity.apostasy;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ApostasyModel extends GeoModel<ApostasyEntity> {

    private static float wrapRad(float a) {
        a %= (float)(Math.PI * 2.0);
        if (a <= -Math.PI) a += (float)(Math.PI * 2.0);
        if (a >   Math.PI) a -= (float)(Math.PI * 2.0);
        return a;
    }

    private static float lerpAngleRad(float a, float b, float t) {
        float d = wrapRad(b - a);
        return a + d * t;
    }

    @Override
    public void setCustomAnimations(ApostasyEntity entity, long instanceId, AnimationState<ApostasyEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);

        final float tickDelta = state.getPartialTick();

        float t = (entity.age + tickDelta) * 0.06f;
        long seed = entity.getUuid().getMostSignificantBits() ^ entity.getUuid().getLeastSignificantBits();
        GeoBone r1 = getAnimationProcessor().getBone("ring_1");
        GeoBone r2 = getAnimationProcessor().getBone("ring_2");
        GeoBone r3 = getAnimationProcessor().getBone("ring_3");
        if (r1 != null) { var a = ApostasyRingAnimator.angles(seed,"ring_1",t);       r1.setRotX(a.x); r1.setRotY(a.y); r1.setRotZ(a.z); }
        if (r2 != null) { var a = ApostasyRingAnimator.angles(seed,"ring_2",t*1.12f); r2.setRotX(a.x); r2.setRotY(a.y); r2.setRotZ(a.z); }
        if (r3 != null) { var a = ApostasyRingAnimator.angles(seed,"ring_3",t*0.91f); r3.setRotX(a.x); r3.setRotY(a.y); r3.setRotZ(a.z); }

        GeoBone head = getAnimationProcessor().getBone("mainhead");
        if (head == null || entity == null || entity.getWorld() == null) return;

        var world = entity.getWorld();
        var player = world.getClosestPlayer(entity, 64.0);
        if (player == null) {
            head.setRotZ(0);
            return;
        }

        double ex = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double ey = MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) + entity.getStandingEyeHeight();
        double ez = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        double px = MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double py = MathHelper.lerp(tickDelta, player.prevY, player.getY()) + player.getStandingEyeHeight();
        double pz = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

        Vec3d d = new Vec3d(px - ex, py - ey, pz - ez);

        float targetYaw  = (float)Math.atan2(d.x, d.z);
        float targetPitch= (float)-Math.atan2(d.y, Math.sqrt(d.x*d.x + d.z*d.z));


        targetPitch = -targetPitch;

        float curY = head.getRotY();
        float curX = head.getRotX();
        float smooth = 0.18f;

        float newY = lerpAngleRad(curY, targetYaw,   smooth);
        float newX = lerpAngleRad(curX, targetPitch, smooth);

        float maxPitch = (float)Math.toRadians(45);
        newX = MathHelper.clamp(newX, -maxPitch, maxPitch);

        head.setRotY(newY);
        head.setRotX(newX);
        head.setRotZ(0);
    }

    @Override public Identifier getModelResource(ApostasyEntity a){ return Identifier.of("odyssey","geo/entity/apostasy.geo.json"); }
    @Override public Identifier getTextureResource(ApostasyEntity a){ return Identifier.of("odyssey","textures/entity/apostasy.png"); }
    @Override public Identifier getAnimationResource(ApostasyEntity a){ return Identifier.of("odyssey","animations/entity/apostasy.animation.json"); }
}