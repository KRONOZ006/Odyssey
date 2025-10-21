package net.kronoz.odyssey.block.custom;

import com.mojang.serialization.MapCodec;
import net.kronoz.odyssey.block.GeoCollisionHelper;
import net.kronoz.odyssey.entity.StasisPodBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StasisPodBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 44);
    private static final BlockPos[] OFFSETS = buildOffsets();

    public StasisPodBlock(Settings settings) {
        super(settings.nonOpaque().strength(3.0f));
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART, 4));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return null;
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING, PART); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        BlockPos origin = ctx.getBlockPos();
        World world = ctx.getWorld();
        if (!canPlaceStructure(world, origin, facing, ctx)) return null;
        placeStructure(world, origin, facing);
        return getDefaultState().with(FACING, facing).with(PART, 4);
    }

    @Override public BlockRenderType getRenderType(BlockState s) { return BlockRenderType.MODEL; }
    @Override public ActionResult onUse(BlockState s, World w, BlockPos p, PlayerEntity pl, BlockHitResult hit) { return ActionResult.PASS; }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == 4 ? new StasisPodBlockEntity(pos, state) : null;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        return GeoCollisionHelper
                .partShape("odyssey", "block/stasispod", s.get(PART), s.get(FACING));
    }
    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        return GeoCollisionHelper
                .partShape("odyssey", "block/stasispod", s.get(PART), s.get(FACING));
    }
    @Override
    public VoxelShape getRaycastShape(BlockState s, BlockView w, BlockPos p) {
        return GeoCollisionHelper
                .partShape("odyssey", "block/stasispod", s.get(PART), s.get(FACING));
    }



    private static BlockPos rotateRel(BlockPos rel, Direction facing) {
        return switch (facing) {
            case NORTH -> rel;
            case SOUTH -> new BlockPos(-rel.getX(), rel.getY(), -rel.getZ());
            case WEST  -> new BlockPos(rel.getZ(), rel.getY(), -rel.getX());
            case EAST  -> new BlockPos(-rel.getZ(), rel.getY(), rel.getX());
            default    -> rel;
        };
    }

    private boolean canPlaceStructure(World world, BlockPos origin, Direction facing, ItemPlacementContext ctx) {
        for (BlockPos o : OFFSETS) {
            BlockPos p = origin.add(rotateRel(o, facing));
            BlockState st = world.getBlockState(p);
            if (!st.isAir() && !st.canReplace(ctx)) return false;
        }
        return true;
    }

    private void placeStructure(World world, BlockPos origin, Direction facing) {
        for (int i = 0; i < OFFSETS.length; i++) {
            BlockPos p = origin.add(rotateRel(OFFSETS[i], facing));
            world.setBlockState(p, getDefaultState().with(FACING, facing).with(PART, i), Block.NOTIFY_ALL | Block.FORCE_STATE);
        }
    }

    @Override
    public void onStateReplaced(BlockState s, World w, BlockPos pos, BlockState ns, boolean moved) {
        if (s.isOf(ns.getBlock())) return;
        Direction face = s.get(FACING);
        int part = s.get(PART);
        BlockPos origin = pos.subtract(rotateRel(OFFSETS[part], face));
        for (BlockPos o : OFFSETS) {
            BlockPos p = origin.add(rotateRel(o, face));
            if (w.getBlockState(p).isOf(this)) w.removeBlock(p, false);
        }
    }

    private static BlockPos[] buildOffsets() {
        BlockPos[] list = new BlockPos[45];
        int i = 0;
        for (int y = 0; y < 5; y++) for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) list[i++] = new BlockPos(x, y, z);
        return list;
    }
}
