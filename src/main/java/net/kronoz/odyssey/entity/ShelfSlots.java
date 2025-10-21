package net.kronoz.odyssey.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class ShelfSlots {
    public record SlotDef(
            String bone,
            Box pickBox,         // local-space [0..1] box for clicking
            Vec3d translate,     // local pos (0..1)
            Vec3d eulerDeg,      // rotation XYZ in degrees
            double scale         // uniform scale for item
    ) {}

    public static final List<SlotDef> LAYOUT = new ArrayList<>();

    static {
        // Example: 4 bones on a two-row shelf. Adjust to match your model.
        // pickBox is a small area in front of each bone (x,z in 0..1; y is 0..1)
        LAYOUT.add(new SlotDef(
                "bone_top_left",
                new Box(0.18, 0.20, 0.30, 0.32, 0.60, 0.44),
                new Vec3d(0.25, 0.32, 0.37),
                new Vec3d(0, 0, 0),
                0.45
        ));
        LAYOUT.add(new SlotDef(
                "bone_top_right",
                new Box(0.68, 0.20, 0.30, 0.82, 0.60, 0.44),
                new Vec3d(0.75, 0.32, 0.37),
                new Vec3d(0, 15, 0),
                0.45
        ));
        LAYOUT.add(new SlotDef(
                "bone_bottom_left",
                new Box(0.18, 0.02, 0.30, 0.32, 0.34, 0.44),
                new Vec3d(0.25, 0.14, 0.37),
                new Vec3d(0, -10, 0),
                0.55
        ));
        LAYOUT.add(new SlotDef(
                "bone_bottom_right",
                new Box(0.68, 0.02, 0.30, 0.82, 0.34, 0.44),
                new Vec3d(0.75, 0.14, 0.37),
                new Vec3d(0, 0, 0),
                0.55
        ));
    }

    public static int pickSlot(Vec3d local01) {
        for (int i = 0; i < LAYOUT.size(); i++) {
            if (contains(LAYOUT.get(i).pickBox, local01)) return i;
        }
        return -1;
    }

    private static boolean contains(Box b, Vec3d p) {
        return p.x >= b.minX && p.x <= b.maxX &&
               p.y >= b.minY && p.y <= b.maxY &&
               p.z >= b.minZ && p.z <= b.maxZ;
    }
}
