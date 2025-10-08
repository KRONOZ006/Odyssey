package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.entity.LiftPartColliderEntity;
import net.kronoz.odyssey.entity.LiftPlatformEntity;
import net.kronoz.odyssey.entity.SlidePartColliderEntity;
import net.kronoz.odyssey.entity.SlidePlatformEntity;
import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.kronoz.odyssey.entity.projectile.LaserProjectileEntity;
import net.kronoz.odyssey.entity.sentinel.SentinelEntity;
import net.kronoz.odyssey.entity.sentry.SentryEntity;
import net.minecraft.entity.EntityDimensions;
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
    public static final EntityType<SentinelEntity> SENTINEL = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Odyssey.MODID, "sentinel"),
            EntityType.Builder.<SentinelEntity>create(SentinelEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.8f, 0.8f)
                    .eyeHeight(0.6f)
                    .build()
    );
    public static final EntityType<SentryEntity> SENTRY = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Odyssey.MODID, "sentry"),
            EntityType.Builder.<SentryEntity>create(SentryEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.9f, 2.95f)
                    .build()
    );
    public static final EntityType<ApostasyEntity> APOSTASY = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Odyssey.MODID, "apostasy"),
            EntityType.Builder.<ApostasyEntity>create(ApostasyEntity::new, SpawnGroup.MONSTER)
                    .dimensions(3.0f, 5.0f)
                    .build()
    );
    public static final EntityType<LaserProjectileEntity> LASER_PROJECTILE =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(Odyssey.MODID, "laser_projectile"),
                    FabricEntityTypeBuilder.<LaserProjectileEntity>create(SpawnGroup.MISC, (type, world) -> new LaserProjectileEntity(type, world))
                            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(10)
                            .build()
            );

    public static void init() {
        FabricDefaultAttributeRegistry.register(APOSTASY, ApostasyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SENTRY, SentryEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SENTINEL, SentinelEntity.createAttributes());
    }
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
