package net.kronoz.odyssey.block.custom;

import com.mojang.serialization.MapCodec;
import net.kronoz.odyssey.entity.ElevatorBlockEntity;
import net.kronoz.odyssey.init.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ElevatorBlock extends BlockWithEntity {
    public static final MapCodec<ElevatorBlock> CODEC = createCodec(ElevatorBlock::new);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    public ElevatorBlock(Settings s){ super(s); setDefaultState(getDefaultState().with(POWERED,false)); }
    @Override public MapCodec<? extends BlockWithEntity> getCodec(){ return CODEC; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state){ return new ElevatorBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w, BlockState s, BlockEntityType<T> t){ return w.isClient?null:validateTicker(t, ModBlockEntities.ELEVATOR_BE, ElevatorBlockEntity::serverTick); }
    @Override public BlockRenderType getRenderType(BlockState state){ return BlockRenderType.MODEL; }
    @Override protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> b){ b.add(POWERED); }

    @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx){
        boolean p = ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos());
        return getDefaultState().with(POWERED,p);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, net.minecraft.block.Block sourceBlock, BlockPos sourcePos, boolean notify){
        if(world.isClient) return;
        boolean now = world.isReceivingRedstonePower(pos);
        boolean was = state.get(POWERED);
        if(now != was){
            world.setBlockState(pos, state.with(POWERED, now), 3);
            if(now){
                BlockEntity be = world.getBlockEntity(pos);
                if(be instanceof ElevatorBlockEntity elev) elev.start((ServerWorld)world, +1);
            }
        }
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
    }
}