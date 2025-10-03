package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.ElevatorBlockEntity;
import net.kronoz.odyssey.entity.SlidingDoorBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static BlockEntityType<ElevatorBlockEntity> ELEVATOR_BE;
    public static BlockEntityType<SlidingDoorBlockEntity> SLIDING_DOOR_BE;

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
    }
}
