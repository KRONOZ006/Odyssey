package net.kronoz.odyssey.systems.cinematics.api;

import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EasingUtil {

    private EasingUtil() {}

    public static float clamp01(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t;
    }

    public static double clamp01(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t;
    }

    public static float ease(Easing easing, float t) {
        return (float) easing.apply(clamp01(t));
    }

    public static double ease(Easing easing, double t) {
        return easing.apply(clamp01(t));
    }

    public static float shortestAngleDeltaDeg(float fromDeg, float toDeg) {
        float delta = MathHelper.wrapDegrees(toDeg - fromDeg);
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    public static float easeAngleDeg(Easing easing, float startDeg, float endDeg, float t) {
        float eased = ease(easing, t);
        float delta = shortestAngleDeltaDeg(startDeg, endDeg);
        return startDeg + delta * eased;
    }

    public static void easeYawPitch(Easing easing, float startYaw, float endYaw, float startPitch, float endPitch, float t, java.util.function.BiConsumer<Float, Float> setter) {
        float yaw = easeAngleDeg(easing, startYaw, endYaw, t);
        float pitch = easeAngleDeg(easing, startPitch, endPitch, t);
        setter.accept(yaw, pitch);
    }

    public static Quaternionf easeQuaternionSlerp(Easing easing, Quaternionf start, Quaternionf end, float t) {
        float k = ease(easing, t);
        Quaternionf out = new Quaternionf(start);
        out.slerp(end, k);
        return out;
    }

    public static Vector3f easeVec3(Easing easing, Vector3f a, Vector3f b, float t) {
        float k = ease(easing, t);
        return new Vector3f(
                MathHelper.lerp(k, a.x, b.x),
                MathHelper.lerp(k, a.y, b.y),
                MathHelper.lerp(k, a.z, b.z)
        );
    }
}
