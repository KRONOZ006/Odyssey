// src/main/java/net/kronoz/odyssey/client/movement/MovementVisuals.java
package net.kronoz.odyssey.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class MovementVisuals {
    private static float rollDeg = 0f;
    private static float rollTarget = 0f;
    private static float wallPoseWeight = 0f;
    private static float wallPoseTarget = 0f;
    private static float wallPoseSide = 0f;
    private static double fovScale = 1.0;

    public static void clientTick(ClientPlayerEntity p) {
        if (p != null) {
            Vec3d v = p.getVelocity();
            double speed = v.length();
            double target = 1.0 + Math.min(0.14, speed * 0.12);
            double alpha = 0.15;
            fovScale += (target - fovScale) * alpha;
        } else {
            // If there is no local player, aggressively settle visuals back to identity.
            fovScale += (1.0 - fovScale) * 0.28;
            rollTarget = 0f;
            wallPoseTarget = 0f;
        }
        float lerp = 0.10f;
        rollDeg += (rollTarget - rollDeg) * lerp;
        if (Math.abs(rollTarget - rollDeg) < 0.01f) rollDeg = rollTarget;

        float poseLerp = wallPoseTarget > wallPoseWeight ? 0.22f : 0.16f;
        wallPoseWeight += (wallPoseTarget - wallPoseWeight) * poseLerp;
        if (Math.abs(wallPoseTarget - wallPoseWeight) < 0.01f) {
            wallPoseWeight = wallPoseTarget;
        }
        if (wallPoseWeight <= 0.001f && wallPoseTarget <= 0.001f) {
            wallPoseWeight = 0f;
            wallPoseSide = 0f;
        }
    }

    public static void updateWallTilt(ClientPlayerEntity p, WallRun.WallState s) {
        if (s != null && s.active() && s.normal != null) {
            Vec3d look = p.getRotationVec(1).multiply(1,0,1);
            if (look.lengthSquared() < 1e-6) look = new Vec3d(1,0,0);
            Vec3d right = new Vec3d(look.z, 0, -look.x).normalize();
            double side = right.dotProduct(s.normal);
            rollTarget = side >= 0 ? -30f : 30f;
            wallPoseTarget = 1f;
            wallPoseSide = side >= 0 ? 1f : -1f;
        } else {
            rollTarget = 0f;
            wallPoseTarget = 0f;
        }
    }

    public static double fovScale() { return fovScale; }
    public static float rollDegrees() { return rollDeg; }
    public static float wallPoseWeight() { return wallPoseWeight; }
    public static float wallPoseSide() { return wallPoseSide; }

    public static void reset() {
        rollDeg = 0f;
        rollTarget = 0f;
        wallPoseWeight = 0f;
        wallPoseTarget = 0f;
        wallPoseSide = 0f;
        fovScale = 1.0;
    }
}
