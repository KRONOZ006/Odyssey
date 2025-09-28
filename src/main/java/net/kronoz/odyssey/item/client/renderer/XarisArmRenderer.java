package net.kronoz.odyssey.item.client.renderer;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.kronoz.odyssey.item.custom.XarisArm;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.specialty.DynamicGeoItemRenderer;
import software.bernie.geckolib.util.Color;

public class XarisArmRenderer extends DynamicGeoItemRenderer<XarisArm> {

    private static final Identifier MODEL_ID   = Identifier.of(Odyssey.MODID, "xaris");

    public XarisArmRenderer() {
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
        if (!(current.getItem() instanceof XarisArm)) return false;

        return false;
    }
}
