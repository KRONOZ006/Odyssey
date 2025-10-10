package net.kronoz.odyssey.systems.grapple;

import net.kronoz.odyssey.entity.GrappleHookEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class GrappleServerLogic {

    public static boolean hasHook(PlayerEntity p) {
        return SimpleGrappleServer.hasHook(p);
    }

    public static void setHook(PlayerEntity p, GrappleHookEntity hook) {
        SimpleGrappleServer.setHook(p, hook);
    }

    public static void detach(PlayerEntity p) {
        SimpleGrappleServer.detach(p);
    }

    public static void syncToClient(Entity p, GrappleHookEntity hook) {
    }

    public static void fling(ServerPlayerEntity p) {
    }
}
