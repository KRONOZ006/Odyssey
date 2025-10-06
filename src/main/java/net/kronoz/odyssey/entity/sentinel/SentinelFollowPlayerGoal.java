package net.kronoz.odyssey.entity.sentinel;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.EnumSet;

public class SentinelFollowPlayerGoal extends Goal {
    private final SentinelEntity mob;
    private PlayerEntity target;
    private int roamCooldown;
    private double hoverPhase;
    private int memoryTicks;
    private Vec3d lastSeenPos;

    private static final double HOVER_UP = 2.25;
    private static final double HOVER_RADIUS = 1.75;
    private static final double SPEED_APPROACH = 1.0;
    private static final double SPEED_HOVER = 0.9;
    private static final double SPEED_ROAM = 0.7;
    private static final int MEMORY_MAX_TICKS = 60;

    public SentinelFollowPlayerGoal(SentinelEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override public boolean canStart() { return true; }

    @Override
    public void tick() {
        hoverPhase += 0.05;
        if (roamCooldown > 0) roamCooldown--;

        if (target == null || !target.isAlive() || target.isSpectator() || target.isCreative())
            target = mob.getTrackedPlayer();

        double hover = Math.sin(hoverPhase) * 0.45;

        if (target != null) {
            boolean visible = mob.canSeePlayerInCone(target);
            mob.setSpotted(visible);

            Vec3d tp = target.getEyePos().add(0, HOVER_UP, 0);
            if (visible) {
                memoryTicks = MEMORY_MAX_TICKS;
                lastSeenPos = tp;
                Vec3d me = mob.getPos();
                Vec3d dir = tp.subtract(me);
                double d = dir.length();
                if (d > HOVER_RADIUS + 0.5) {
                    mob.steerTowards(tp.add(0, hover, 0), SPEED_APPROACH);
                } else if (d < HOVER_RADIUS - 0.5) {
                    Vec3d away = me.subtract(tp).normalize();
                    Vec3d pos = tp.add(away.multiply(HOVER_RADIUS)).add(0, hover, 0);
                    mob.steerTowards(pos, SPEED_HOVER);
                } else {
                    mob.steerTowards(tp.add(0, hover * 0.5, 0), SPEED_HOVER);
                }
            } else if (memoryTicks > 0 && lastSeenPos != null) {
                memoryTicks--;
                mob.steerTowards(lastSeenPos.add(0, hover, 0), SPEED_HOVER);
            } else {
                if (roamCooldown == 0) {
                    Vec3d me = mob.getPos();
                    Vec3d roam = me.add(rand(mob.getRandom(), 8), rand(mob.getRandom(), 4), rand(mob.getRandom(), 8));
                    mob.steerTowards(roam, SPEED_ROAM);
                    roamCooldown = 25;
                }
            }
        } else {
            mob.setSpotted(false);
            if (roamCooldown == 0) {
                Vec3d me = mob.getPos();
                Vec3d roam = me.add(rand(mob.getRandom(), 8), rand(mob.getRandom(), 4), rand(mob.getRandom(), 8));
                mob.steerTowards(roam, SPEED_ROAM);
                roamCooldown = 25;
            }
        }
    }

    private static double rand(Random r, int range) {
        return MathHelper.nextDouble(r, -range, range);
    }
}
