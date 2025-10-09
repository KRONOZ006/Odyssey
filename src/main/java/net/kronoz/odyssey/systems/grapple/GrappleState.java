package net.kronoz.odyssey.systems.grapple;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GrappleState {
    public boolean latched = false;
    public Vec3d anchorPos = Vec3d.ZERO;
    public int latchedEntityId = -1;
    public double ropeLength = 6.0;

    private static final Map<UUID, GrappleState> STATES = new ConcurrentHashMap<>();

    public static GrappleState get(PlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> new GrappleState());
    }

    public static void clear(PlayerEntity p) { STATES.remove(p.getUuid()); }
}
