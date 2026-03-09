package net.kronoz.odyssey.client;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

public final class LightOcclusionHelper {
    private static final float LINE_OF_SIGHT_MIN_FACTOR = 0.05f;
    private static final int ENTITY_QUERY_HARD_LIMIT = 16;
    private static final double ENTITY_INFLUENCE_EXPAND = 0.12;
    private static final double ENTITY_RAYCAST_EXPAND = 0.10;
    private static final double HIT_EPSILON = 0.05;

    private static final Vec3d[] POINT_SAMPLE_DIRECTIONS = new Vec3d[] {
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

    private LightOcclusionHelper() {}

    public static float directionalDistance(ClientWorld world, Vec3d origin, Vec3d direction, float maxDistance, @Nullable Entity ignored) {
        if (world == null || maxDistance <= 0.0f) {
            return 0.0f;
        }

        Vec3d normalized = direction.normalize();
        if (normalized.lengthSquared() < 1.0E-6) {
            return maxDistance;
        }

        float testedDistance = Math.max(0.15f, maxDistance);
        Vec3d end = origin.add(normalized.multiply(testedDistance));
        double best = maxDistance;

        RaycastContext colliderRaycast = ignored != null
                ? new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                ignored
        )
                : new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                ShapeContext.absent()
        );
        BlockHitResult blockHit = world.raycast(colliderRaycast);
        if (blockHit.getType() != HitResult.Type.MISS) {
            best = Math.min(best, origin.distanceTo(blockHit.getPos()));
        }

        RaycastContext outlineRaycast = ignored != null
                ? new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                ignored
        )
                : new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                ShapeContext.absent()
        );
        BlockHitResult outlineHit = world.raycast(outlineRaycast);
        if (outlineHit.getType() != HitResult.Type.MISS) {
            BlockPos hitPos = outlineHit.getBlockPos();
            BlockState hitState = world.getBlockState(hitPos);
            if (isOutlineOccluder(world, hitPos, hitState)) {
                best = Math.min(best, origin.distanceTo(outlineHit.getPos()));
            }
        }

        double entityHit = entityDistance(world, origin, end, ignored);
        if (entityHit >= 0.0) {
            best = Math.min(best, entityHit);
        }

        return (float) Math.max(0.0, best - HIT_EPSILON);
    }

    public static float pointVisibility(ClientWorld world, Vec3d origin, float radius, @Nullable Entity ignored) {
        if (world == null || radius <= 0.0f) {
            return 0.0f;
        }

        float sum = 0.0f;
        for (Vec3d dir : POINT_SAMPLE_DIRECTIONS) {
            float d = directionalDistance(world, origin, dir, radius, ignored);
            sum += clamp01(d / radius);
        }
        return sum / (float) POINT_SAMPLE_DIRECTIONS.length;
    }

    public static float lineOfSightFactor(ClientWorld world, Vec3d start, Vec3d end, @Nullable Entity ignored) {
        if (world == null) {
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

        // Keep a tiny amount of bleed to preserve atmosphere while blocking hard line-of-sight leaks.
        float eased = ratio * ratio;
        return Math.max(LINE_OF_SIGHT_MIN_FACTOR, eased * 0.90f);
    }

    private static double entityDistance(ClientWorld world, Vec3d start, Vec3d end, @Nullable Entity ignored) {
        Box rayBox = new Box(start, end).expand(ENTITY_INFLUENCE_EXPAND);
        double best = -1.0;
        int checked = 0;
        for (Entity other : world.getOtherEntities(ignored, rayBox, LightOcclusionHelper::isEntityOccluder)) {
            if (++checked > ENTITY_QUERY_HARD_LIMIT) {
                break;
            }
            var hit = other.getBoundingBox().expand(ENTITY_RAYCAST_EXPAND).raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double dist = start.distanceTo(hit.get());
            if (best < 0.0 || dist < best) {
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
        if (state.isOpaqueFullCube(world, pos) || state.isOpaque()) {
            return true;
        }
        if (state.getOpacity(world, pos) >= 7) {
            return true;
        }
        if (!state.getCollisionShape(world, pos).isEmpty()) {
            return true;
        }
        // Keep thin custom model blocks in the occlusion path when they have visible geometry.
        return !state.getOutlineShape(world, pos).isEmpty();
    }

    private static boolean isEntityOccluder(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator() || entity.noClip) {
            return false;
        }

        Box box = entity.getBoundingBox();
        double maxDimension = Math.max(box.getLengthX(), Math.max(box.getLengthY(), box.getLengthZ()));
        if (maxDimension >= 0.90) {
            return true;
        }
        return entity instanceof PlayerEntity || entity instanceof MobEntity;
    }

    private static float clamp01(float value) {
        if (value <= 0.0f) return 0.0f;
        if (value >= 1.0f) return 1.0f;
        return value;
    }
}
