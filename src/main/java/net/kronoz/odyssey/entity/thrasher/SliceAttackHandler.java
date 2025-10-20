package net.kronoz.odyssey.entity.thrasher;


import net.kronoz.odyssey.net.DashC2SPayload;
import net.kronoz.odyssey.net.SliceAttackC2SPayload;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SliceAttackHandler {
    private static final Map<UUID, Long> CD = new HashMap<>();
    private static final long COOLDOWN_MS = 650;
    private static final double BASE = 1.95;
    private static final double CARRY = 0.08;
    private static final double MAX_VY = 1.6;
    private static final double MAX_VH = 2.4;

    public static void onSlicePacket(ServerPlayerEntity p, SliceAttackC2SPayload pl) {
        Entity riddenEntity = p.getVehicle();
        riddenEntity.kill();

        Vec3d dir = new Vec3d(pl.lx(), pl.ly(), pl.lz());
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
    }
}