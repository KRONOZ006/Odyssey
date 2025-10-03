package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.LiftPartColliderEntity;
import net.kronoz.odyssey.entity.LiftPlatformEntity;
import net.kronoz.odyssey.entity.SlidePartColliderEntity;
import net.kronoz.odyssey.entity.SlidePlatformEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static EntityType<LiftPlatformEntity> LIFT_PLATFORM;
    public static EntityType<LiftPartColliderEntity> LIFT_PART_COLLIDER;
    public static EntityType<SlidePlatformEntity> SLIDE_PLATFORM;
    public static EntityType<SlidePartColliderEntity> SLIDE_PART_COLLIDER;

    public static void register() {
        LIFT_PLATFORM = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(Odyssey.MODID, "lift_platform"),
                EntityType.Builder.create(LiftPlatformEntity::new, SpawnGroup.MISC)
                        .dimensions(0.95f, 0.85f)
                        .maxTrackingRange(128)
                        .build()
        );
        LIFT_PART_COLLIDER = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(Odyssey.MODID, "lift_part_collider"),
                EntityType.Builder.<LiftPartColliderEntity>create(LiftPartColliderEntity::new, SpawnGroup.MISC)
                        .dimensions(1.0f, 1.0f)
                        .maxTrackingRange(64)
                        .build()
        );

        SLIDE_PLATFORM = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(Odyssey.MODID,"slide_platform"),
                EntityType.Builder.create(
                        SlidePlatformEntity::new, SpawnGroup.MISC
                ).dimensions(1.0f,1.0f).maxTrackingRange(128).build()
        );
        SLIDE_PART_COLLIDER = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(Odyssey.MODID,"slide_part_collider"),
                EntityType.Builder.create(
                        SlidePartColliderEntity::new, SpawnGroup.MISC
                ).dimensions(1.0f,1.0f).maxTrackingRange(64).build()
        );
    }
}
