package net.kronoz.odyssey.entity.sentinel;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class EyeTrackGoal extends Goal {
    private final SentinelEntity mob;

    public EyeTrackGoal(SentinelEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() { return true; }

    @Override
    public void tick() {
        PlayerEntity p = mob.getWorld().getClosestPlayer(mob, 32.0);
        if (p == null) {
            mob.setDesiredHead(0f, 0f);
            mob.setDesiredEye(0f, 0f);
            return;
        }

        Vec3d from = mob.getEyePos();
        Vec3d to   = p.getEyePos();
        Vec3d d    = to.subtract(from);
        double dx = d.x, dy = d.y, dz = d.z;

        float targetYawDeg   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float currentYawDeg  = mob.getYaw();
        float yawDeltaDeg    = MathHelper.wrapDegrees(targetYawDeg - currentYawDeg);
        float headYawLocal   = (float)Math.toRadians(yawDeltaDeg);
        float headPitchLocal = (float)(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

        mob.setDesiredHead(headYawLocal, headPitchLocal);
        mob.setDesiredEye(0f, 0f);

        float absYaw = Math.abs(yawDeltaDeg);
        float lerp = absYaw > 45f ? 0.28f : 0.14f;
        mob.faceYawDeg(targetYawDeg, lerp);
    }
}
