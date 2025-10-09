package net.kronoz.odyssey.systems.grapple;

import net.kronoz.odyssey.entity.GrappleHookEntity;
import net.kronoz.odyssey.systems.grapple.GrapplePayloads;
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

    static void forcePull(ServerPlayerEntity p) {
        GrappleHookEntity hook = getHook(p);
        if (hook == null || !hook.latched) return;

        double dist = p.getPos().distanceTo(hook.anchor);
        double targetLen = Math.max(hook.minRopeLength, dist - 2.5);
        hook.ropeLength = Math.max(hook.minRopeLength, Math.min(hook.ropeLength, targetLen));

        if (hook.latchedEntityId != -1) {
            Entity latchedEnt = p.getServerWorld().getEntityById(hook.latchedEntityId);
            if (latchedEnt != null && latchedEnt.isAlive()) {
                Vec3d dirToPlayer = p.getPos().subtract(latchedEnt.getPos()).normalize();
                Vec3d impulse = dirToPlayer.multiply(1.8);
                latchedEnt.addVelocity(impulse.x, impulse.y, impulse.z);
                latchedEnt.velocityModified = true;
                return;
            }
        }

        Vec3d dir = hook.anchor.subtract(p.getPos()).normalize();
        Vec3d impulse = dir.multiply(2.2);
        Vec3d newVel = p.getVelocity().add(impulse);
        double max = 1.6;
        if (newVel.length() > max) newVel = newVel.normalize().multiply(max);
        p.setVelocity(newVel);
        p.velocityModified = true;
    }
}