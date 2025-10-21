package net.kronoz.odyssey.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class EnergyBarrierBlock extends Block {
    public static final IntProperty LIGHT_LEVEL = IntProperty.of("light_level", 5, 15);
    public static final DirectionProperty FACING = Properties.FACING;
    public EnergyBarrierBlock(Settings settings) {

        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(LIGHT_LEVEL, 5)
                .with(FACING, Direction.UP));

    }
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction clickedFace = ctx.getSide(); // the face the player clicked
        return this.getDefaultState().with(FACING, clickedFace);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIGHT_LEVEL, FACING);
    }

      @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);

       if (!world.isClient) {
           world.scheduleBlockTick(pos, this, 1);
       }
   }

    @Override
   public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            world.scheduleBlockTick(pos, this, 1);
        }
   }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {

//        Direction facing = state.get(FACING);
//        BlockPos targetPos = pos.offset(facing);
//        int value = 7;
//        BlockPos behindBlock = pos.offset(facing.getOpposite(), value);
//        BlockState behindState = world.getBlockState(behindBlock);

        int lightLevel = getPulsingLight(world);
        world.setBlockState(pos, state.with(LIGHT_LEVEL, lightLevel), 3);
        world.scheduleBlockTick(pos, this, 1);


//        if (!world.isClient) {
//            if (!world.getBlockState(behindBlock).isOf(ModBlocks.ENERGY_BARRIER)) {
//            world.scheduleBlockTick(pos, this, 2);
//            if (world.getBlockState(targetPos).isAir()) {
//                BlockState newState = ModBlocks.ENERGY_BARRIER.getDefaultState()
//                        .with(FACING, facing);
//                world.setBlockState(targetPos, newState);
//            }
//            }



//
//        }





    }

    private static int getPulsingLight(World world) {

        double time = (world.getTime() % 40) / 40.0 * Math.PI * 2; // full cycle every 40 ticks (~2 seconds)
        double brightness = (Math.sin(time) * 0.5 + 0.5); // 0 → 1 → 0
        return 5 + (int) (brightness * (15 - 5));
    }





}