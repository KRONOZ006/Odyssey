package net.kronoz.odyssey.entity.apostasy;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public final class ApostasyRingAnimator {
    private ApostasyRingAnimator() {}
    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private static final class Vec3 { float x,y,z; Vec3(){} Vec3(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
        static Vec3 lerp(Vec3 a, Vec3 b, float t){ return new Vec3(MathHelper.lerp(t,a.x,b.x), MathHelper.lerp(t,a.y,b.y), MathHelper.lerp(t,a.z,b.z)); } }

    private static final class Track { Vec3 start=new Vec3(); Vec3 target=new Vec3(); Vec3 current=new Vec3(); int startTick; int duration; int holdUntil; int phaseOffset; }
    private static final class State { final Random rnd; final Track r1=new Track(); final Track r2=new Track(); final Track r3=new Track(); State(long seed){ this.rnd=Random.create(seed);} }

    private static float easeInOutSine(float t){ return 0.5f - 0.5f*(float)Math.cos(Math.PI*t); }

    private static final float[] R1_RANGE={-10f,10f,-90f,90f,-10f,10f};
    private static final float[] R2_RANGE={-15f,15f,-100f,100f,-15f,15f};
    private static final float[] R3_RANGE={-8f,8f,-80f,80f,-8f,8f};

    private static float radRange(Random r, float d0, float d1){ return (float)Math.toRadians(d0 + r.nextFloat()*(d1-d0)); }

    private static void newSegment(State s, Track tr, int now, float[] rangeDeg, int dmin, int dmax, int hmin, int hmax){
        tr.start = new Vec3(tr.target.x,tr.target.y,tr.target.z);
        tr.startTick = now + tr.phaseOffset;
        tr.duration = dmin + s.rnd.nextInt(dmax - dmin + 1);
        int hold = hmin + s.rnd.nextInt(hmax - hmin + 1);
        tr.holdUntil = tr.startTick + tr.duration + hold;
        tr.target = new Vec3(radRange(s.rnd,rangeDeg[0],rangeDeg[1]), radRange(s.rnd,rangeDeg[2],rangeDeg[3]), radRange(s.rnd,rangeDeg[4],rangeDeg[5]));
    }

    private static float time01(int now, int start, int dur){
        if (dur<=0) return 1f;
        if (now<=start) return 0f;
        if (now>=start+dur) return 1f;
        return (now-start)/(float)dur;
    }

    public static Result sample(int id, int age){
        State st = STATES.computeIfAbsent(id, i->{
            State ns=new State(0x9E3779B97F4A7C15L ^ i);
            ns.r1.phaseOffset = ns.rnd.nextInt(15);
            ns.r2.phaseOffset = 20 + ns.rnd.nextInt(15);
            ns.r3.phaseOffset = 10 + ns.rnd.nextInt(15);
            ns.r1.target=new Vec3(); ns.r2.target=new Vec3(); ns.r3.target=new Vec3();
            ns.r1.current=new Vec3(0,0,0); ns.r2.current=new Vec3(0,0,0); ns.r3.current=new Vec3(0,0,0);
            return ns;
        });

        if (age>st.r1.holdUntil) newSegment(st, st.r1, age, R1_RANGE, 120, 180, 60, 90);
        if (age>st.r2.holdUntil) newSegment(st, st.r2, age, R2_RANGE, 130, 190, 65, 95);
        if (age>st.r3.holdUntil) newSegment(st, st.r3, age, R3_RANGE, 110, 170, 55, 85);

        float t1 = easeInOutSine(time01(age, st.r1.startTick, st.r1.duration));
        float t2 = easeInOutSine(time01(age, st.r2.startTick, st.r2.duration));
        float t3 = easeInOutSine(time01(age, st.r3.startTick, st.r3.duration));

        Vec3 d1 = Vec3.lerp(st.r1.start, st.r1.target, t1);
        Vec3 d2 = Vec3.lerp(st.r2.start, st.r2.target, t2);
        Vec3 d3 = Vec3.lerp(st.r3.start, st.r3.target, t3);

        float smooth = 0.05f;
        st.r1.current.x += (d1.x - st.r1.current.x)*smooth;
        st.r1.current.y += (d1.y - st.r1.current.y)*smooth;
        st.r1.current.z += (d1.z - st.r1.current.z)*smooth;

        st.r2.current.x += (d2.x - st.r2.current.x)*smooth;
        st.r2.current.y += (d2.y - st.r2.current.y)*smooth;
        st.r2.current.z += (d2.z - st.r2.current.z)*smooth;

        st.r3.current.x += (d3.x - st.r3.current.x)*smooth;
        st.r3.current.y += (d3.y - st.r3.current.y)*smooth;
        st.r3.current.z += (d3.z - st.r3.current.z)*smooth;

        return new Result(st.r1.current, st.r2.current, st.r3.current);
    }

    public static final class Result {
        public final float r1x,r1y,r1z, r2x,r2y,r2z, r3x,r3y,r3z;
        Result(Vec3 a, Vec3 b, Vec3 c){ r1x=a.x; r1y=a.y; r1z=a.z; r2x=b.x; r2y=b.y; r2z=b.z; r3x=c.x; r3y=c.y; r3z=c.z; }
    }
}
