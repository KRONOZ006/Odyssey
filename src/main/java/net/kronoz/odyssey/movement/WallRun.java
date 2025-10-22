// src/main/java/net/kronoz/odyssey/movement/WallRun.java
package net.kronoz.odyssey.movement;

import net.kronoz.odyssey.init.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class WallRun {
    private static final int HOLD_TICKS = 24;
    private static final int DECAY_TICKS = 32;
    private static final double GRAVITY_HOLD = 0.22;
    private static final double GRAVITY_DECAY_MIN = 0.55;
    private static final double GRAVITY_DECAY_MAX = 1.15;
    private static final double STICK = 0.085;
    private static final double ALONG_GAIN = 0.055;
    private static final double START_MIN_HSPEED = 0.02;
    private static final double PROBE_DIST = 0.6;
    private static final double PROBE_STEP_Y = 0.9;

    public static boolean canStart(PlayerEntity p) {
        if (p.isSpectator() || p.isSwimming() || p.isFallFlying() || p.isInLava()) return false;
        if (p.isOnGround()) return false;
        Vec3d vh = p.getVelocity().multiply(1, 0, 1);
        if (vh.lengthSquared() < START_MIN_HSPEED * START_MIN_HSPEED) return false;
        return findWallNormal(p) != null;
    }

    private static boolean isWallRunnable(Block b) {
        return b == ModBlocks.WALLRUN;

    }

    private static Vec3d findWallNormal(PlayerEntity p) {
        Vec3d pos = p.getPos();
        Direction[] dirs = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        for (int y = 0; y < 2; y++) {
            double yOff = y * PROBE_STEP_Y;
            for (Direction d : dirs) {
                Vec3d off = new Vec3d(d.getOffsetX(), 0, d.getOffsetZ()).normalize().multiply(PROBE_DIST);
                BlockPos bp = BlockPos.ofFloored(pos.add(off).add(0, yOff, 0));
                BlockState st = p.getWorld().getBlockState(bp);
                if (!st.isAir() && isWallRunnable(st.getBlock())) {
                    return new Vec3d(-d.getOffsetX(), 0, -d.getOffsetZ()).normalize();
                }
            }
        }
        return null;
    }

    private static Vec3d wallTangent(Vec3d normal, Vec3d prefer) {
        Vec3d t1 = new Vec3d(-normal.z, 0, normal.x).normalize();
        Vec3d t2 = t1.multiply(-1);
        return prefer.dotProduct(t1) >= prefer.dotProduct(t2) ? t1 : t2;
    }

    public static void tick(PlayerEntity p, WallState s) {
        if (s.remaining <= 0) {
            Vec3d n = findWallNormal(p);
            if (!canStart(p) || n == null) return;
            s.remaining = HOLD_TICKS + DECAY_TICKS;
            s.normal = n;
        } else {
            Vec3d n = findWallNormal(p);
            if (n == null || p.isOnGround()) { s.reset(); return; }
            s.normal = n;
        }

        Vec3d v = p.getVelocity();
        Vec3d look = p.getRotationVec(1).multiply(1, 0, 1);
        Vec3d prefer = v.multiply(1, 0, 1).lengthSquared() > 1e-6 ? v : look;
        Vec3d tangent = wallTangent(s.normal, prefer).normalize();

        int decayed = Math.max(0, (HOLD_TICKS + DECAY_TICKS) - s.remaining - HOLD_TICKS);
        double g;
        if (s.remaining > DECAY_TICKS) g = GRAVITY_HOLD;
        else {
            double t = 1.0 - (s.remaining / (double) DECAY_TICKS);
            g = GRAVITY_DECAY_MIN + (GRAVITY_DECAY_MAX - GRAVITY_DECAY_MIN) * t;
        }

        double vy = v.y > -0.02 ? v.y : v.y * g;
        double fx = v.x + tangent.x * ALONG_GAIN + s.normal.x * -STICK;
        double fz = v.z + tangent.z * ALONG_GAIN + s.normal.z * -STICK;

        p.setVelocity(fx, vy, fz);
        p.fallDistance = 0;

        if (decayed > DECAY_TICKS / 2) p.setVelocity(p.getVelocity().multiply(0.995, 1, 0.995));

        s.remaining--;
        if (s.remaining <= 0) s.reset();
    }

    public static void onJump(PlayerEntity p, WallState s) {
        if (!s.active()) return;
        Vec3d look = p.getRotationVec(1).multiply(1,0,1);
        if (look.lengthSquared() < 1e-6) look = new Vec3d(1,0,0);
        Vec3d right = new Vec3d(look.z, 0, -look.x).normalize();
        double side = right.dotProduct(s.normal);
        Vec3d kickSide = side >= 0 ? right.multiply(-1) : right;
        Vec3d tangent = wallTangent(s.normal, look).normalize();

        double upBoost = 0.9;
        double sidePush = 0.55;
        double along = 0.18;

        p.addVelocity(
                kickSide.x * sidePush + tangent.x * along,
                upBoost,
                kickSide.z * sidePush + tangent.z * along
        );
        p.velocityModified = true;
        p.fallDistance = 0;
        s.reset();
    }

    public static final class WallState {
        public int remaining = 0;
        public Vec3d normal = null;
        public boolean active() { return remaining > 0 && normal != null; }
        public void reset() { remaining = 0; normal = null; }
    }
}
