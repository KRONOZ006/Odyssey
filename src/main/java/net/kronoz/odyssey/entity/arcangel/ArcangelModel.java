package net.kronoz.odyssey.entity.arcangel;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ArcangelModel extends GeoModel<ArcangelEntity> {
    @Override
    public Identifier getModelResource(ArcangelEntity animatable) {
        return Identifier.of("odyssey", "geo/entity/arcangel.geo.json");
    }

    @Override
    public Identifier getTextureResource(ArcangelEntity animatable) {
        return Identifier.of("odyssey", "textures/entity/arcangel.png");
    }

    @Override
    public Identifier getAnimationResource(ArcangelEntity animatable) {
        return Identifier.of("odyssey", "animations/entity/arcangel.animation.json");
    }

    @Override
    public void setCustomAnimations(ArcangelEntity e, long id, AnimationState<ArcangelEntity> state) {
        GeoBone full = getAnimationProcessor().getBone("full");
        GeoBone head = getAnimationProcessor().getBone("head");

        if (full != null) {
            full.setRotY((float) Math.toRadians(-e.getFullBodyYaw()));
        }
        if (head != null) {
            float baseX = (float) Math.toRadians(-e.getHeadPitchDeg());
            head.setRotX(baseX + e.getRecoilRad());
        }
    }
}