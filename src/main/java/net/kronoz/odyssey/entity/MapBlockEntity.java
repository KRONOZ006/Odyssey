package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.init.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class MapBlockEntity extends BlockEntity {
    public MapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MAP_BLOCK_ENTITY, pos, state);
    }
}
