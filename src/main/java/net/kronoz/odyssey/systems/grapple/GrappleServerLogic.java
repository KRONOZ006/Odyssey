package net.kronoz.odyssey.systems.grapple;

import net.kronoz.odyssey.entity.GrappleHookEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GrappleServerLogic {
    private static final Map<UUID, GrappleHookEntity> ACTIVE = new HashMap<>();

    public static boolean hasHook(PlayerEntity p) {
        return p instanceof ServerPlayerEntity sp && ACTIVE.containsKey(sp.getUuid());
    }
    public static void setHook(PlayerEntity p, GrappleHookEntity hook) {
        if (p instanceof ServerPlayerEntity sp) ACTIVE.put(sp.getUuid(), hook);
    }
    public static void detach(PlayerEntity p) {
        if (p instanceof ServerPlayerEntity sp) detach(sp);
    }
    public static void syncToClient(Entity p, GrappleHookEntity hook) {
        if (!(p instanceof ServerPlayerEntity sp)) return;
        Vec3d a = hook.anchor;
        int entId = hook.latchedEntityId;
        GrappleNetworking.sendTo(sp, new GrapplePayloads.SyncStateS2C(hook.latched, entId, a.x, a.y, a.z, hook.ropeLength));
    }

    static boolean hasHook(ServerPlayerEntity p) { return ACTIVE.containsKey(p.getUuid()); }
    static GrappleHookEntity getHook(ServerPlayerEntity p) { return ACTIVE.get(p.getUuid()); }

    static void detach(ServerPlayerEntity p) {
        GrappleHookEntity hook = ACTIVE.remove(p.getUuid());
        if (hook != null && hook.isAlive()) hook.discard();
        GrappleNetworking.sendTo(p, new GrapplePayloads.SyncStateS2C(false, -1, 0, 0, 0, 0));
    }

    static void fling(ServerPlayerEntity p) {
        GrappleHookEntity hook = getHook(p);
        if (hook == null || !hook.latched) return;

        if (hook.latchedEntityId != -1) {
            Entity e = p.getServerWorld().getEntityById(hook.latchedEntityId);
            if (e != null && e.isAlive()) {
                Vec3d dir = p.getPos().subtract(e.getPos()).normalize();
                Vec3d impulse = dir.multiply(2.4);
                Vec3d nv = e.getVelocity().add(impulse);
                double cap = 2.2;
                if (nv.length() > cap) nv = nv.normalize().multiply(cap);
                e.setVelocity(nv);
                e.velocityModified = true;
            }
            return;
        }

        Vec3d forward = p.getRotationVec(1f).normalize();
        Vec3d tangent = forward;
        Vec3d impulse = tangent.multiply(2.2).add(0, 0.2, 0);
        Vec3d nv = p.getVelocity().add(impulse);
        double cap = 2.4;
        if (nv.length() > cap) nv = nv.normalize().multiply(cap);
        p.setVelocity(nv);
        p.velocityModified = true;
    }
}
