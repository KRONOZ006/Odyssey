package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Comparator;

public class UnholyAlterBlock extends Block {

    public static final BooleanProperty SPAWNED = BooleanProperty.of("spawned");
    public static final DirectionProperty FACING = Properties.FACING;

    public UnholyAlterBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.UP)
                .with(SPAWNED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPAWNED);
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return state.get(SPAWNED);
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(SPAWNED) ? 15 : 0;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }

    /**
     * Spawn Apostasy once per block
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient || state.get(SPAWNED)) return ActionResult.SUCCESS;

        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.SUCCESS;

        // Check if Apostasy already exists nearby
        Box checkBox = new Box(pos).expand(2, 3, 2);
        boolean exists = !serverWorld.getEntitiesByClass(ApostasyEntity.class, checkBox, e -> true).isEmpty();

        if (!exists) {
            ApostasyEntity apostasy = ModEntities.APOSTASY.create(serverWorld);
            if (apostasy != null) {
                Direction facing = state.get(FACING);
                float yaw = switch (facing) {
                    case NORTH -> 180f;
                    case SOUTH -> 0f;
                    case WEST -> 90f;
                    case EAST -> 270f;
                    default -> 0f;
                };
                apostasy.refreshPositionAndAngles(
                        pos.getX() + 0.5,
                        pos.getY() + 1,
                        pos.getZ() + 0.5,
                        yaw,
                        0
                );
                serverWorld.spawnEntity(apostasy);

                // Update block state so it won't spawn again

                world.setBlockState(pos, state.with(SPAWNED, true), Block.NOTIFY_ALL | Block.FORCE_STATE);
                world.updateNeighbors(pos.down(), this);

            }
        }

        return ActionResult.SUCCESS;
    }
}
