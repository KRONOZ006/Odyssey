package net.kronoz.odyssey.block.custom;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TransformingAlterBlock extends Block {
    public TransformingAlterBlock(AbstractBlock.Settings settings) {
        super(settings);
    }



    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // Set spawn here
            serverPlayer.setSpawnPoint(world.getRegistryKey(), pos.up(), 0.0f, true, false);
            serverPlayer.sendMessage(Text.literal("Spawn set!"), false);
        }
         return ActionResult.SUCCESS;

    }

}