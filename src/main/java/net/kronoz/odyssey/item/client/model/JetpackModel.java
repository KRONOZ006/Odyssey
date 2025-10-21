package net.kronoz.odyssey.item.client.model;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.JetpackTorso;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class JetpackModel extends GeoModel<JetpackTorso> {
    @Override
    public Identifier getModelResource(JetpackTorso item) {
        return Identifier.of(Odyssey.MODID, "geo/item/jetpack.geo.json");
    }

    @Override
    public Identifier getTextureResource(JetpackTorso item) {
        return Identifier.of(Odyssey.MODID, "textures/item/jetpack.png");
    }

    @Override
    public Identifier getAnimationResource(JetpackTorso item) {
        return null;
    }
}
