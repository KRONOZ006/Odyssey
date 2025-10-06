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
    public void setCustomAnimations(SentinelEntity e, long id, AnimationState<SentinelEntity> s) {
        GeoBone head = getAnimationProcessor().getBone("head");
        GeoBone eye  = getAnimationProcessor().getBone("eye");
        if (head != null) head.setRotX(e.getHeadPitch());
        if (eye != null) {
            eye.setRotY(e.getEyeYaw());
            eye.setRotX(-e.getEyePitch());
        }
    }
}
