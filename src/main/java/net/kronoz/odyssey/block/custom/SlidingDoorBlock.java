package net.kronoz.odyssey.block.custom;

import com.mojang.serialization.MapCodec;
import net.kronoz.odyssey.entity.SlidingDoorBlockEntity;
import net.kronoz.odyssey.init.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class SlidingDoorBlock extends BlockWithEntity {
    public static final MapCodec<SlidingDoorBlock> CODEC = createCodec(SlidingDoorBlock::new);
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    public SlidingDoorBlock(Settings s){ super(s); setDefaultState(getDefaultState().with(POWERED,false).with(FACING,Direction.NORTH)); }
    @Override public MapCodec<? extends BlockWithEntity> getCodec(){ return CODEC; }
    @Override public BlockState getPlacementState(ItemPlacementContext ctx){ return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()); }
    @Override protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> b){ b.add(POWERED, FACING); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state){ return new SlidingDoorBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w, BlockState s, BlockEntityType<T> t){
        return w.isClient?null:validateTicker(t, ModBlockEntities.SLIDING_DOOR_BE, SlidingDoorBlockEntity::serverTick);
    }
    @Override public BlockRenderType getRenderType(BlockState state){ return BlockRenderType.MODEL; }

    @Override public void neighborUpdate(BlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos fromPos, boolean notify){
        if(world.isClient) return;
        boolean now = world.isReceivingRedstonePower(pos);
        boolean was = state.get(POWERED);
        if(now!=was){
            world.setBlockState(pos, state.with(POWERED, now), 3);
            BlockEntity be = world.getBlockEntity(pos);
            if(be instanceof SlidingDoorBlockEntity d){
                d.toggle((ServerWorld)world, state.get(FACING));
            }
        }
    }
}
