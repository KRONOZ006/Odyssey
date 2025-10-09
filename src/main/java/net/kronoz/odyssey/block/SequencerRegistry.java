package net.kronoz.odyssey.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.kronoz.odyssey.block.custom.SequencerBlock;
import net.kronoz.odyssey.entity.SequencerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SequencerRegistry {
    public static final SequencerBlock SEQUENCER_BLOCK = new SequencerBlock(Block.Settings.create().strength(1.0f).nonOpaque());
    public static BlockEntityType<SequencerBlockEntity> SEQUENCER_BE;

    public static void init() {
        Registry.register(Registries.BLOCK, Identifier.of("odyssey","sequencer"), SEQUENCER_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of("odyssey","sequencer"), new BlockItem(SEQUENCER_BLOCK, new Item.Settings()));
        SEQUENCER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of("odyssey","sequencer"),
                FabricBlockEntityTypeBuilder.create(SequencerBlockEntity::new, SEQUENCER_BLOCK).build()
        );
    }
}
