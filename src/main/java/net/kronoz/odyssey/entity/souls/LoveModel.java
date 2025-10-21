package net.kronoz.odyssey.entity.souls;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.sentry.SentryEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class LoveModel extends GeoModel<LoveEntity> {
    @Override public Identifier getModelResource(LoveEntity e){ return Identifier.of(Odyssey.MODID,"geo/entity/love.geo.json"); }
    @Override public Identifier getTextureResource(LoveEntity e){ return Identifier.of(Odyssey.MODID,"textures/entity/love.png"); }
    @Override public Identifier getAnimationResource(LoveEntity e){ return Identifier.of(Odyssey.MODID,"animations/entity/love.animation.json"); }

    @Override
    public void setCustomAnimations(LoveEntity e, long id, AnimationState<LoveEntity> s) {

    }
}
