package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class WireAnchor {
    public final BlockPos pos;
    public final Direction face;
    public final Vec3d offsetWorld;

    public WireAnchor(BlockPos pos, Direction face, Vec3d offsetWorld){
        this.pos = pos; this.face = face; this.offsetWorld = offsetWorld;
    }

    public static WireAnchor of(BlockPos pos, Direction face){
        return new WireAnchor(pos, face, Vec3d.ZERO);
    }

    public NbtCompound toNbt(){
        NbtCompound n = new NbtCompound();
        n.putInt("x", pos.getX());
        n.putInt("y", pos.getY());
        n.putInt("z", pos.getZ());
        n.putInt("face", face.getId());
        n.putDouble("ox", offsetWorld.x);
        n.putDouble("oy", offsetWorld.y);
        n.putDouble("oz", offsetWorld.z);
        return n;
    }

    public static WireAnchor fromNbt(NbtCompound n){
        BlockPos p = new BlockPos(n.getInt("x"), n.getInt("y"), n.getInt("z"));
        Direction f = Direction.byId(n.getInt("face"));
        Vec3d off = new Vec3d(n.getDouble("ox"), n.getDouble("oy"), n.getDouble("oz"));
        return new WireAnchor(p, f, off);
    }
}
