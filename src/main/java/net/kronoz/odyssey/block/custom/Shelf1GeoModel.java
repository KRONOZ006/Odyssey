package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.entity.Shelf1BlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class Shelf1GeoModel extends GeoModel<Shelf1BlockEntity> {
    private static final Identifier GEO = Identifier.of("odyssey", "geo/block/shelf1.geo.json");
    private static final Identifier TEX = Identifier.of("odyssey", "textures/block/shelf1.png");
    private static final Identifier ANIM = Identifier.of("odyssey", "animations/shelf1.animation.json");

    @Override public Identifier getModelResource(Shelf1BlockEntity animatable) { return GEO; }
    @Override public Identifier getTextureResource(Shelf1BlockEntity animatable) { return TEX; }
    @Override public Identifier getAnimationResource(Shelf1BlockEntity animatable) { return ANIM; }
}
