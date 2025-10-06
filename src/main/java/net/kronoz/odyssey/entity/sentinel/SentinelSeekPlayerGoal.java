package net.kronoz.odyssey.entity.sentinel;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.EnumSet;

public class SentinelSeekPlayerGoal extends Goal {
    private final SentinelEntity mob;
    private PlayerEntity target;
    private int roamCooldown;
    private double hoverPhase;
    private float orbitAngleDeg;

    private static final double FOLLOW_RADIUS = 7.0;
    private static final double FOLLOW_BAND   = 1.5;
    private static final double APPROACH      = 1.0;
    private static final double ORBIT         = 0.85;
    private static final double ROAM          = 0.7;

    public SentinelSeekPlayerGoal(SentinelEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() { return true; }

    @Override
    public void tick() {
        hoverPhase += 0.05;
        orbitAngleDeg = (orbitAngleDeg + 2.0f) % 360f;
        if (roamCooldown > 0) roamCooldown--;

        if (target == null || !target.isAlive() || target.isSpectator() || target.isCreative()) {
            target = mob.getWorld().getClosestPlayer(mob, 32.0);
        }

        double hover = Math.sin(hoverPhase) * 0.5;

        if (target != null) {
            boolean visible = mob.canSeePlayerInCone(target);
            mob.setSpotted(visible);

            Vec3d me = mob.getPos();
            Vec3d tp = target.getEyePos();
            double d = me.distanceTo(tp);

            if (visible) {
                if (d > FOLLOW_RADIUS + FOLLOW_BAND) {
                    mob.steerTowards(tp.add(0, hover, 0), APPROACH);
                } else if (d < FOLLOW_RADIUS - FOLLOW_BAND) {
                    Vec3d away = me.subtract(tp).normalize();
                    Vec3d pos  = tp.add(away.multiply(FOLLOW_RADIUS)).add(0, hover, 0);
                    mob.steerTowards(pos, APPROACH);
                } else {
                    double ang = Math.toRadians(orbitAngleDeg);
                    Vec3d ring = new Vec3d(Math.cos(ang), 0, Math.sin(ang)).multiply(FOLLOW_RADIUS);
                    Vec3d pos  = tp.add(ring).add(0, hover, 0);
                    mob.steerTowards(pos, ORBIT);
                }
            } else {
                if (roamCooldown == 0) {
                    Vec3d guess = tp.add(randHoriz(mob.getRandom(), 6), hover, randHoriz(mob.getRandom(), 6));
                    mob.steerTowards(guess, ROAM);
                    roamCooldown = 20;
                }
            }
        } else {
            if (roamCooldown == 0) {
                Vec3d roam = randomAirPos(mob, 8, 4).add(0, hover, 0);
                mob.steerTowards(roam, ROAM);
                roamCooldown = 20;
            }
        }
    }

    private static int between(Random r, int min, int max) {
        if (max <= min) return min;
        return min + r.nextInt((max - min) + 1);
    }

    private static Vec3d randomAirPos(SentinelEntity e, int horiz, int vert) {
        Random r = e.getRandom();
        BlockPos bp = e.getBlockPos().add(between(r, -horiz, horiz), between(r, -vert, vert), between(r, -horiz, horiz));
        return Vec3d.ofCenter(bp);
    }

    private static double randHoriz(Random r, int range) {
        return MathHelper.nextDouble(r, -range, range);
    }
}
