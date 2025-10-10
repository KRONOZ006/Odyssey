package net.kronoz.odyssey.systems.grapple;

import net.kronoz.odyssey.entity.GrappleHookEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SimpleGrappleServer {
    private static final Map<UUID, GrappleHookEntity> ACTIVE = new HashMap<>();

    public static boolean hasHook(PlayerEntity p) {
        return p instanceof ServerPlayerEntity sp && ACTIVE.containsKey(sp.getUuid());
    }

    public static void setHook(PlayerEntity p, GrappleHookEntity hook) {
        if (p instanceof ServerPlayerEntity sp) ACTIVE.put(sp.getUuid(), hook);
    }

    public static void detach(PlayerEntity p) {
        if (p instanceof ServerPlayerEntity sp) {
            GrappleHookEntity hook = ACTIVE.remove(sp.getUuid());
            if (hook != null && hook.isAlive()) hook.discard();
        }
    }
}
