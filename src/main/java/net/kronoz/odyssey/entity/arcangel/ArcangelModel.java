package net.kronoz.odyssey.entity.arcangel;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.animation.AnimationState;

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
        return Identifier.of("odyssey", "animations/empty.json");
    }

    @Override
    public void setCustomAnimations(ArcangelEntity entity, long instanceId, AnimationState<ArcangelEntity> state) {
        GeoBone full = this.getAnimationProcessor().getBone("full");
        GeoBone head = this.getAnimationProcessor().getBone("head");

        if (full != null) {
            float yawRad = (float) Math.toRadians(-entity.getDataTracker().get(ArcangelEntity.FULL_YAW));
            full.setRotY(yawRad);
        }
        if (head != null) {
            float pitchRad = (float) Math.toRadians(-entity.getDataTracker().get(ArcangelEntity.HEAD_PITCH));
            head.setRotX(pitchRad);
        }
    }
}
