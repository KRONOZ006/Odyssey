package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.ModBlocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static BlockEntityType<ElevatorBlockEntity> ELEVATOR_BE;

    public static void register() {
        ELEVATOR_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(Odyssey.MODID, "elevator_be"),
                BlockEntityType.Builder.create(ElevatorBlockEntity::new, ModBlocks.ELEVATOR).build(null)
        );
    }
}
