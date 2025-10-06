package net.kronoz.odyssey.entity.sentry;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.sentry.SentryEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class SentryModel extends GeoModel<SentryEntity> {
    @Override public Identifier getModelResource(SentryEntity e){ return Identifier.of(Odyssey.MODID,"geo/entity/sentry.geo.json"); }
    @Override public Identifier getTextureResource(SentryEntity e){ return Identifier.of(Odyssey.MODID,"textures/entity/sentry.png"); }
    @Override public Identifier getAnimationResource(SentryEntity e){ return Identifier.of(Odyssey.MODID,"animations/entity/sentry.animation.json"); }

    @Override
    public void setCustomAnimations(SentryEntity e, long id, AnimationState<SentryEntity> s) {
        GeoBone head = getAnimationProcessor().getBone("head");
        GeoBone eye  = getAnimationProcessor().getBone("eye");
        if (head != null) { head.setRotX(e.getHeadPitch()); head.setRotY(0f); }
        if (eye != null)  { eye.setRotY(-e.getEyeYaw()); eye.setRotX(e.getEyePitch()); }
    }
}
