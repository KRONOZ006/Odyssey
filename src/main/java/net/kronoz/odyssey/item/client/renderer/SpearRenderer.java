package net.kronoz.odyssey.item.client.renderer;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.SpearDashItem;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.specialty.DynamicGeoItemRenderer;
import software.bernie.geckolib.util.Color;

public class SpearRenderer extends DynamicGeoItemRenderer<SpearDashItem> {

    private static final Identifier MODEL_ID = Identifier.of(Odyssey.MODID, "spear");


    public SpearRenderer() {
        super(new DefaultedItemGeoModel<>(MODEL_ID));
    }

    @Override
    protected boolean boneRenderOverride(MatrixStack poseStack,
                                         GeoBone bone,
                                         VertexConsumerProvider bufferSource,
                                         VertexConsumer buffer,
                                         float partialTick,
                                         int packedLight,
                                         int packedOverlay,
                                         int colour) {
        ItemStack stack = getCurrentItemStack();
        if (stack.isEmpty()) return false;

        boolean isActive = SpearDashItem.isRenderActive(stack);

        // ✅ Only glow when active
        boolean isEmissiveBone = bone.getName().equals("plane")
                || bone.getName().equals("glowblade")
                || bone.getName().equals("glowhandle");

        if (isActive && isEmissiveBone) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(
                    RenderLayer.getEyes(Identifier.of(Odyssey.MODID, "textures/item/spear.png"))
            );

            if (!bone.isHidden()) {
                poseStack.push();
                for (GeoCube cube : bone.getCubes()) {
                    renderCube(poseStack, cube, vertexConsumer,
                            15728640, OverlayTexture.DEFAULT_UV, Color.WHITE.getColor());
                }
                poseStack.pop();
            }

            return true;
        }


        return false;
    }
}

