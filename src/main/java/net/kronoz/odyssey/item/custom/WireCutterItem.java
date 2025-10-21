package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.systems.physics.wire.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

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

        WireRecord victim = null;
        double bestD = Double.MAX_VALUE;
        for (WireRecord r : WireStorage.get(sw).all()) {
            Vec3d a = WireToolMath.anchorCenter(r.a);
            Vec3d b = WireToolMath.anchorCenter(r.b);
            double d = pointSegmentDistance(hit, a, b);
            double radius = 0.15;
            var sim = WireManager.get(r.id);
            if (sim != null) radius = Math.max(radius, sim.getHalfWidth() * 1.75);
            if (d < bestD && d <= radius) {
                bestD = d;
                victim = r;
            }
        }

        if (victim == null) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        Vec3d A = WireToolMath.anchorCenter(victim.a);
        Vec3d B = WireToolMath.anchorCenter(victim.b);
        double t = clamp01(projectParam(hit, A, B));
        Vec3d mid = A.lerp(B, t);

        WireAnchor ghost = WireToolMath.ghostAt(mid);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        WireRecord r1 = new WireRecord(
                id1, victim.defId,
                victim.a, victim.aPinned,
                ghost, false
        );
        WireRecord r2 = new WireRecord(
                id2, victim.defId,
                ghost, false,
                victim.b, victim.bPinned
        );

        WireStorage st = WireStorage.get(sw);
        st.remove(victim.id);
        st.put(r1);
        st.put(r2);

        Vec3d pA = WireToolMath.anchorCenter(r1.a);
        Vec3d pG = WireToolMath.anchorCenter(r1.b);
        WireManager.remove(victim.id);
        WireManager.ensure(r1.id, WireDef.defaultCable(victim.defId), pA, pG);
        WireManager.get(r1.id).setPinned(r1.aPinned, r1.bPinned);

        Vec3d pG2 = WireToolMath.anchorCenter(r2.a);
        Vec3d pB = WireToolMath.anchorCenter(r2.b);
        WireManager.ensure(r2.id, WireDef.defaultCable(victim.defId), pG2, pB);
        WireManager.get(r2.id).setPinned(r2.aPinned, r2.bPinned);

        return TypedActionResult.success(user.getStackInHand(hand));
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
