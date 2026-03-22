package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.systems.physics.wire.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

public class WireCutterItem extends Item {
    public WireCutterItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!(world instanceof ServerWorld sw)) {
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        double reach = 6.5;
        HitResult hr = user.raycast(reach, 1.0f, false);
        Vec3d hit = hr.getPos();

        WireStorage storage = WireStorage.get(sw);

        if (hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            List<WireRecord> attached = storage.attachedTo(bhr.getBlockPos());
            if (!attached.isEmpty()) {
                int removed = removeAttached(sw, storage, attached, hit, user.isSneaking());
                if (removed > 0) {
                    return TypedActionResult.success(user.getStackInHand(hand));
                }
            }
        }

        WireRecord victim = nearestBySegment(storage, hit);
        if (victim == null) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        storage.remove(victim.id);
        WireNetworking.broadcastRemove(sw, victim.id);
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private static int removeAttached(ServerWorld world, WireStorage storage, List<WireRecord> attached, Vec3d hit, boolean removeAll) {
        if (attached.isEmpty()) {
            return 0;
        }
        if (removeAll) {
            int removed = 0;
            for (WireRecord record : attached) {
                storage.remove(record.id);
                WireNetworking.broadcastRemove(world, record.id);
                removed++;
            }
            return removed;
        }

        WireRecord closest = attached.stream()
                .min(Comparator.comparingDouble(record -> anchorDistanceForTarget(record, hit)))
                .orElse(null);
        if (closest == null) {
            return 0;
        }

        storage.remove(closest.id);
        WireNetworking.broadcastRemove(world, closest.id);
        return 1;
    }

    private static double anchorDistanceForTarget(WireRecord record, Vec3d hit) {
        Vec3d a = WireToolMath.anchorCenter(record.a);
        Vec3d b = WireToolMath.anchorCenter(record.b);
        return Math.min(a.squaredDistanceTo(hit), b.squaredDistanceTo(hit));
    }

    private static WireRecord nearestBySegment(WireStorage storage, Vec3d hit) {
        WireRecord victim = null;
        double bestDistance = Double.MAX_VALUE;
        for (WireRecord record : storage.all()) {
            Vec3d a = WireToolMath.anchorCenter(record.a);
            Vec3d b = WireToolMath.anchorCenter(record.b);
            double d = pointSegmentDistance(hit, a, b);
            double radius = 0.18;
            WireSim sim = WireManager.get(record.id);
            if (sim != null) {
                radius = Math.max(radius, sim.getHalfWidth() * 1.8);
            }
            if (d <= radius && d < bestDistance) {
                bestDistance = d;
                victim = record;
            }
        }
        return victim;
    }

    private static double clamp01(double x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    private static double projectParam(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-12) return 0.0;
        return p.subtract(a).dotProduct(ab) / len2;
    }

    private static double pointSegmentDistance(Vec3d p, Vec3d a, Vec3d b) {
        double t = clamp01(projectParam(p, a, b));
        Vec3d c = a.lerp(b, t);
        return p.distanceTo(c);
    }
}
