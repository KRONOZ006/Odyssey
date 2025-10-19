package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.init.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class TerminalBlockEntity extends BlockEntity {
    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL, pos, state);
    }
}
