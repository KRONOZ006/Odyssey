package net.kronoz.odyssey.block.custom;

import com.mojang.serialization.MapCodec;
import net.kronoz.odyssey.block.SequencerRegistry;
import net.kronoz.odyssey.entity.SequencerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class SequencerBlock extends BlockWithEntity {
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");
    public static final IntProperty POWER = IntProperty.of("power", 0, 15);

    public SequencerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(RUNNING, false).with(POWER, 0));
    }

    // 1.21.1 requirement
    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(SequencerBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(RUNNING, POWER);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SequencerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return type == SequencerRegistry.SEQUENCER_BE
                ? (w, p, s, be) -> ((SequencerBlockEntity) be).serverTick()
                : null;
    }

    // 1.21.1: non-item interaction
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof SequencerBlockEntity seq) {
            Direction face = hit.getSide();
            seq.spawnVeilLight(face);
            return ActionResult.CONSUME;

        }
        return ActionResult.CONSUME;
    }

    // 1.21.1: item-in-hand interaction (we just pass-through to the same behavior)
    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                          PlayerEntity player, Hand hand, BlockHitResult hit) {
        ActionResult r = onUse(state, world, pos, player, hit);
        return r.isAccepted() ? ItemActionResult.SUCCESS : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    // leave these WITHOUT @Override (mappings/API differ)
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction dir) {
        return state.get(POWER);
    }

    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction dir) {
        return state.get(POWER);
    }
}
