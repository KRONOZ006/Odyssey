// net/kronoz/odyssey/item/client/renderer/JetpackBoneCache.java
package net.kronoz.odyssey.item.client.renderer;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JetpackBoneCache {
    private JetpackBoneCache() {}

    public static final class Pose {
        public Vec3d pos;
        public Quaternionf rot;
        public Vector3f right, up, fwd;
        public long lastSeenNanos;
    }

    private static final Map<String, Pose> POSES = new ConcurrentHashMap<>();

    public static void set(String name, Vec3d p, Quaternionf q, Vector3f right, Vector3f up, Vector3f fwd) {
        Pose pose = new Pose();
        pose.pos = p; pose.rot = q; pose.right = right; pose.up = up; pose.fwd = fwd;
        pose.lastSeenNanos = System.nanoTime();
        POSES.put(name, pose);
    }

    public static Pose get(String name) { return POSES.get(name); }
    public static Map<String, Pose> all() { return POSES; }
}
