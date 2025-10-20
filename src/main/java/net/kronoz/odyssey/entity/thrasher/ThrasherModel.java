package net.kronoz.odyssey.entity.thrasher;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.sentry.SentryEntity;
import net.minecraft.util.Identifier;
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


    }
}
