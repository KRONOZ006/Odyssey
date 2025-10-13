package net.kronoz.odyssey.systems.physics.lightning;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BoltBuilder {

    public static Vec3d[] buildMainPathLocalFractal(double totalLen, int depth, double roughness, Random rng) {
        Vec3d top = new Vec3d(0, totalLen * 0.85, 0);
        Vec3d bot = new Vec3d(0, 0, 0);

        List<Vec3d> pts = new ArrayList<>();
        pts.add(top);
        pts.add(bot);

        for (int i = 0; i < depth; i++) {
            List<Vec3d> next = new ArrayList<>(pts.size() * 2);
            double amp = roughness * totalLen * Math.pow(0.5, i + 1);

            for (int s = 0; s < pts.size() - 1; s++) {
                Vec3d a = pts.get(s);
                Vec3d b = pts.get(s + 1);
                Vec3d mid = a.add(b).multiply(0.5);

                double jx = (rng.nextDouble() - 0.5) * amp;
                double jz = (rng.nextDouble() - 0.5) * amp;
                double jy = -(rng.nextDouble()) * (amp * 0.2);
                mid = mid.add(jx, jy, jz);

                next.add(a);
                next.add(mid);
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
        }

        pts = smoothOnce(pts);

        return pts.toArray(Vec3d[]::new);
    }

    private static List<Vec3d> smoothOnce(List<Vec3d> p) {
        if (p.size() <= 2) return p;
        ArrayList<Vec3d> out = new ArrayList<>(p.size());
        out.add(p.get(0));
        for (int i = 1; i < p.size() - 1; i++) {
            Vec3d a = p.get(i - 1);
            Vec3d b = p.get(i);
            Vec3d c = p.get(i + 1);
            Vec3d s = a.multiply(0.25).add(b.multiply(0.5)).add(c.multiply(0.25));
            out.add(s);
        }
        out.add(p.get(p.size() - 1));
        return out;
    }

    public record Branch(Vec3d[] points) {}

    public static Branch[] buildBranchesLocal(Vec3d[] spine, int count, Random rng) {
        if (spine.length < 4) return new Branch[0];
        Branch[] out = new Branch[count];
        for (int i = 0; i < count; i++) {
            int anchor = 1 + rng.nextInt(spine.length - 2);
            Vec3d start = spine[anchor];

            double len = 10.5 + rng.nextDouble() * 3.0;
            double dx = (rng.nextDouble() - 0.5) * 1.2;
            double dz = (rng.nextDouble() - 0.5) * 1.2;
            double dy = -0.2 - rng.nextDouble() * 0.8;

            Vec3d end = start.add(dx * len, dy * len, dz * len);

            int depth = 1 + rng.nextInt(10);
            List<Vec3d> pts = new ArrayList<>();
            pts.add(start);
            pts.add(end);
            for (int d = 0; d < depth; d++) {
                ArrayList<Vec3d> next = new ArrayList<>(pts.size() * 2);
                double amp = (len * 0.4) * Math.pow(0.5, d + 1);
                for (int s = 0; s < pts.size() - 1; s++) {
                    Vec3d a = pts.get(s);
                    Vec3d b = pts.get(s + 1);
                    Vec3d m = a.add(b).multiply(0.5)
                            .add((rng.nextDouble() - 0.5) * amp,
                                    (rng.nextDouble() - 0.5) * amp * 0.5,
                                    (rng.nextDouble() - 0.5) * amp);
                    next.add(a);
                    next.add(m);
                }
                next.add(pts.get(pts.size() - 1));
                pts = next;
            }
            out[i] = new Branch(pts.toArray(Vec3d[]::new));
        }
        return out;
    }
}
