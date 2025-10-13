package net.kronoz.odyssey.systems.cam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CameraSway {

    // --- tuning (un peu plus intense que ta version) ---
    private static final float GAIT_HZ             = 0.40f;
    private static final double GAIT_STEP_PER_TICK = (Math.PI * 2.0) * GAIT_HZ / 20.0;
    private static final int   RESET_AFTER_IDLE_TICKS = 8;

    private static final float GAIT_ROLL_MAX_DEG  = 4.2f; // 3.2 -> 4.2 (un peu plus de gauche/droite)
    private static final float GAIT_SPEED_TO_AMPL = 0.26f;
    private static final float GAIT_SIDE_WEIGHT   = 0.85f; // 0.80 -> 0.85 (strafe compte un peu plus)
    private static final float GAIT_AIR_DAMP      = 0.35f;

    private static final float PITCH_FACTOR = 13f; // 10 -> 13
    private static final float ROLL_FACTOR  = 18f; // 14 -> 18

    private static final float PITCH_CLAMP = 3.6f; // 3.2 -> 3.6
    private static final float ROLL_CLAMP  = 7.0f; // 6.2 -> 7.0

    private static final float JUMP_PITCH_KICK = -2.4f;
    private static final float LAND_PITCH_KICK =  3.2f;
    private static final float JUMP_ROLL_KICK  =  1.1f;
    private static final float LAND_ROLL_KICK  = -1.3f;
    private static final float J_DAMP = 0.86f;

    // Regard (on garde raisonnable pour pas être trop agressif)
    private static final float LOOK_SMOOTH_ALPHA = 0.20f;
    private static final float LOOK_DEADZONE_DPS = 10f;
    private static final float LOOK_MAX_DPS      = 1080f;
    private static final float LOOK_ROLL_GAIN  = 0.024f; // léger, le boost vient surtout du mouvement
    private static final float LOOK_PITCH_GAIN = 0.018f;

    private static final float EASE_PITCH_FREQ = 5.0f;
    private static final float EASE_ROLL_FREQ  = 5.0f;
    private static final float EASE_ZETA       = 0.70f;
    private static final float DT = 1f / 20f;

    // ---- état par entité (clé = entityId) ----
    private static final class State {
        long lastTick = Long.MIN_VALUE;

        // sorties
        float outPitchDeg = 0f;
        float outRollDeg  = 0f;
        boolean outActive = false;

        // marche
        double gaitPhase = 0.0;
        int    idleTicks = 0;

        // sol
        boolean wasOnGround = true;

        // look
        float lastYawSnap = Float.NaN;
        float lastPitchSnap = Float.NaN;
        float lsYawDps = 0f, lsPitchDps = 0f;

        // sauts
        float jumpPitch = 0f, jumpRoll = 0f;

        // easing ressort
        float easedPitch = 0f, easedPitchVel = 0f;
        float easedRoll  = 0f, easedRollVel  = 0f;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static int pruneTicker = 0;

    private static State st(Entity e) {
        return STATES.computeIfAbsent(e.getId(), k -> new State());
    }

    public static void update(Entity e, float tickDelta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || e == null) return;

        // prune léger toutes les 80 ticks pour éviter l’accumulation
        if ((++pruneTicker & 79) == 0) {
            Set<Integer> keep = new HashSet<>();
            for (var p : mc.world.getPlayers()) keep.add(p.getId());
            STATES.keySet().removeIf(id -> !keep.contains(id));
        }

        State s = st(e);

        long tick = mc.world.getTime();
        if (tick == s.lastTick) return; // déjà fait pour cette entité ce tick
        s.lastTick = tick;

        Vec3d v = e.getVelocity();
        double speedXZ = Math.sqrt(v.x * v.x + v.z * v.z);

        Vec3d lookPrevTick = e.getRotationVec(0f);
        Vec3d fwd2D = new Vec3d(lookPrevTick.x, 0, lookPrevTick.z);
        if (fwd2D.lengthSquared() < 1e-6) fwd2D = new Vec3d(0, 0, 1); else fwd2D = fwd2D.normalize();
        Vec3d right = new Vec3d(-fwd2D.z, 0, fwd2D.x);

        float forward = (float) v.dotProduct(fwd2D);
        float strafe  = (float) v.dotProduct(right);

        float yawNow   = e.getYaw();
        float pitchNow = e.getPitch();
        if (Float.isNaN(s.lastYawSnap))  s.lastYawSnap = yawNow;
        if (Float.isNaN(s.lastPitchSnap)) s.lastPitchSnap = pitchNow;

        float dYawDeg   = MathHelper.wrapDegrees(yawNow - s.lastYawSnap);
        float dPitchDeg = MathHelper.wrapDegrees(pitchNow - s.lastPitchSnap);
        s.lastYawSnap = yawNow;
        s.lastPitchSnap = pitchNow;

        float yawDpsRaw   = MathHelper.clamp(dYawDeg * 20f,   -LOOK_MAX_DPS, LOOK_MAX_DPS);
        float pitchDpsRaw = MathHelper.clamp(dPitchDeg * 20f, -LOOK_MAX_DPS, LOOK_MAX_DPS);

        s.lsYawDps   = s.lsYawDps   + LOOK_SMOOTH_ALPHA * (yawDpsRaw   - s.lsYawDps);
        s.lsPitchDps = s.lsPitchDps + LOOK_SMOOTH_ALPHA * (pitchDpsRaw - s.lsPitchDps);

        float yawAtten   = softDeadzoneAtten(Math.abs(s.lsYawDps),   LOOK_DEADZONE_DPS);
        float pitchAtten = softDeadzoneAtten(Math.abs(s.lsPitchDps), LOOK_DEADZONE_DPS);

        float lookRoll  = -(s.lsYawDps   * yawAtten)   * LOOK_ROLL_GAIN;
        float lookPitch = -(s.lsPitchDps * pitchAtten) * LOOK_PITCH_GAIN;

        float sprintBoost = (e.isSprinting() ? 1.2f : 1.0f);

        float p_base = -forward * PITCH_FACTOR * sprintBoost + lookPitch;
        float r_base =  strafe  * ROLL_FACTOR  * sprintBoost + lookRoll;

        boolean onGround    = e.isOnGround();
        boolean movingHoriz = speedXZ > 0.01f;

        if (movingHoriz && onGround) {
            s.idleTicks = 0;
            s.gaitPhase += GAIT_STEP_PER_TICK;
            if (s.gaitPhase >= Math.PI * 2.0) s.gaitPhase -= Math.PI * 2.0;
        } else {
            s.idleTicks++;
            if (s.idleTicks >= RESET_AFTER_IDLE_TICKS) {
                s.gaitPhase = 0.0;
                s.idleTicks = RESET_AFTER_IDLE_TICKS;
            }
        }

        float speedBps = (float) (speedXZ * 20.0);
        float ampl01 = MathHelper.clamp(speedBps * GAIT_SPEED_TO_AMPL, 0f, 1f);
        float moveSum = Math.abs(forward) + Math.abs(strafe);
        float dirW = moveSum < 1e-4f ? 0f
                : (Math.abs(forward) + GAIT_SIDE_WEIGHT * Math.abs(strafe)) / moveSum;
        float gaitAmpDeg = GAIT_ROLL_MAX_DEG * ampl01 * dirW * (onGround ? 1f : GAIT_AIR_DAMP);

        float s1 = (float) Math.sin(s.gaitPhase);
        float s2 = (float) Math.sin(2.0 * s.gaitPhase) * 0.12f;
        float r_gait = (s1 + s2) * gaitAmpDeg;

        if (s.wasOnGround && !onGround) {
            s.jumpPitch += JUMP_PITCH_KICK;
            s.jumpRoll  += JUMP_ROLL_KICK * Math.signum(strafe + 1e-6f);
        }
        if (!s.wasOnGround && onGround) {
            s.jumpPitch += LAND_PITCH_KICK;
            s.jumpRoll  += LAND_ROLL_KICK * Math.signum(strafe + 1e-6f);
        }
        s.wasOnGround = onGround;

        s.jumpPitch *= J_DAMP;
        s.jumpRoll  *= J_DAMP;

        float pitchTarget = p_base + s.jumpPitch;
        float rollTarget  = r_base + r_gait + s.jumpRoll;

        pitchTarget = MathHelper.clamp(pitchTarget, -PITCH_CLAMP, PITCH_CLAMP);
        rollTarget  = MathHelper.clamp(rollTarget,  -ROLL_CLAMP,  ROLL_CLAMP);

        float[] easedP = springStep(s.easedPitch, s.easedPitchVel, pitchTarget, EASE_PITCH_FREQ, EASE_ZETA, DT);
        s.easedPitch    = easedP[0];
        s.easedPitchVel = easedP[1];

        float[] easedR = springStep(s.easedRoll, s.easedRollVel, rollTarget, EASE_ROLL_FREQ, EASE_ZETA, DT);
        s.easedRoll    = easedR[0];
        s.easedRollVel = easedR[1];

        s.outPitchDeg = s.easedPitch;
        s.outRollDeg  = s.easedRoll;
        s.outActive   = (Math.abs(s.outPitchDeg) > 0.02f || Math.abs(s.outRollDeg) > 0.02f);
    }

    private static float[] springStep(float x, float v, float target, float omega, float zeta, float dt) {
        float ax = -2f * zeta * omega * v - (omega * omega) * (x - target);
        v += ax * dt;
        x += v * dt;
        return new float[]{ x, v };
    }

    private static float softDeadzoneAtten(float value, float deadzone) {
        if (value <= deadzone) return 0f;
        float t = MathHelper.clamp((value - deadzone) / deadzone, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // -------- getters par entité (multi-instances) --------
    public static float getPitchDegFor(Entity e) { return e == null ? 0f : st(e).outPitchDeg; }
    public static float getRollDegFor(Entity e)  { return e == null ? 0f : st(e).outRollDeg; }
    public static boolean isActiveFor(Entity e)  { return e != null && st(e).outActive; }

    // rétro-compat (utilise la caméra actuelle)
    public static float getPitchDeg() { var mc = MinecraftClient.getInstance(); return mc == null ? 0f : getPitchDegFor(mc.getCameraEntity()); }
    public static float getRollDeg()  { var mc = MinecraftClient.getInstance(); return mc == null ? 0f : getRollDegFor(mc.getCameraEntity()); }
    public static boolean isActive()  { var mc = MinecraftClient.getInstance(); return mc != null && isActiveFor(mc.getCameraEntity()); }

    public static void reset() {
        STATES.clear();
        pruneTicker = 0;
    }

    private CameraSway() {}
}
