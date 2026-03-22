package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.systems.reset.ResetZoneSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RespawnPointBlock extends Block {
    public RespawnPointBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) {
            super.onStateReplaced(state, world, pos, newState, moved);
            return;
        }
        if (world instanceof ServerWorld serverWorld) {
            ResetZoneSystem.unlinkRespawn(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
