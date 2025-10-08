package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class WireToolMath {
    private WireToolMath(){}

    public static Vec3d anchorCenter(WireAnchor a){
        return anchorCenter(a.pos, a.face).add(a.offsetWorld);
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
}