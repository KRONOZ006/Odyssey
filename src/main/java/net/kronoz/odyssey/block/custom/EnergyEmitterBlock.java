package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.init.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class EnergyEmitterBlock extends Block {
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final DirectionProperty FACING = Properties.FACING;
    public static final int MAX_DISTANCE = 100;
    private static final int STEP_DELAY_TICKS = 2;

    public EnergyEmitterBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getSide());
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;

        boolean isPowered = world.isReceivingRedstonePower(pos);
        boolean wasPowered = state.get(POWERED);

        if (isPowered != wasPowered) {
            world.setBlockState(pos, state.with(POWERED, isPowered), Block.NOTIFY_LISTENERS);
            world.scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            Direction dir = state.get(FACING);
            retractChain((ServerWorld) world, pos, dir);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        Direction dir = state.get(FACING);
        boolean powered = world.isReceivingRedstonePower(pos);

        if (!powered) {
            retractChain(world, pos, dir);
            return;
        }

        int raw = world.getReceivedRedstonePower(pos);
        int targetLen = Math.min(raw * raw, MAX_DISTANCE);

        int currentLen = 0;
        for (int i = 1; i <= MAX_DISTANCE; i++) {
            BlockPos p = pos.offset(dir, i);
            if (world.getBlockState(p).isOf(ModBlocks.ENERGY_BARRIER)) {
                currentLen++;
            } else {
                break;
            }
        }

        if (currentLen > targetLen) {
            for (int i = currentLen; i > targetLen; i--) {
                BlockPos p = pos.offset(dir, i);
                if (world.getBlockState(p).isOf(ModBlocks.ENERGY_BARRIER)) {
                    world.breakBlock(p, false);
                }
            }
            world.scheduleBlockTick(pos, this, STEP_DELAY_TICKS);
            return;
        }

        if (currentLen < targetLen) {
            BlockPos nextPos = pos.offset(dir, currentLen + 1);
            BlockState nextState = world.getBlockState(nextPos);

            if (nextState.isAir()) {
                world.setBlockState(
                        nextPos,
                        ModBlocks.ENERGY_BARRIER.getDefaultState().with(EnergyBarrierBlock.FACING, dir),
                        Block.NOTIFY_ALL
                );
            }
            world.scheduleBlockTick(pos, this, STEP_DELAY_TICKS);
        }
    }

    private void retractChain(ServerWorld world, BlockPos origin, Direction dir) {
        for (int i = 1; i <= MAX_DISTANCE; i++) {
            BlockPos p = origin.offset(dir, i);
            if (world.getBlockState(p).isOf(ModBlocks.ENERGY_BARRIER)) {

                world.removeBlock(p, false);
            } else {
                break;
            }
        }
    }
}