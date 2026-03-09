package net.kronoz.odyssey.mixin.shadows;

import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(VoxelShadowGrid.class)
public abstract class VoxelShadowGridEntityMixin {

    private static final int ENTITY_HARD_LIMIT = 96;
    private static final int ENTITY_VOXEL_BUDGET = 22000;
    private static final double ENTITY_QUERY_MARGIN = 1.5;
    private static final double ENTITY_BOX_EXPAND = 0.06;
    private static final double MIN_OCCLUDER_VOLUME = 0.010;
    private static final double MIN_RADIUS = 0.12;

    private static final Int2ByteMap MUTATED_VOXELS = new Int2ByteOpenHashMap(4096);

    @Shadow private static int originX;
    @Shadow private static int originY;
    @Shadow private static int originZ;
    @Shadow private static ByteBuffer gridBuffer;
    @Shadow @Final public static int GRID_SIZE;

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/impl/client/render/light/VoxelShadowGrid;uploadBuffer(Ljava/nio/ByteBuffer;)V"
            )
    )
    private static void veil_shadows$stampEntitiesBeforeUpload(CallbackInfo ci) {
        MUTATED_VOXELS.clear();
        if (gridBuffer == null || GRID_SIZE <= 0) {
            return;
        }
        if (!OdysseyConfig.occlusionEnabled
                || !OdysseyConfig.occludeWithEntities
                || !OdysseyConfig.entityShadowingEnabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client != null ? client.world : null;
        if (world == null) {
            return;
        }

        Entity cameraEntity = client.getCameraEntity();
        Box gridBounds = new Box(
                originX, originY, originZ,
                originX + GRID_SIZE, originY + GRID_SIZE, originZ + GRID_SIZE
        ).expand(ENTITY_QUERY_MARGIN);

        List<Entity> entities = world.getOtherEntities(null, gridBounds, VoxelShadowGridEntityMixin::isEntityOccluder);
        if (entities.isEmpty()) {
            return;
        }
        if (cameraEntity != null) {
            entities.removeIf(entity -> entity == cameraEntity);
        }

        int quality = OdysseyConfig.effectiveEntityShadowQuality();
        int voxelBudget = switch (quality) {
            case 0 -> ENTITY_VOXEL_BUDGET / 3;
            case 1 -> ENTITY_VOXEL_BUDGET / 2;
            case 3 -> (int) (ENTITY_VOXEL_BUDGET * 1.5f);
            default -> ENTITY_VOXEL_BUDGET;
        };
        int hardLimit = switch (quality) {
            case 0 -> 32;
            case 1 -> 64;
            case 3 -> 156;
            default -> ENTITY_HARD_LIMIT;
        };

        Vec3d camera = cameraEntity != null
                ? cameraEntity.getCameraPosVec(1.0f)
                : new Vec3d(originX + GRID_SIZE * 0.5, originY + GRID_SIZE * 0.5, originZ + GRID_SIZE * 0.5);
        entities.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(camera)));

        int processed = 0;
        for (Entity entity : entities) {
            if (entity == null || ++processed > hardLimit || voxelBudget <= 0) {
                break;
            }
            int written = stampEntity(entity, voxelBudget);
            if (written <= 0) {
                continue;
            }
            voxelBudget -= written;
        }
    }

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/impl/client/render/light/VoxelShadowGrid;uploadBuffer(Ljava/nio/ByteBuffer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void veil_shadows$restoreEntitiesAfterUpload(CallbackInfo ci) {
        if (gridBuffer == null || MUTATED_VOXELS.isEmpty()) {
            MUTATED_VOXELS.clear();
            return;
        }

        for (Int2ByteMap.Entry entry : MUTATED_VOXELS.int2ByteEntrySet()) {
            gridBuffer.put(entry.getIntKey(), entry.getByteValue());
        }
        MUTATED_VOXELS.clear();
    }

    @Inject(method = "clearLevel", at = @At("HEAD"))
    private static void veil_shadows$clearEntityStampState(CallbackInfo ci) {
        MUTATED_VOXELS.clear();
    }

    @Unique
    private record StampVolume(double cx, double cy, double cz, double rx, double ry, double rz, int occupancy) {
    }

    @Unique
    private static int stampEntity(Entity entity, int voxelBudget) {
        Box box = entity.getVisibilityBoundingBox()
                .union(entity.getBoundingBox())
                .expand(ENTITY_BOX_EXPAND);
        if (box.isNaN()) {
            return 0;
        }

        List<StampVolume> volumes = buildVolumes(entity, box);
        if (volumes.isEmpty()) {
            return 0;
        }

        int written = 0;
        for (StampVolume volume : volumes) {
            int budgetLeft = Math.max(voxelBudget - written, 0);
            if (budgetLeft <= 0) {
                break;
            }
            written += stampEllipsoid(volume, budgetLeft);
        }
        if (written < 4) {
            int fallback = Math.max(entityOccupancy(entity) - 28, 0);
            written += stampEllipsoid(boxAsVolume(box, fallback), Math.max(voxelBudget - written, 0));
        }
        return written;
    }

    @Unique
    private static List<StampVolume> buildVolumes(Entity entity, Box box) {
        int occupancy = entityOccupancy(entity);
        List<StampVolume> volumes = new ArrayList<>(12);

        double lengthX = box.getLengthX();
        double lengthY = box.getLengthY();
        double lengthZ = box.getLengthZ();
        double width = Math.max(lengthX, lengthZ);

        if (!(entity instanceof LivingEntity) || lengthY < 0.62) {
            volumes.add(boxAsVolume(box, occupancy));
            return volumes;
        }

        double yaw = Math.toRadians(entity.getBodyYaw());
        double sideX = Math.cos(yaw);
        double sideZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        double pelvisY = box.minY + lengthY * 0.33;
        double chestY = box.minY + lengthY * 0.59;
        double neckY = box.minY + lengthY * 0.74;
        double headY = box.minY + lengthY * 0.86;

        double pelvisRx = Math.max(MIN_RADIUS, width * 0.28);
        double torsoRx = Math.max(MIN_RADIUS, width * 0.24);
        double limbRx = Math.max(MIN_RADIUS * 0.65, width * 0.12);
        double legRy = Math.max(MIN_RADIUS, lengthY * 0.24);
        double armRy = Math.max(MIN_RADIUS, lengthY * 0.18);

        volumes.add(new StampVolume(centerX, pelvisY, centerZ, pelvisRx, lengthY * 0.17, pelvisRx, Math.max(occupancy - 18, 0)));
        volumes.add(new StampVolume(centerX, chestY, centerZ, torsoRx, lengthY * 0.21, torsoRx, occupancy));
        volumes.add(new StampVolume(centerX, neckY, centerZ, torsoRx * 0.85, lengthY * 0.11, torsoRx * 0.85, Math.max(occupancy - 12, 0)));
        volumes.add(new StampVolume(centerX, headY, centerZ, torsoRx * 0.72, lengthY * 0.12, torsoRx * 0.72, Math.max(occupancy - 6, 0)));

        double legOffset = width * 0.16;
        double armOffset = width * 0.30;
        double legForward = width * 0.05;
        double armForward = width * 0.13;
        double upperLegY = box.minY + lengthY * 0.20;
        double lowerLegY = box.minY + lengthY * 0.08;
        double armY = box.minY + lengthY * 0.57;

        volumes.add(orientedVolume(centerX, upperLegY, centerZ, sideX, sideZ, forwardX, forwardZ, legOffset, legForward, limbRx, legRy, limbRx, Math.max(occupancy - 24, 0)));
        volumes.add(orientedVolume(centerX, upperLegY, centerZ, sideX, sideZ, forwardX, forwardZ, -legOffset, -legForward, limbRx, legRy, limbRx, Math.max(occupancy - 24, 0)));
        volumes.add(orientedVolume(centerX, lowerLegY, centerZ, sideX, sideZ, forwardX, forwardZ, legOffset, 0.0, limbRx * 0.9, legRy * 0.95, limbRx * 0.9, Math.max(occupancy - 32, 0)));
        volumes.add(orientedVolume(centerX, lowerLegY, centerZ, sideX, sideZ, forwardX, forwardZ, -legOffset, 0.0, limbRx * 0.9, legRy * 0.95, limbRx * 0.9, Math.max(occupancy - 32, 0)));
        volumes.add(orientedVolume(centerX, armY, centerZ, sideX, sideZ, forwardX, forwardZ, armOffset, armForward, limbRx * 0.92, armRy, limbRx * 0.92, Math.max(occupancy - 34, 0)));
        volumes.add(orientedVolume(centerX, armY, centerZ, sideX, sideZ, forwardX, forwardZ, -armOffset, armForward, limbRx * 0.92, armRy, limbRx * 0.92, Math.max(occupancy - 34, 0)));

        return volumes;
    }

    @Unique
    private static StampVolume orientedVolume(double baseX,
                                              double baseY,
                                              double baseZ,
                                              double sideX,
                                              double sideZ,
                                              double forwardX,
                                              double forwardZ,
                                              double sideOffset,
                                              double forwardOffset,
                                              double rx,
                                              double ry,
                                              double rz,
                                              int occupancy) {
        double x = baseX + sideX * sideOffset + forwardX * forwardOffset;
        double z = baseZ + sideZ * sideOffset + forwardZ * forwardOffset;
        return new StampVolume(x, baseY, z, rx, ry, rz, occupancy);
    }

    @Unique
    private static StampVolume boxAsVolume(Box box, int occupancy) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double rx = Math.max(MIN_RADIUS, box.getLengthX() * 0.5);
        double ry = Math.max(MIN_RADIUS, box.getLengthY() * 0.5);
        double rz = Math.max(MIN_RADIUS, box.getLengthZ() * 0.5);
        return new StampVolume(cx, cy, cz, rx, ry, rz, occupancy);
    }

    @Unique
    private static int stampEllipsoid(StampVolume volume, int voxelBudget) {
        if (gridBuffer == null || volume.occupancy() <= 0 || voxelBudget <= 0) {
            return 0;
        }

        double cx = volume.cx();
        double cy = volume.cy();
        double cz = volume.cz();
        double rx = Math.max(MIN_RADIUS, volume.rx());
        double ry = Math.max(MIN_RADIUS, volume.ry());
        double rz = Math.max(MIN_RADIUS, volume.rz());

        int minX = clampInt((int) Math.floor(cx - rx) - originX, 0, GRID_SIZE - 1);
        int minY = clampInt((int) Math.floor(cy - ry) - originY, 0, GRID_SIZE - 1);
        int minZ = clampInt((int) Math.floor(cz - rz) - originZ, 0, GRID_SIZE - 1);
        int maxX = clampInt((int) Math.ceil(cx + rx) - originX - 1, 0, GRID_SIZE - 1);
        int maxY = clampInt((int) Math.ceil(cy + ry) - originY - 1, 0, GRID_SIZE - 1);
        int maxZ = clampInt((int) Math.ceil(cz + rz) - originZ - 1, 0, GRID_SIZE - 1);

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return 0;
        }

        double invRx = 1.0 / rx;
        double invRy = 1.0 / ry;
        double invRz = 1.0 / rz;
        int occupancy = Math.min(255, Math.max(1, volume.occupancy()));

        int written = 0;
        int sliceArea = GRID_SIZE * GRID_SIZE;
        for (int z = minZ; z <= maxZ && written < voxelBudget; z++) {
            int zOffset = z * sliceArea;
            double worldZ = originZ + z + 0.5;
            double dz = (worldZ - cz) * invRz;
            double dz2 = dz * dz;
            for (int y = minY; y <= maxY && written < voxelBudget; y++) {
                int rowOffset = zOffset + y * GRID_SIZE;
                double worldY = originY + y + 0.5;
                double dy = (worldY - cy) * invRy;
                double dy2 = dy * dy;
                for (int x = minX; x <= maxX && written < voxelBudget; x++) {
                    double worldX = originX + x + 0.5;
                    double dx = (worldX - cx) * invRx;
                    double d2 = dx * dx + dy2 + dz2;
                    if (d2 > 1.0) {
                        continue;
                    }

                    double shellFactor = 1.0 - d2;
                    int target = (int) Math.round(occupancy * (0.42 + 0.58 * shellFactor));
                    if (target <= 0) {
                        continue;
                    }

                    int index = rowOffset + x;
                    int previous = gridBuffer.get(index) & 0xFF;
                    if (previous >= target) {
                        continue;
                    }
                    if (!MUTATED_VOXELS.containsKey(index)) {
                        MUTATED_VOXELS.put(index, (byte) previous);
                    }
                    gridBuffer.put(index, (byte) target);
                    written++;
                }
            }
        }

        return written;
    }

    @Unique
    private static int entityOccupancy(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return 240;
        }
        if (entity instanceof MobEntity) {
            return 232;
        }
        if (entity instanceof LivingEntity) {
            return 218;
        }

        double volume = entity.getVisibilityBoundingBox().getLengthX()
                * entity.getVisibilityBoundingBox().getLengthY()
                * entity.getVisibilityBoundingBox().getLengthZ();
        if (volume > 2.0) {
            return 226;
        }
        if (volume > 0.30) {
            return 200;
        }
        return 162;
    }

    @Unique
    private static boolean isEntityOccluder(Entity entity) {
        if (entity == null || entity.isRemoved() || !entity.isAlive() || entity.noClip || entity.isSpectator()) {
            return false;
        }
        if (entity.isInvisible()) {
            return false;
        }
        Box box = entity.getVisibilityBoundingBox();
        if (box == null || box.isNaN()) {
            return false;
        }
        double volume = box.getLengthX() * box.getLengthY() * box.getLengthZ();
        return volume >= MIN_OCCLUDER_VOLUME;
    }

    @Unique
    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
