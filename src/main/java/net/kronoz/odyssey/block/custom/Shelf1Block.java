package net.kronoz.odyssey.block.custom;

import com.mojang.serialization.MapCodec;
import net.kronoz.odyssey.block.CollisionShapeHelper;
import net.kronoz.odyssey.entity.Shelf1BlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class Shelf1Block extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    private static final String NS = "odyssey";
    private static final String MODEL_PATH = "shelf1";

    public Shelf1Block(Settings settings) {
        super(settings.nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return null;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return CollisionShapeHelper.getDirectionalShape(NS, MODEL_PATH, state.get(FACING));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return CollisionShapeHelper.getDirectionalShape(NS, MODEL_PATH, state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return CollisionShapeHelper.getDirectionalShape(NS, MODEL_PATH, state.get(FACING));
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new Shelf1BlockEntity(pos, state); }

    @Override public boolean hasComparatorOutput(BlockState state) { return true; }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof Shelf1BlockEntity shelf) {
            int filled = shelf.getFilledSlots(), total = shelf.size();
            return total == 0 ? 0 : Math.round(15f * (filled / (float) total));
        }
        return 0;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof Shelf1BlockEntity shelf)) return ActionResult.PASS;

        boolean bothHandsEmpty = player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty();
        if (!bothHandsEmpty) return ActionResult.PASS;

        if (world.isClient) {
            clientPreviewTake(world, pos);
            return ActionResult.SUCCESS;
        }

        int slot = shelf.lastFilledSlot();
        if (slot >= 0) {
            ItemStack s = shelf.getStack(slot);
            if (!player.getInventory().insertStack(s.copy())) player.dropItem(s.copy(), false);
            shelf.clearSlot(slot);
            world.updateComparators(pos, this);
            return ActionResult.CONSUME;
        }
        return ActionResult.SUCCESS;
    }
    private static void clientPreviewTake(World world, BlockPos pos) {
        if (!(world instanceof net.minecraft.client.world.ClientWorld)) return;
        var be = world.getBlockEntity(pos);
        if (!(be instanceof net.kronoz.odyssey.entity.Shelf1BlockEntity shelf)) return;

        int slot = shelf.lastFilledSlot();
        if (slot >= 0) {
            shelf.clearSlot(slot);
        }
    }

    private static void clientPreviewPlace(World world, BlockPos pos, ItemStack stack, boolean placeAll) {
        if (!(world instanceof net.minecraft.client.world.ClientWorld)) return;
        if (stack.isEmpty()) return;
        var be = world.getBlockEntity(pos);
        if (!(be instanceof net.kronoz.odyssey.entity.Shelf1BlockEntity shelf)) return;

        int slot = shelf.firstEmptySlot();
        if (slot >= 0) {
            int count = placeAll ? stack.getCount() : 1;
            shelf.setStack(slot, stack.copyWithCount(count));
        }
    }
    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                          PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof Shelf1BlockEntity shelf)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (world.isClient) {
            clientPreviewPlace(world, pos, stack, player.isSneaking());
            return ItemActionResult.SUCCESS;
        }

        int slot = shelf.firstEmptySlot();
        if (slot >= 0) {
            int count = player.isSneaking() ? stack.getCount() : 1;
            ItemStack placed = stack.copyWithCount(count);
            shelf.setStack(slot, placed);
            stack.decrement(count);
            world.updateComparators(pos, this);
            return ItemActionResult.CONSUME;
        }
        return ItemActionResult.SUCCESS;
    }
}
