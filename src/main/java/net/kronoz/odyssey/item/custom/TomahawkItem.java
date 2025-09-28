package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.item.client.renderer.TomahawkRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ToolMaterials;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class TomahawkItem extends AxeItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);


    public TomahawkItem(@NotNull Settings settings) {
        super(ToolMaterials.DIAMOND, settings);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private TomahawkRenderer renderer;
            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new TomahawkRenderer();
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar r) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
