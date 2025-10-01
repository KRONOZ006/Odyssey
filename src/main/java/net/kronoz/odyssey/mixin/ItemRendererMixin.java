package net.kronoz.odyssey.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.init.ModItems;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Shadow
    public abstract ItemModels getModels();





    @ModifyVariable(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "HEAD"),
            argsOnly = true
    )
    private BakedModel antlers$swapTomahawkGuiModel(
            BakedModel bakedModel,
            @Local(argsOnly = true) ItemStack stack,
            @Local(argsOnly = true) ModelTransformationMode renderMode
    ) {

        if (stack.getItem() == ModItems.TOMAHAWK &&
                (renderMode == ModelTransformationMode.GUI)) {

            Identifier id = Identifier.of(
                    Odyssey.MODID,
                    "tomahawk_2d"
            );
            ModelIdentifier mid = ModelIdentifier.ofInventoryVariant(id);

            return getModels().getModelManager().getModel(mid);
        }
        return bakedModel;
    }
}
