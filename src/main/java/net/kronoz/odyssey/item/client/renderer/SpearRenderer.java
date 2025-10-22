package net.kronoz.odyssey.item.client.renderer;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.SpearDashItem;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.specialty.DynamicGeoItemRenderer;

public class SpearRenderer extends DynamicGeoItemRenderer<SpearDashItem> {

    private static final Identifier MODEL_ID   = Identifier.of(Odyssey.MODID, "spear");


    public SpearRenderer() {
        super(new DefaultedItemGeoModel<>(MODEL_ID));
    }

    @Override
    protected boolean boneRenderOverride(
            MatrixStack ms,
            GeoBone bone,
            VertexConsumerProvider buffers,
            VertexConsumer buffer,
            float pt,
            int packedLight,
            int packedOverlay,
            int colour
    ) {

        ItemStack current = this.getCurrentItemStack();
        if (!(current.getItem() instanceof SpearDashItem)) return false;
        return false;
    }


}
