package net.kronoz.odyssey.systems.cinematics.api;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public final class Curves {
    private Curves(){}

    public static Vec3d lerp(Vec3d a, Vec3d b, double t){
        return new Vec3d(
                MathHelper.lerp((float)t, (float)a.x, (float)b.x),
                MathHelper.lerp((float)t, (float)a.y, (float)b.y),
                MathHelper.lerp((float)t, (float)a.z, (float)b.z));
    }

    public static float lerp(float a, float b, double t){
        return MathHelper.lerp((float)t, a, b);
    }

    public static Quaternionf slerp(Quaternionf a, Quaternionf b, double t){
        Quaternionf out = new Quaternionf(a);
        out.slerp(b, (float)t);
        return out;
    }
}
