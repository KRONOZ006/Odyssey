package net.kronoz.odyssey.entity.sentry;

import net.kronoz.odyssey.entity.sentry.SentryEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SentryRenderer extends GeoEntityRenderer<SentryEntity> {
    public SentryRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SentryModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    public Identifier getTextureLocation(SentryEntity e) {
        return ((SentryModel)this.getGeoModel()).getTextureResource(e);
    }
}
