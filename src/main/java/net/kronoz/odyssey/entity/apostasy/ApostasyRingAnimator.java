package net.kronoz.odyssey.entity.apostasy;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public final class ApostasyRingAnimator {
    private ApostasyRingAnimator() {}
    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private static final class Vec3 {
        float x,y,z;
        Vec3() {}
        Vec3(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
        static Vec3 lerp(Vec3 a, Vec3 b, float t) {
            return new Vec3(MathHelper.lerp(t,a.x,b.x), MathHelper.lerp(t,a.y,b.y), MathHelper.lerp(t,a.z,b.z));
        }
    }

    private static final class Track {
        Vec3 start = new Vec3();
        Vec3 target = new Vec3();
        Vec3 lastOut = new Vec3();
        int startTick;
        int duration;
        int holdUntil;
        int phaseOffset;
    }

    private static final class State {
        final Random rnd;
        final Track r1 = new Track();
        final Track r2 = new Track();
        final Track r3 = new Track();
        State(long seed){ this.rnd = Random.create(seed); }
    }

    // smoother than linear: cosine-based easeInOut
    private static float easeInOutSine(float t) { return 0.5f - 0.5f * (float)Math.cos(Math.PI * t); }
    // per-tick angular speed limit (radians/tick) to prevent big jumps
    private static final float MAX_RAD_STEP_R1 = (float)Math.toRadians(1.8);  // ~1.8°/tick
    private static final float MAX_RAD_STEP_R2 = (float)Math.toRadians(2.2);  // ~2.2°/tick
    private static final float MAX_RAD_STEP_R3 = (float)Math.toRadians(1.4);  // ~1.4°/tick

    // tighter angle ranges (deg)
    private static final float[] R1_RANGE = {-15f, 15f, -120f, 120f, -15f, 15f};
    private static final float[] R2_RANGE = {-25f, 25f, -140f, 140f, -25f, 25f};
    private static final float[] R3_RANGE = {-12f, 12f, -110f, 110f, -12f, 12f};

    private static float radRange(Random r, float minDeg, float maxDeg) {
        return (float)Math.toRadians(minDeg + r.nextFloat()*(maxDeg-minDeg));
    }

    private static void newSegment(State s, Track tr, int now, float[] rangeDeg, int durMin, int durMax, int holdMin, int holdMax) {
        if (tr.target == null) tr.target = new Vec3();
        tr.start = new Vec3(tr.target.x, tr.target.y, tr.target.z);
        if (Float.isNaN(tr.lastOut.x)) tr.lastOut = new Vec3(tr.start.x, tr.start.y, tr.start.z);

        tr.startTick = now + tr.phaseOffset;
        tr.duration = durMin + s.rnd.nextInt(durMax - durMin + 1);
        int hold = holdMin + s.rnd.nextInt(holdMax - holdMin + 1);
        tr.holdUntil = tr.startTick + tr.duration + hold;

        tr.target = new Vec3(
                radRange(s.rnd, rangeDeg[0], rangeDeg[1]),
                radRange(s.rnd, rangeDeg[2], rangeDeg[3]),
                radRange(s.rnd, rangeDeg[4], rangeDeg[5])
        );
    }

    private static float time01(int now, int start, int dur) {
        if (dur <= 0) return 1f;
        if (now <= start) return 0f;
        if (now >= start+dur) return 1f;
        return (now - start) / (float)dur;
    }

    private static float stepLimit(float current, float desired, float maxStep) {
        float delta = desired - current;
        if (delta >  maxStep) delta =  maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return current + delta;
    }

    public static Result sample(int id, int age) {
        State st = STATES.computeIfAbsent(id, i -> {
            State ns = new State(0x9E3779B97F4A7C15L ^ i);
            ns.r1.phaseOffset = ns.rnd.nextInt(10);
            ns.r2.phaseOffset = 18 + ns.rnd.nextInt(14);
            ns.r3.phaseOffset = 7 + ns.rnd.nextInt(18);
            ns.r1.target = new Vec3(); ns.r2.target = new Vec3(); ns.r3.target = new Vec3();
            ns.r1.lastOut = new Vec3(0,0,0); ns.r2.lastOut = new Vec3(0,0,0); ns.r3.lastOut = new Vec3(0,0,0);
            return ns;
        });

        if (age > st.r1.holdUntil) newSegment(st, st.r1, age, R1_RANGE, 48, 60, 28, 40);
        if (age > st.r2.holdUntil) newSegment(st, st.r2, age, R2_RANGE, 52, 66, 32, 44);
        if (age > st.r3.holdUntil) newSegment(st, st.r3, age, R3_RANGE, 44, 58, 26, 38);

        float t1 = easeInOutSine(time01(age, st.r1.startTick, st.r1.duration));
        float t2 = easeInOutSine(time01(age, st.r2.startTick, st.r2.duration));
        float t3 = easeInOutSine(time01(age, st.r3.startTick, st.r3.duration));

        Vec3 d1 = Vec3.lerp(st.r1.start, st.r1.target, t1);
        Vec3 d2 = Vec3.lerp(st.r2.start, st.r2.target, t2);
        Vec3 d3 = Vec3.lerp(st.r3.start, st.r3.target, t3);

        st.r1.lastOut.x = stepLimit(st.r1.lastOut.x, d1.x, MAX_RAD_STEP_R1);
        st.r1.lastOut.y = stepLimit(st.r1.lastOut.y, d1.y, MAX_RAD_STEP_R1);
        st.r1.lastOut.z = stepLimit(st.r1.lastOut.z, d1.z, MAX_RAD_STEP_R1);

        st.r2.lastOut.x = stepLimit(st.r2.lastOut.x, d2.x, MAX_RAD_STEP_R2);
        st.r2.lastOut.y = stepLimit(st.r2.lastOut.y, d2.y, MAX_RAD_STEP_R2);
        st.r2.lastOut.z = stepLimit(st.r2.lastOut.z, d2.z, MAX_RAD_STEP_R2);

        st.r3.lastOut.x = stepLimit(st.r3.lastOut.x, d3.x, MAX_RAD_STEP_R3);
        st.r3.lastOut.y = stepLimit(st.r3.lastOut.y, d3.y, MAX_RAD_STEP_R3);
        st.r3.lastOut.z = stepLimit(st.r3.lastOut.z, d3.z, MAX_RAD_STEP_R3);

        return new Result(st.r1.lastOut, st.r2.lastOut, st.r3.lastOut);
    }

    public static final class Result {
        public final float r1x,r1y,r1z, r2x,r2y,r2z, r3x,r3y,r3z;
        Result(Vec3 a, Vec3 b, Vec3 c) {
            r1x=a.x; r1y=a.y; r1z=a.z;
            r2x=b.x; r2y=b.y; r2z=b.z;
            r3x=c.x; r3y=c.y; r3z=c.z;
        }
    }
}
