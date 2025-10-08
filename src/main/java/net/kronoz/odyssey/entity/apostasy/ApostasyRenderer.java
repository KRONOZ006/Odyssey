package net.kronoz.odyssey.entity.apostasy;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ApostasyRenderer extends GeoEntityRenderer<ApostasyEntity> {
    public ApostasyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ApostasyModel());
        this.shadowRadius = 2.0f;
    }
    @Override
    public Identifier getTextureLocation(ApostasyEntity e) {
        return ((ApostasyModel)this.getGeoModel()).getTextureResource(e);
    }
}
