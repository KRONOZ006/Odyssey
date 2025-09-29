package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public class FacilityPIllarBlock extends Block {
    private static final VoxelShape SHAPE = net.dark.spv_addon.init.helper.CollisionShapeHelper.loadUnrotatedCollisionFromModelJson(Odyssey.MODID, "facility_pillar");

    public FacilityPIllarBlock(Settings settings) { super(settings); }

    @Override
    public VoxelShape getCollisionShape(net.minecraft.block.BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOutlineShape(net.minecraft.block.BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(net.minecraft.block.BlockState state) {
        return BlockRenderType.MODEL;
    }
}
