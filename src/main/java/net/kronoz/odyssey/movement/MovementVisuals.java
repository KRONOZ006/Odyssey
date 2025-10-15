// src/main/java/net/kronoz/odyssey/client/movement/MovementVisuals.java
package net.kronoz.odyssey.movement;

import net.kronoz.odyssey.movement.WallRun;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class MovementVisuals {
    private static float rollDeg = 0f;
    private static float rollTarget = 0f;
    private static double fovScale = 1.0;

    public static void clientTick(ClientPlayerEntity p) {
        if (p != null) {
            Vec3d v = p.getVelocity();
            double speed = v.length();
            double target = 1.0 + Math.min(0.14, speed * 0.12);
            double alpha = 0.15;
            fovScale += (target - fovScale) * alpha;
        }
        float lerp = 0.10f;
        rollDeg += (rollTarget - rollDeg) * lerp;
        if (Math.abs(rollTarget - rollDeg) < 0.01f) rollDeg = rollTarget;
    }

    public static void updateWallTilt(ClientPlayerEntity p, WallRun.WallState s) {
        if (s != null && s.active() && s.normal != null) {
            Vec3d look = p.getRotationVec(1).multiply(1,0,1);
            if (look.lengthSquared() < 1e-6) look = new Vec3d(1,0,0);
            Vec3d right = new Vec3d(look.z, 0, -look.x).normalize();
            double side = right.dotProduct(s.normal);
            rollTarget = side >= 0 ? -30f : 30f;
        } else rollTarget = 0f;
    }

    public static double fovScale() { return fovScale; }
    public static float rollDegrees() { return rollDeg; }
}
