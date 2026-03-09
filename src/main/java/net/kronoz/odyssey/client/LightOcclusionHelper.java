package net.kronoz.odyssey.client;

import net.kronoz.odyssey.config.OdysseyConfig;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

public final class LightOcclusionHelper {
    private static final float LINE_OF_SIGHT_MIN_FACTOR = 0.05f;
    private static final double ENTITY_INFLUENCE_EXPAND = 0.12;
    private static final double ENTITY_RAYCAST_EXPAND = 0.10;
    private static final double ENTITY_MIN_VOLUME = 0.010;
    private static final double HIT_EPSILON = 0.05;

    private static final Vec3d[] POINT_SAMPLE_DIRECTIONS_LOW = new Vec3d[] {
            new Vec3d(1, 0, 0),
            new Vec3d(-1, 0, 0),
            new Vec3d(0, 1, 0),
            new Vec3d(0, -1, 0),
            new Vec3d(0, 0, 1),
            new Vec3d(0, 0, -1)
    };

    private static final Vec3d[] POINT_SAMPLE_DIRECTIONS_MEDIUM = new Vec3d[] {
            new Vec3d(1, 0, 0),
            new Vec3d(-1, 0, 0),
            new Vec3d(0, 1, 0),
            new Vec3d(0, -1, 0),
            new Vec3d(0, 0, 1),
            new Vec3d(0, 0, -1),
            new Vec3d(1, 1, 1),
            new Vec3d(-1, 1, 1),
            new Vec3d(1, -1, 1),
            new Vec3d(1, 1, -1),
            new Vec3d(-1, -1, 1),
            new Vec3d(-1, 1, -1),
            new Vec3d(1, -1, -1),
            new Vec3d(-1, -1, -1)
    };

    private static final Vec3d[] POINT_SAMPLE_DIRECTIONS_HIGH = new Vec3d[] {
            new Vec3d(1, 0, 0),
            new Vec3d(-1, 0, 0),
            new Vec3d(0, 1, 0),
            new Vec3d(0, -1, 0),
            new Vec3d(0, 0, 1),
            new Vec3d(0, 0, -1),
            new Vec3d(1, 1, 1),
            new Vec3d(-1, 1, 1),
            new Vec3d(1, -1, 1),
            new Vec3d(1, 1, -1),
            new Vec3d(-1, -1, 1),
            new Vec3d(-1, 1, -1),
            new Vec3d(1, -1, -1),
            new Vec3d(-1, -1, -1),
            new Vec3d(1, 1, 0),
            new Vec3d(1, -1, 0),
            new Vec3d(-1, 1, 0),
            new Vec3d(-1, -1, 0),
            new Vec3d(1, 0, 1),
            new Vec3d(1, 0, -1),
            new Vec3d(-1, 0, 1),
            new Vec3d(-1, 0, -1),
            new Vec3d(0, 1, 1),
            new Vec3d(0, 1, -1),
            new Vec3d(0, -1, 1),
            new Vec3d(0, -1, -1)
    };

    private LightOcclusionHelper() {}

    public static float directionalDistance(ClientWorld world, Vec3d origin, Vec3d direction, float maxDistance, @Nullable Entity ignored) {
        if (world == null || maxDistance <= 0.0f) {
            return 0.0f;
        }
        if (!OdysseyConfig.occlusionEnabled) {
            return maxDistance;
        }

        Vec3d normalized = direction.normalize();
        if (normalized.lengthSquared() < 1.0E-6) {
            return maxDistance;
        }

        float testedDistance = Math.max(0.15f, maxDistance);
        Vec3d end = origin.add(normalized.multiply(testedDistance));
        double best = maxDistance;

        RaycastContext colliderRaycast = raycastContext(origin, end, RaycastContext.ShapeType.COLLIDER, ignored);
        BlockHitResult blockHit = world.raycast(colliderRaycast);
        if (blockHit.getType() != HitResult.Type.MISS) {
            best = Math.min(best, origin.distanceTo(blockHit.getPos()));
        }

        if (OdysseyConfig.occludeWithModelGeometry) {
            RaycastContext outlineRaycast = raycastContext(origin, end, RaycastContext.ShapeType.OUTLINE, ignored);
            BlockHitResult outlineHit = world.raycast(outlineRaycast);
            if (outlineHit.getType() != HitResult.Type.MISS) {
                BlockPos hitPos = outlineHit.getBlockPos();
                BlockState hitState = world.getBlockState(hitPos);
                if (isOutlineOccluder(world, hitPos, hitState)) {
                    best = Math.min(best, origin.distanceTo(outlineHit.getPos()));
                }
            }

            RaycastContext visualRaycast = raycastContext(origin, end, RaycastContext.ShapeType.VISUAL, ignored);
            BlockHitResult visualHit = world.raycast(visualRaycast);
            if (visualHit.getType() != HitResult.Type.MISS) {
                BlockPos hitPos = visualHit.getBlockPos();
                BlockState hitState = world.getBlockState(hitPos);
                if (isVisualOccluder(world, hitPos, hitState)) {
                    best = Math.min(best, origin.distanceTo(visualHit.getPos()));
                }
            }
        }

        if (OdysseyConfig.occludeWithEntities && OdysseyConfig.entityShadowingEnabled) {
            double entityHit = entityDistance(world, origin, end, ignored);
            if (entityHit >= 0.0) {
                best = Math.min(best, entityHit);
            }
        }

        return (float) Math.max(0.0, best - HIT_EPSILON);
    }

    public static float pointVisibility(ClientWorld world, Vec3d origin, float radius, @Nullable Entity ignored) {
        if (world == null || radius <= 0.0f) {
            return 0.0f;
        }
        if (!OdysseyConfig.occlusionEnabled) {
            return 1.0f;
        }

        Vec3d[] directions = pointSampleDirections();
        float sum = 0.0f;
        for (Vec3d dir : directions) {
            float d = directionalDistance(world, origin, dir, radius, ignored);
            sum += clamp01(d / radius);
        }
        return sum / (float) directions.length;
    }

    public static float lineOfSightFactor(ClientWorld world, Vec3d start, Vec3d end, @Nullable Entity ignored) {
        if (world == null || !OdysseyConfig.occlusionEnabled) {
            return 1.0f;
        }

        double totalDistance = start.distanceTo(end);
        if (totalDistance <= 1.0E-6) {
            return 1.0f;
        }

        Vec3d direction = end.subtract(start).normalize();
        float clearDistance = directionalDistance(world, start, direction, (float) totalDistance, ignored);
        float ratio = clamp01((float) (clearDistance / totalDistance));
        if (ratio >= 0.999f) {
            return 1.0f;
        }

        float eased = ratio * ratio;
        return Math.max(LINE_OF_SIGHT_MIN_FACTOR, eased * 0.90f);
    }

    private static Vec3d[] pointSampleDirections() {
        int quality = OdysseyConfig.qualityLevel(OdysseyConfig.lightQuality);
        if (quality <= 0) {
            return POINT_SAMPLE_DIRECTIONS_LOW;
        }
        if (quality >= 3) {
            return POINT_SAMPLE_DIRECTIONS_HIGH;
        }
        return POINT_SAMPLE_DIRECTIONS_MEDIUM;
    }

    private static int entityQueryHardLimit() {
        int quality = OdysseyConfig.effectiveEntityShadowQuality();
        return switch (quality) {
            case 0 -> 8;
            case 1 -> 12;
            case 3 -> 24;
            default -> 16;
        };
    }

    private static double entityDistance(ClientWorld world, Vec3d start, Vec3d end, @Nullable Entity ignored) {
        Box rayBox = new Box(start, end).expand(ENTITY_INFLUENCE_EXPAND);
        double best = -1.0;
        int checked = 0;
        int hardLimit = entityQueryHardLimit();

        for (Entity other : world.getOtherEntities(ignored, rayBox, LightOcclusionHelper::isEntityOccluder)) {
            if (++checked > hardLimit) {
                break;
            }

            Box base = other.getVisibilityBoundingBox()
                    .union(other.getBoundingBox())
                    .expand(ENTITY_RAYCAST_EXPAND);
            double dist = raycastBoxDistance(base, start, end);

            if (other instanceof LivingEntity) {
                double height = base.getLengthY();
                if (height >= 0.65) {
                    double legTop = base.minY + height * 0.52;
                    double torsoTop = base.minY + height * 0.83;

                    Box legs = shrinkHorizontal(base, 0.18).withMaxY(legTop);
                    Box torso = shrinkHorizontal(base, 0.08).withMinY(legTop).withMaxY(torsoTop);
                    Box head = shrinkHorizontal(base, 0.26).withMinY(torsoTop);

                    dist = minPositive(dist, raycastBoxDistance(legs, start, end));
                    dist = minPositive(dist, raycastBoxDistance(torso, start, end));
                    dist = minPositive(dist, raycastBoxDistance(head, start, end));
                }
            }

            if (dist >= 0.0 && (best < 0.0 || dist < best)) {
                best = dist;
            }
        }
        return best;
    }

    private static boolean isOutlineOccluder(ClientWorld world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.getRenderType() == BlockRenderType.INVISIBLE) {
            return false;
        }
        RenderLayer layer = RenderLayers.getBlockLayer(state);
        if (layer == RenderLayer.getTripwire()) {
            return false;
        }
        if (state.isOpaqueFullCube(world, pos) || state.isOpaque()) {
            return true;
        }
        if (state.getOpacity(world, pos) >= 7) {
            return true;
        }
        if (!state.getCollisionShape(world, pos).isEmpty()) {
            return true;
        }
        if (!OdysseyConfig.occludeWithModelGeometry) {
            return false;
        }
        if (layer == RenderLayer.getTranslucent()) {
            return state.getOpacity(world, pos) >= 4 && !state.getOutlineShape(world, pos).isEmpty();
        }
        return !state.getOutlineShape(world, pos).isEmpty();
    }

    private static boolean isVisualOccluder(ClientWorld world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || state.getRenderType() == BlockRenderType.INVISIBLE) {
            return false;
        }
        RenderLayer layer = RenderLayers.getBlockLayer(state);
        if (layer == RenderLayer.getTripwire()) {
            return false;
        }
        if (state.isOpaqueFullCube(world, pos) || state.isOpaque()) {
            return true;
        }
        if (!OdysseyConfig.occludeWithModelGeometry) {
            return state.getOpacity(world, pos) >= 7;
        }
        if (!state.getOutlineShape(world, pos).isEmpty()) {
            if (layer == RenderLayer.getTranslucent()) {
                return state.getOpacity(world, pos) >= 5;
            }
            return true;
        }
        return state.getOpacity(world, pos) >= 7;
    }

    private static boolean isEntityOccluder(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.isSpectator() || entity.noClip) {
            return false;
        }
        if (entity.isInvisible()) {
            return false;
        }

        Box box = entity.getVisibilityBoundingBox();
        double volume = box.getLengthX() * box.getLengthY() * box.getLengthZ();
        if (volume >= ENTITY_MIN_VOLUME) {
            return true;
        }
        return entity instanceof PlayerEntity || entity instanceof MobEntity || entity instanceof LivingEntity;
    }

    private static RaycastContext raycastContext(Vec3d origin,
                                                 Vec3d end,
                                                 RaycastContext.ShapeType shapeType,
                                                 @Nullable Entity ignored) {
        if (ignored != null) {
            return new RaycastContext(
                    origin,
                    end,
                    shapeType,
                    RaycastContext.FluidHandling.NONE,
                    ignored
            );
        }
        return new RaycastContext(
                origin,
                end,
                shapeType,
                RaycastContext.FluidHandling.NONE,
                ShapeContext.absent()
        );
    }

    private static double raycastBoxDistance(Box box, Vec3d start, Vec3d end) {
        if (box == null || box.isNaN()) {
            return -1.0;
        }
        var hit = box.raycast(start, end);
        return hit.map(start::distanceTo).orElse(-1.0);
    }

    private static double minPositive(double a, double b) {
        if (a < 0.0) {
            return b;
        }
        if (b < 0.0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static Box shrinkHorizontal(Box box, double factor) {
        double dx = box.getLengthX() * factor;
        double dz = box.getLengthZ() * factor;

        double minX = box.minX + dx;
        double maxX = box.maxX - dx;
        double minZ = box.minZ + dz;
        double maxZ = box.maxZ - dz;
        if (maxX <= minX || maxZ <= minZ) {
            return box;
        }
        return new Box(minX, box.minY, minZ, maxX, box.maxY, maxZ);
    }

    private static float clamp01(float value) {
        if (value <= 0.0f) return 0.0f;
        if (value >= 1.0f) return 1.0f;
        return value;
    }
}
