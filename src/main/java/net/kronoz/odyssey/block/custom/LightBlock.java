package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.CollisionShapeHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LightBlock extends Block {
    private static final VoxelShape SHAPE =
            CollisionShapeHelper.loadUnrotatedCollisionFromModelJson(Odyssey.MODID, "light_1");

    public LightBlock(Settings settings) { super(settings); }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean moved) {
        super.onBlockAdded(state, world, pos, oldState, moved);
        if (world.isClient) {
            SimpleBlockLightManager.requestAdd(pos);
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) {
            SimpleBlockLightManager.requestAdd(pos);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient && (!newState.isOf(this))) {
            SimpleBlockLightManager.requestRemove(pos);
        }
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.onBroken(world, pos, state);
        if (world.isClient) {
            SimpleBlockLightManager.requestRemove(pos);
        }
    }
}
