package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.item.client.renderer.TomahawkRenderer;
import net.kronoz.odyssey.item.client.renderer.XarisArmRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class XarisArm extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);


    public XarisArm(@NotNull Settings settings) {
        super(settings);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private XarisArmRenderer renderer;
            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new XarisArmRenderer();
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar r) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
