package net.kronoz.odyssey.item.client.model;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class TomahawkModel extends GeoModel<TomahawkItem> {
    @Override
    public Identifier getModelResource(TomahawkItem item) {
        return  Identifier.of(Odyssey.MODID, "geo/item/tomahawk.geo.json");
    }

    @Override
    public Identifier getTextureResource(TomahawkItem item) {
        return Identifier.of(Odyssey.MODID, "textures/item/tomahawk.png");
    }

    @Override
    public Identifier getAnimationResource(TomahawkItem item) {
        return null;
    }
}
