package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.init.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
        Direction clickedFace = ctx.getSide();
        return this.getDefaultState().with(FACING, clickedFace);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;

        boolean isPowered = world.isReceivingRedstonePower(pos);
        boolean wasPowered = state.get(POWERED);

        if (isPowered && !wasPowered) {
            world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_LISTENERS);
            world.scheduleBlockTick(pos, this, 2);
        } else if (!isPowered && wasPowered) {
            world.setBlockState(pos, state.with(POWERED, false), Block.NOTIFY_LISTENERS);
            world.scheduleBlockTick(pos, this, 2);
        }
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        Direction facing = state.get(FACING);
        boolean isPowered = world.isReceivingRedstonePower(pos);
        int power = world.getReceivedRedstonePower(pos) * world.getReceivedRedstonePower(pos);


        if (isPowered) {
            extendChain(world, pos, facing, 1, Math.min(power, MAX_DISTANCE));


        } else {
            retractChain(world, pos, facing);
        }
    }

    private void extendChain(ServerWorld world, BlockPos origin, Direction dir, int step, int max) {
        if (step > max) return;

        BlockPos targetPos = origin.offset(dir, step);
        BlockState targetState = world.getBlockState(targetPos);


        if (!targetState.isAir() && !targetState.isReplaceable()) return;
        System.out.println(step + "goobor");


        world.setBlockState(targetPos, ModBlocks.ENERGY_BARRIER.getDefaultState().with(FACING, dir));


        world.scheduleBlockTick(origin, this, 20 + step);

        world.getServer().execute(() -> extendChain(world, origin, dir, step + 1, max));


    }

    private void retractChain(ServerWorld world, BlockPos origin, Direction dir) {

        for (int i = 1; i <= MAX_DISTANCE; i++) {
            BlockPos targetPos = origin.offset(dir, i);
            if (world.getBlockState(targetPos).isOf(ModBlocks.ENERGY_BARRIER)) {
                world.breakBlock(targetPos, false);
            }
        }
    }
}