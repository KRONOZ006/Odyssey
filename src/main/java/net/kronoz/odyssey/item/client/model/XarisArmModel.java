package net.kronoz.odyssey.item.client.model;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.XarisArm;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class XarisArmModel extends GeoModel<XarisArm> {
    @Override
    public Identifier getModelResource(XarisArm item) {
        return Identifier.of(Odyssey.MODID, "geo/item/xaris.geo.json");
    }

    @Override
    public Identifier getTextureResource(XarisArm item) {
        return Identifier.of(Odyssey.MODID, "textures/item/xaris.png");
    }

    @Override
    public Identifier getAnimationResource(XarisArm item) {
        return Identifier.of(Odyssey.MODID, "animations/item/xaris.animation.json");
    }
}
