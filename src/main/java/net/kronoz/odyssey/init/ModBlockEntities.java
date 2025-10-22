package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.custom.TerminalBlockEntity;
import net.kronoz.odyssey.entity.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<ElevatorBlockEntity> ELEVATOR_BE;

    public static BlockEntityType<SlidingDoorBlockEntity> SLIDING_DOOR_BE;
    public static BlockEntityType<Shelf1BlockEntity> SHELF1;
    public static final BlockEntityType<SequencerBlockEntity> SEQUENCER = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(Odyssey.MODID, "sequencer"), BlockEntityType.Builder.create((pos, state) -> new SequencerBlockEntity(pos, state, ModBlockEntities.SEQUENCER), ModBlocks.SEQUENCEB).build(null));
    public static final BlockEntityType<TerminalBlockEntity> TERMINAL = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of("odyssey","terminal"), BlockEntityType.Builder.create(TerminalBlockEntity::new, ModBlocks.TERMINAL).build(null));
    public static final BlockEntityType<StasisPodBlockEntity> STASISPOD = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of("odyssey","stasispod"), BlockEntityType.Builder.create(StasisPodBlockEntity::new, ModBlocks.STASISPOD).build(null));
    public static void register() {
        ELEVATOR_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(Odyssey.MODID, "elevator_be"),
                BlockEntityType.Builder.create(ElevatorBlockEntity::new, ModBlocks.ELEVATOR).build(null)
        );
        SLIDING_DOOR_BE = net.minecraft.registry.Registry.register(
                net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(net.kronoz.odyssey.Odyssey.MODID,"sliding_door_be"),
                net.minecraft.block.entity.BlockEntityType.Builder.create(
                        SlidingDoorBlockEntity::new, ModBlocks.SLIDING_DOOR
                ).build(null)
        );
        SHELF1 = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of("odyssey", "shelf1"),
                BlockEntityType.Builder.create(Shelf1BlockEntity::new, ModBlocks.SHELF1).build(null)
        );
    }
    public static void init() {}
}
