package net.kronoz.odyssey.entity.apostasy;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class ApostasyHeadLookGoal extends Goal {
    private final ApostasyEntity mob;
    private static final double RANGE = 48.0;
    private static final float SMOOTH = 0.15f;

    public ApostasyHeadLookGoal(ApostasyEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override public boolean canStart() { return true; }

    @Override
    public void tick() {
        PlayerEntity p = mob.getWorld().getClosestPlayer(mob, RANGE);
        if (p == null || p.isSpectator() || p.isCreative()) {
            float hp = mob.getHeadPitch();
            mob.setHeadPitch(hp + (0f - hp) * 0.05f);
            return;
        }
        Vec3d from = mob.getEyePos();
        Vec3d to = p.getEyePos();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx*dx + dz*dz);
        float target = (float)Math.atan2(dy, flat);
        float cur = mob.getHeadPitch();
        mob.setHeadPitch(cur + (target - cur) * SMOOTH);
    }
}
