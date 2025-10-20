package net.kronoz.odyssey.entity.arcangel;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ArcangelRenderer extends GeoEntityRenderer<ArcangelEntity> {
    public ArcangelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ArcangelModel());
        this.shadowRadius = 0.7f;
    }

    @Override
    public Identifier getTextureLocation(ArcangelEntity entity) {
        return Identifier.of("odyssey","textures/entity/arcangel.png");
    }
}
