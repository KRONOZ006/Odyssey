package net.kronoz.odyssey.systems.slide;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class SlideServer {
    private static final Object2IntOpenHashMap<UUID> T = new Object2IntOpenHashMap<>();
    private static final Object2IntOpenHashMap<UUID> CD = new Object2IntOpenHashMap<>();

    private static final int DURATION = 14;
    private static final int COOLDOWN = 28;

    private static final double MIN_START = 0.28;
    private static final double MAX_START = 1.55;
    private static final double MAX_SPEED = 8.85;

    private static final float F_GROUND = 0.985f;
    private static final float F_ICE = 0.995f;
    private static final float F_AIR = 0.995f;

    public static void init() {
        PayloadTypeRegistry.playC2S().register(SlideRequestPayload.ID, SlideRequestPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SlideRequestPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            if (p == null || p.isSpectator() || p.isFallFlying()) return;
            UUID id = p.getUuid();
            if (CD.getOrDefault(id, 0) > 0 || T.getOrDefault(id, 0) > 0) return;

            Vec3d v = p.getVelocity();
            Vec3d h = new Vec3d(v.x, 0, v.z);
            double sp = h.length();

            if (sp < 1.0E-4) {
                Vec3d look = p.getRotationVec(1f);
                h = new Vec3d(look.x, 0, look.z);
                sp = 0.35;
            }

            Vec3d dir = h.lengthSquared() > 1.0E-6 ? h.normalize() : new Vec3d(0, 0, 0);
            double start = Math.max(MIN_START, Math.min(MAX_START, sp * 3.0));
            Vec3d boost = dir.multiply(start);

            Vec3d nv = new Vec3d(
                    clampMag(v.x + boost.x, MAX_SPEED),
                    v.y,
                    clampMag(v.z + boost.z, MAX_SPEED)
            );

            p.setVelocity(nv);
            p.velocityModified = true;

            T.put(id, DURATION);
            CD.put(id, COOLDOWN);
        });

        ServerTickEvents.END_SERVER_TICK.register(SlideServer::tick);
    }

    private static void tick(MinecraftServer server) {
        CD.replaceAll((k, v) -> Math.max(0, v - 1));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            int left = T.getOrDefault(id, 0);
            if (left <= 0) continue;

            boolean onGround = p.isOnGround();
            BlockPos below = p.getBlockPos().down();
            BlockState bs = p.getWorld().getBlockState(below);
            float f = !onGround ? F_AIR : (bs.isIn(BlockTags.ICE) ? F_ICE : F_GROUND);

            Vec3d v = p.getVelocity();
            Vec3d damp = new Vec3d(v.x * f, v.y, v.z * f);

            if (new Vec3d(damp.x, 0, damp.z).lengthSquared() < 0.004) {
                T.put(id, 0);
                continue;
            }

            p.setVelocity(damp);
            p.velocityModified = true;

            left -= 1;
            if (left <= 0) {
                T.put(id, 0);
            } else {
                T.put(id, left);
            }
        }
    }

    private static double clampMag(double val, double max) {
        double s = Math.copySign(1.0, val);
        double a = Math.abs(val);
        return a > max ? s * max : val;
    }
}
