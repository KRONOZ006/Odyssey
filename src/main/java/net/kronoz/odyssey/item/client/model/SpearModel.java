package net.kronoz.odyssey.item.client.model;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.SpearDashItem;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class SpearModel extends GeoModel<SpearDashItem> {
    @Override
    public Identifier getModelResource(SpearDashItem item) {
        return Identifier.of(Odyssey.MODID, "geo/item/spear.geo.json");
    }

    @Override
    public Identifier getTextureResource(SpearDashItem item) {
        return Identifier.of(Odyssey.MODID, "textures/item/spear.png");
    }

    @Override
    public Identifier getAnimationResource(SpearDashItem item) {
        return Identifier.of(Odyssey.MODID, "animation/item/spear.animation.json");
    }
}
