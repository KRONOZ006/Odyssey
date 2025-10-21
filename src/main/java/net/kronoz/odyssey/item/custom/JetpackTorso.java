package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.item.client.renderer.JetpackRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.item.Item;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class JetpackTorso extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);


    public JetpackTorso(@NotNull Settings settings) {
        super(settings);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private JetpackRenderer renderer;
            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new JetpackRenderer();
                return renderer;
            }
        });
    }
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
