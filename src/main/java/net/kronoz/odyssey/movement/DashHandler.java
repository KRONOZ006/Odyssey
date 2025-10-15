// src/main/java/net/kronoz/odyssey/player/DashHandler.java
package net.kronoz.odyssey.movement;

import net.kronoz.odyssey.net.DashC2SPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DashHandler {
    private static final Map<UUID, Long> CD = new HashMap<>();
    private static final long COOLDOWN_MS = 650;
    private static final double BASE = 1.95;
    private static final double CARRY = 0.08;
    private static final double MAX_VY = 1.6;
    private static final double MAX_VH = 2.4;

    public static void onDashPacket(ServerPlayerEntity p, DashC2SPayload pl) {
        long now = System.currentTimeMillis();
        if (now - CD.getOrDefault(p.getUuid(), 0L) < COOLDOWN_MS) return;

        Vec3d dir = new Vec3d(pl.lx(), pl.ly(), pl.lz());
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();

        Vec3d carry = p.getVelocity().multiply(CARRY);
        double gain = BASE * pl.speed();

        Vec3d dash = dir.multiply(gain);
        double vh = Math.hypot(dash.x, dash.z);
        if (vh > MAX_VH) {
            double s = MAX_VH / vh;
            dash = new Vec3d(dash.x * s, dash.y, dash.z * s);
        }
        if (Math.abs(dash.y) > MAX_VY) {
            dash = new Vec3d(dash.x, Math.copySign(MAX_VY, dash.y), dash.z);
        }

        p.setVelocity(carry.x + dash.x, Math.max(carry.y, pl.up() + dash.y), carry.z + dash.z);
        p.velocityModified = true;
        p.getWorld().playSound(null, p.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                p.getSoundCategory(), 0.7f, 1.45f);

        CD.put(p.getUuid(), now);
        p.getItemCooldownManager().set(net.minecraft.item.Items.AIR, (int)(COOLDOWN_MS / 50));
    }
}
