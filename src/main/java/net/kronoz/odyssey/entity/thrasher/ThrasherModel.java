package net.kronoz.odyssey.entity.thrasher;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ThrasherModel extends GeoModel<ThrasherEntity> {
    @Override public Identifier getModelResource(ThrasherEntity e){ return Identifier.of(Odyssey.MODID,"geo/entity/thrasher.geo.json"); }
    @Override public Identifier getTextureResource(ThrasherEntity e){ return Identifier.of(Odyssey.MODID,"textures/entity/thrasher.png"); }
    @Override public Identifier getAnimationResource(ThrasherEntity e){ return Identifier.of(Odyssey.MODID,"animations/entity/thrasher.animation.json"); }

    @Override
    public void setCustomAnimations(ThrasherEntity e, long id, AnimationState<ThrasherEntity> s) {
        GeoBone head = getAnimationProcessor().getBone("head");
        GeoBone eye  = getAnimationProcessor().getBone("eye");

        GeoBone seatBone = getAnimationProcessor().getBone("seat");

        super.setCustomAnimations(e,id, s);
        GeoBone spinner = this.getAnimationProcessor().getBone("full");
        if (spinner == null) return;

        // Get velocity and movement direction
        Vec3d velocity = e.getVelocity();
        Vec3d facing = e.getRotationVec(1.0f);

        // Dot product → how aligned velocity is with facing direction
        double forwardSpeed = velocity.dotProduct(facing);

        // Magnitude of velocity → overall speed
        double totalSpeed = velocity.length();

        // Compute spin speed and direction
        float spinSpeed = (float) (forwardSpeed * -800.0f); // tweak multiplier (higher = faster spin)

        // Smoothly interpolate spin progress (reduces jitter)
        e.spinSpeed += (spinSpeed - e.spinSpeed) * 0.2f;

        // Accumulate spin angle
        e.spinAngle += e.spinSpeed * 0.05f; // tweak multiplier to control smoothness

        // Keep angle within bounds (avoid float overflow)
        if (e.spinAngle > 360f || e.spinAngle < -360f) e.spinAngle = 0f;

        // Apply rotation to the bone (convert to radians)
        spinner.setRotX((float) Math.toRadians(e.spinAngle));
    }

}
