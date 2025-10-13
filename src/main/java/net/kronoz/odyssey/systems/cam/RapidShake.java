package net.kronoz.odyssey.systems.cam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Rapid random camera jitter (no sway):
 * - picks a new random direction every few ticks (configurable Hz)
 * - lerps quickly toward that target for "crunchy" shake
 * - offsets in camera space: X along RIGHT, Y along UP
 * - tiny roll (configurable)
 *
 * Use:
 *   RapidShake.enable(true);
 *   RapidShake.configure(0.65f, 12f, 0.10f, 0.02f, 1.0f, 0.55f); // baseIntensity, Hz, maxHoriz, maxVert, maxRollDeg, snappiness
 *   RapidShake.pulse(0.6f, 20); // optional burst on top of base
 *
 * Call update() from your Camera mixin each tick; read getX()/getY()/getRollDeg().
 */
public final class RapidShake {
    private static final Random RNG = Random.create();

    // --- config (editable at runtime) ---
    private static boolean enabled = false;
    private static float baseIntensity = 0.6f;    // 0..1 (scales amplitudes)
    private static float freqHz       = 12f;      // how often we pick a new target (per second)
    private static float maxHoriz     = 0.10f;    // blocks at intensity=1 (camera-right)
    private static float maxVert      = 0.02f;    // blocks at intensity=1 (camera-up)
    private static float maxRollDeg   = 1.0f;     // deg at intensity=1 (very small)
    private static float snappiness   = 0.55f;    // 0..1 lerp to new target per tick (higher = snappier)
    private static float outSmooth    = 0.30f;    // low-pass for final output (0..1)

    // --- state ---
    private static long lastWorldTick = Long.MIN_VALUE;

    private static int sampleTicks = 1;   // derived from freqHz
    private static int sampleCountdown = 0;

    private static float tgtX = 0f, curX = 0f;   // X = camera-right offset (blocks)
    private static float tgtY = 0f, curY = 0f;   // Y = camera-up offset (blocks)
    private static float tgtR = 0f, curR = 0f;   // roll degrees (tiny)

    private static float outX = 0f, outY = 0f, outR = 0f;
    private static boolean active = false;

    private static float pulseIntensity = 0f;
    private static int   pulseTicksLeft = 0;
    private static int baseTicksLeft = 0;

    public static void enable(boolean on) { enabled = on; }
    public static void configure(float baseIntensity01, float hz, float maxHorizBlocks, float maxVertBlocks, float maxRollDegrees, float snappiness01) {
        baseIntensity = MathHelper.clamp(baseIntensity01, 0f, 1f);
        freqHz        = MathHelper.clamp(hz, 1f, 60f);
        maxHoriz      = Math.max(0f, maxHorizBlocks);
        maxVert       = Math.max(0f, maxVertBlocks);
        maxRollDeg    = Math.max(0f, maxRollDegrees);
        snappiness    = MathHelper.clamp(snappiness01, 0.05f, 0.95f);

        sampleTicks = Math.max(1, Math.round(20f / freqHz));
        sampleCountdown = 0;
    }
    public static void setOutputSmoothing(float smooth01) {
        outSmooth = MathHelper.clamp(smooth01, 0f, 0.95f);
    }

    public static void pulse(float addIntensity01, int durationTicks) {
        pulseIntensity = Math.max(pulseIntensity, MathHelper.clamp(addIntensity01, 0f, 1f));
        pulseTicksLeft = Math.max(pulseTicksLeft, Math.max(1, durationTicks));
    }

    public static void update() {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            reset();
            return;
        }
        long wt = mc.world.getTime();
        if (wt == lastWorldTick) return;
        lastWorldTick = wt;

        // pulse decay
        if (pulseTicksLeft > 0) {
            pulseTicksLeft--;
            if (pulseTicksLeft <= 0) pulseIntensity = 0f;
        }

        float eff = enabled ? MathHelper.clamp(baseIntensity + pulseIntensity, 0f, 1f) : 0f;
        if (baseTicksLeft > 0) {
            baseTicksLeft--;
            if (baseTicksLeft == 0) baseIntensity = 0f; // auto-stop
        }

        if (eff <= 0f) {
            curX = lerp(curX, 0f, 0.6f);
            curY = lerp(curY, 0f, 0.6f);
            curR = lerp(curR, 0f, 0.6f);
            outX = lerp(outX, 0f, 0.5f);
            outY = lerp(outY, 0f, 0.5f);
            outR = lerp(outR, 0f, 0.5f);
            active = Math.abs(outX) > 0.0008f || Math.abs(outY) > 0.0008f || Math.abs(outR) > 0.02f;
            return;
        }


        // sample new random target every N ticks
        if (--sampleCountdown <= 0) {
            sampleCountdown = sampleTicks;
            float dirX = RNG.nextFloat() * 2f - 1f; // -1..1
            float dirY = RNG.nextFloat() * 2f - 1f; // -1..1
            // normalize for uniform directions
            float len = Math.max(1e-5f, MathHelper.sqrt(dirX * dirX + dirY * dirY));
            dirX /= len; dirY /= len;

            // random magnitude with slight bias to bigger hops
            float mag = 0.5f + 0.5f * RNG.nextFloat(); // 0.5..1.0
            tgtX = dirX * (maxHoriz * eff * mag);
            tgtY = dirY * (maxVert  * eff * mag);
            tgtR = (RNG.nextFloat() * 2f - 1f) * (maxRollDeg * eff * 0.85f); // small roll
        }

        // chase targets (snappy)
        curX = lerp(curX, tgtX, snappiness);
        curY = lerp(curY, tgtY, snappiness);
        curR = lerp(curR, tgtR, snappiness);

        // smooth outputs
        outX = lerp(outX, curX, outSmooth);
        outY = lerp(outY, curY, outSmooth);
        outR = lerp(outR, curR, outSmooth);

        // tiny clamps (parano)
        outX = MathHelper.clamp(outX, -maxHoriz, maxHoriz);
        outY = MathHelper.clamp(outY, -maxVert,  maxVert);
        outR = MathHelper.clamp(outR, -maxRollDeg, maxRollDeg);

        active = Math.abs(outX) > 0.0008f || Math.abs(outY) > 0.0008f || Math.abs(outR) > 0.02f;
    }

    public static void enableTimed(float intensity01, int durationTicks) {
        enable(true);
        baseIntensity = MathHelper.clamp(intensity01, 0f, 1f);
        baseTicksLeft = Math.max(1, durationTicks);
    }

    public static boolean isActive()   { return active; }
    public static float getX()         { return outX; }       // along camera RIGHT, meters/blocks
    public static float getY()         { return outY; }       // along camera UP
    public static float getRollDeg()   { return outR; }       // degrees

    public static void reset() {
        lastWorldTick = Long.MIN_VALUE;
        sampleCountdown = 0;
        tgtX = tgtY = tgtR = 0f;
        curX = curY = curR = 0f;
        outX = outY = outR = 0f;
        pulseIntensity = 0f;
        pulseTicksLeft = 0;
        active = false;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private RapidShake() {}
}
