package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.kronoz.odyssey.entity.arcangel.ArcangelEntity;
import net.kronoz.odyssey.init.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Comparator;

public class DevinityMachineBlock extends Block {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final int SPAWN_DELAY = 1; // tick delay to ensure world is loaded

    public DevinityMachineBlock(Settings settings) {
        super(settings.nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }



    /**
     * Schedule a tick whenever the block is added or loaded.
     * This ensures it works for player placement, WorldEdit, structure generation, etc.
     */
    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) {
            world.scheduleBlockTick(pos, this, SPAWN_DELAY);
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         net.minecraft.entity.LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) {
            scheduledTick(state, (ServerWorld) world, pos, null);
        }
    }



    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.SUCCESS;
        if (sp.isCreative() || sp.isSpectator()) return ActionResult.SUCCESS;

        sp.damage(((ServerWorld) world).getDamageSources().magic(), 8f);

        Box box = new Box(pos).expand(64); // area around block
        ArcangelEntity nearest = world.getEntitiesByClass(ArcangelEntity.class, box, e -> true)
                .stream()
                .min(Comparator.comparingDouble(
                        e -> e.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                ))
                .orElse(null);

        if (nearest != null) {
            nearest.addBlood(25);
            world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1f, 0.8f);
        }

        return ActionResult.SUCCESS;
    }
}
