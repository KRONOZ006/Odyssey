package net.kronoz.odyssey.systems.slide;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public final class SlideClientState {
    private static boolean active;
    private static int ticksLeft;
    private static float lerp;

    public static void begin(int duration) {
        active = true;
        ticksLeft = duration;
        lerp = 0.35f;
    }

    public static void end() {
        active = false;
        ticksLeft = 0;
        lerp = 0f;
    }

    public static void clientTick() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { end(); return; }
        if (--ticksLeft <= 0) { end(); return; }
        Vec3d v = mc.player.getVelocity();
        double s = Math.sqrt(v.x * v.x + v.z * v.z);
        float target = (float)Math.max(0.25, Math.min(0.7, 0.25 + s * 0.35));
        lerp += (target - lerp) * 0.22f;
    }

    public static boolean isActive() { return active; }
    public static float lerpFactor() { return lerp; }
}
