package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.init.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StasisPodBlockEntity extends BlockEntity implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public StasisPodBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.STASISPOD, pos, state); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public double getTick(Object o) { return world == null ? 0 : world.getTime(); }
}
