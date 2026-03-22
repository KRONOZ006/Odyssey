package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class WireToolMath {
    public static final String PENDING_ANCHOR_NBT = "odyssey_wire_pending_anchor";
    private static final double FACE_CLAMP = 0.495;
    private static final double FACE_STEP = 1.0 / 32.0;

    private WireToolMath(){}

    public static Vec3d anchorCenter(WireAnchor a){
        return anchorCenter(a.pos, a.face).add(a.offsetWorld);
    }

    public static WireAnchor anchorFromHit(BlockPos pos, Direction face, Vec3d hitPos) {
        Vec3d center = anchorCenter(pos, face);
        Vec3d offset = hitPos.subtract(center);
        offset = projectOntoFace(offset, face);
        offset = clampAndQuantize(offset, face);
        return new WireAnchor(pos.toImmutable(), face, offset);
    }

    public static WireAnchor ghostAt(Vec3d worldPos){
        var base = net.minecraft.util.math.BlockPos.ofFloored(worldPos);
        var face = net.minecraft.util.math.Direction.UP;
        Vec3d center = anchorCenter(base, face);
        Vec3d off = worldPos.subtract(center);
        return new WireAnchor(base, face, off);
    }

    public static Vec3d anchorCenter(BlockPos pos, Direction face){
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        switch (face){
            case UP -> cy += 0.5;
            case DOWN -> cy -= 0.5;
            case NORTH -> cz -= 0.5;
            case SOUTH -> cz += 0.5;
            case WEST -> cx -= 0.5;
            case EAST -> cx += 0.5;
        }
        return new Vec3d(cx, cy, cz);
    }

    public static void writePendingAnchor(ItemStack stack, @Nullable WireAnchor anchor) {
        if (stack == null) {
            return;
        }
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (anchor == null) {
            nbt.remove(PENDING_ANCHOR_NBT);
        } else {
            nbt.put(PENDING_ANCHOR_NBT, anchor.toNbt());
        }
        if (nbt.isEmpty()) {
            stack.remove(DataComponentTypes.CUSTOM_DATA);
        } else {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    public static @Nullable WireAnchor readPendingAnchor(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (nbt == null || !nbt.contains(PENDING_ANCHOR_NBT, NbtCompound.COMPOUND_TYPE)) {
            return null;
        }
        return WireAnchor.fromNbt(nbt.getCompound(PENDING_ANCHOR_NBT));
    }

    private static Vec3d projectOntoFace(Vec3d offset, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3d(0.0, offset.y, offset.z);
            case Y -> new Vec3d(offset.x, 0.0, offset.z);
            case Z -> new Vec3d(offset.x, offset.y, 0.0);
        };
    }

    private static Vec3d clampAndQuantize(Vec3d offset, Direction face) {
        double x = offset.x;
        double y = offset.y;
        double z = offset.z;
        switch (face.getAxis()) {
            case X -> {
                y = quantizedClamp(y);
                z = quantizedClamp(z);
                x = 0.0;
            }
            case Y -> {
                x = quantizedClamp(x);
                z = quantizedClamp(z);
                y = 0.0;
            }
            case Z -> {
                x = quantizedClamp(x);
                y = quantizedClamp(y);
                z = 0.0;
            }
        }
        return new Vec3d(x, y, z);
    }

    private static double quantizedClamp(double value) {
        double clamped = Math.max(-FACE_CLAMP, Math.min(FACE_CLAMP, value));
        return Math.rint(clamped / FACE_STEP) * FACE_STEP;
    }
}
