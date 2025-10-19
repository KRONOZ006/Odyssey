package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.entity.StasisPodBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class StasisPodGeoModel extends GeoModel<StasisPodBlockEntity> {
    private static final Identifier GEO = Identifier.of("odyssey","geo/block/stasispod.geo.json");
    private static final Identifier TEX = Identifier.of("odyssey","textures/block/stasis_pod.png");
    private static final Identifier ANIM = Identifier.of("odyssey","animations/stasispod.animation.json");
    @Override public Identifier getModelResource(StasisPodBlockEntity a) { return GEO; }
    @Override public Identifier getTextureResource(StasisPodBlockEntity a) { return TEX; }
    @Override public Identifier getAnimationResource(StasisPodBlockEntity a) { return null; }
}
