package net.kronoz.odyssey.systems.cam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Screen shake (pas de sway):
 * - bruit lissé qui change de cible toutes quelques ticks (pas de sinus constant)
 * - priorité au gauche↔droite (X caméra), vertical très léger
 * - un peu de roll mais fortement réduit
 * - "kicks" empilables, avec enveloppe douce
 *
 * Utilisation:
 *   ScreenShake.kick(0.8f, 28);                 // shake simple
 *   ScreenShake.kick(0.7f, 24, 4f);             // avec petit roll-impulse en degrés
 *   ScreenShake.kickFromEvent(dist2, 96, 0.35f, 1.0f, 14, 40, 8f); // helper distance
 *   // Appeler update() 1x par tick caméra (voir mixin Camera ci-dessous)
 *   // Appliquer getShakeX/Y() à la position caméra, et getRollDeg() au MatrixStack.
 */
public final class ScreenShake {

    private static final Random RNG = Random.create();
    private static long lastWorldTick = Long.MIN_VALUE;

    private static float outX = 0f;        // gauche↔droite (sera projeté sur "right")
    private static float outY = 0f;        // haut/bas léger
    private static float outRollDeg = 0f;  // très réduit
    private static boolean active = false;

    private static float rollDeg = 0f, rollVel = 0f;

    private static final float POS_X_MAX_PER_INTENSITY = 0.10f; // blocs à intensité=1 (latéral)
    private static final float POS_Y_MAX_PER_INTENSITY = 0.015f; // vertical minimal
    private static final float ROLL_MAX_PER_INTENSITY  = 1.2f;   // degrés à intensité=1

    private static final float OUT_SMOOTH = 0.35f;

    private static final float ROLL_VEL_DAMP = 0.88f;
    private static final float ROLL_POS_DAMP = 0.90f;

    private static final List<Kick> KICKS = new ArrayList<>(4);

    private static final class Kick {
        int totalTicks;
        int leftTicks;

        float curX = 0f, tgtX = 0f;
        float curY = 0f, tgtY = 0f;
        float curR = 0f, tgtR = 0f;

        final float alphaX;
        final float alphaY;
        final float alphaR;

        int sampleEveryTicks;
        int sampleCountdown;

        final float intensity;
        final float posScaleX;
        final float posScaleY;
        final float rollScale;

        Kick(float intensity, int durationTicks) {
            this.totalTicks = Math.max(2, durationTicks);
            this.leftTicks  = this.totalTicks;

            this.intensity = MathHelper.clamp(intensity, 0f, 2f);
            this.posScaleX = POS_X_MAX_PER_INTENSITY;
            this.posScaleY = POS_Y_MAX_PER_INTENSITY;
            this.rollScale = ROLL_MAX_PER_INTENSITY;

            this.sampleEveryTicks = 2 + RNG.nextInt(3);
            this.sampleCountdown  = 0;

            this.alphaX = 0.45f;
            this.alphaY = 0.35f;
            this.alphaR = 0.35f;

            reseedTargets();
            this.curX = this.tgtX * 0.6f;
            this.curY = this.tgtY * 0.6f;
            this.curR = this.tgtR * 0.6f;
        }

        private void reseedTargets() {
            this.tgtX = (RNG.nextFloat() * 2f - 1f);
            this.tgtY = (RNG.nextFloat() * 2f - 1f) * 0.5f;
            this.tgtR = (RNG.nextFloat() * 2f - 1f) * 0.6f;
        }

        boolean tickAndAccumulate(Accumulator acc) {
            if (leftTicks <= 0) return false;
            leftTicks--;

            float prog = 1f - (leftTicks / (float) totalTicks);
            float env = (float) Math.sin(prog * Math.PI);
            if (--sampleCountdown <= 0) {
                sampleCountdown = sampleEveryTicks;
                reseedTargets();
            }

            curX = lerp(curX, tgtX, alphaX);
            curY = lerp(curY, tgtY, alphaY);
            curR = lerp(curR, tgtR, alphaR);

            float a = env * intensity;

            acc.sumX    += curX * (a * posScaleX);
            acc.sumY    += curY * (a * posScaleY);
            acc.sumRoll += curR * (a * rollScale);

            return leftTicks > 0;
        }
    }

    private static final class Accumulator {
        float sumX, sumY, sumRoll;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static void update() {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            reset();
            return;
        }
        long wt = mc.world.getTime();
        if (wt == lastWorldTick) return;
        lastWorldTick = wt;

        Accumulator acc = new Accumulator();
        Iterator<Kick> it = KICKS.iterator();
        while (it.hasNext()) {
            if (!it.next().tickAndAccumulate(acc)) it.remove();
        }

        rollVel *= ROLL_VEL_DAMP;
        rollDeg += rollVel;
        rollDeg *= ROLL_POS_DAMP;

        rollDeg = MathHelper.clamp(rollDeg, -6f, 6f);
        if (Math.abs(rollDeg) < 0.01f && Math.abs(rollVel) < 0.01f) {
            rollDeg = 0f; rollVel = 0f;
        }

        outX = MathHelper.lerp(OUT_SMOOTH, outX, acc.sumX);
        outY = MathHelper.lerp(OUT_SMOOTH, outY, acc.sumY);
        outRollDeg = MathHelper.lerp(OUT_SMOOTH, outRollDeg, acc.sumRoll + rollDeg);

        active = (Math.abs(outX) > 0.001f) || (Math.abs(outY) > 0.001f) || (Math.abs(outRollDeg) > 0.02f);
    }


    public static void kick(float intensity, int durationTicks) {
        KICKS.add(new Kick(intensity, durationTicks));
    }

    public static void kick(float intensity, int durationTicks, float rollImpulseDeg) {
        KICKS.add(new Kick(intensity, durationTicks));
        rollVel += rollImpulseDeg * 0.35f; // roll très atténué
    }

    public static void kickFromEvent(double distanceSq, double radius, float minIntensity, float maxIntensity, int minTicks, int maxTicks, float maxRollImpulseDeg) {
        double r2 = radius * radius;
        if (distanceSq > r2) return;
        double d = Math.sqrt(distanceSq);
        float t = (float) (1.0 - (d / radius));
        float intensity = MathHelper.lerp(t, minIntensity, maxIntensity);
        int dur = MathHelper.floor(MathHelper.lerp(t, minTicks, maxTicks));
        float roll = (RNG.nextBoolean() ? 1f : -1f) * (maxRollImpulseDeg * t);
        kick(intensity, Math.max(2, dur), roll);
    }

    public static boolean isShaking() { return active; }
    public static float getShakeX() { return outX; }
    public static float getShakeY() { return outY; }
    public static float getRollDeg() { return outRollDeg; }

    public static void reset() {
        lastWorldTick = Long.MIN_VALUE;
        KICKS.clear();
        outX = outY = outRollDeg = 0f;
        rollDeg = rollVel = 0f;
        active = false;
    }

    private ScreenShake() {}
}
