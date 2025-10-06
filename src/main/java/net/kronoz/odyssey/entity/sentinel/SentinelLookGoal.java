package net.kronoz.odyssey.entity.sentinel;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public class SentinelLookGoal extends Goal {
    private final SentinelEntity mob;
    private static final float HEAD_SMOOTH = 0.18f;
    private static final float EYE_SMOOTH = 0.2f;
    private static final float PITCH_DEADZONE = (float)Math.toRadians(1.0);

    private float scanEyeYaw = 0f;
    private float scanEyePitch = 0f;
    private int scanTicksLeft = 0;

    public SentinelLookGoal(SentinelEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override public boolean canStart() { return true; }

    @Override
    public void tick() {
        PlayerEntity p = mob.getTrackedPlayer();
        boolean visible = p != null && mob.canSeePlayerInCone(p);
        mob.setSpotted(visible);

        if (visible && p != null) {
            Vec3d from = mob.getEyePos();
            Vec3d to = p.getEyePos().add(0, 0.2, 0);
            Vec3d d = to.subtract(from);
            double dx = d.x, dy = d.y, dz = d.z;
            double flat = Math.sqrt(dx*dx + dz*dz);

            float targetHeadPitch = (float)Math.atan2(dy, flat);
            targetHeadPitch = MathHelper.clamp(targetHeadPitch, SentinelEntity.HEAD_PITCH_MIN, SentinelEntity.HEAD_PITCH_MAX);
            float curHead = mob.getHeadPitch();
            float diff = targetHeadPitch - curHead;
            if (Math.abs(diff) > PITCH_DEADZONE) mob.setHeadPitch(curHead + diff * HEAD_SMOOTH);

            float bodyYawDeg = mob.getYaw();
            float targetYawDeg = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90f);
            float localYawDeg = MathHelper.wrapDegrees(targetYawDeg - bodyYawDeg);
            float eyeYaw = (float)Math.toRadians(localYawDeg);
            eyeYaw = MathHelper.clamp(eyeYaw, SentinelEntity.EYE_YAW_MIN, SentinelEntity.EYE_YAW_MAX);

            float eyePitch = (float)Math.atan2(dy, flat);
            eyePitch = MathHelper.clamp(eyePitch, SentinelEntity.EYE_PITCH_MIN, SentinelEntity.EYE_PITCH_MAX);

            float curY = mob.getEyeYaw();
            float curP = mob.getEyePitch();
            mob.setEye(curY + (eyeYaw - curY) * EYE_SMOOTH, curP + (eyePitch - curP) * EYE_SMOOTH);

            scanTicksLeft = 0;
        } else {
            if (scanTicksLeft <= 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                float yawRange   = (SentinelEntity.EYE_YAW_MAX - SentinelEntity.EYE_YAW_MIN) * 0.6f;
                float pitchRange = (SentinelEntity.EYE_PITCH_MAX - SentinelEntity.EYE_PITCH_MIN) * 0.6f;
                float yawCenter   = (SentinelEntity.EYE_YAW_MAX + SentinelEntity.EYE_YAW_MIN) * 0.5f;
                float pitchCenter = (SentinelEntity.EYE_PITCH_MAX + SentinelEntity.EYE_PITCH_MIN) * 0.5f;
                scanEyeYaw   = yawCenter   + (float)((r.nextDouble() - 0.5) * yawRange);
                scanEyePitch = pitchCenter + (float)((r.nextDouble() - 0.5) * pitchRange);
                scanTicksLeft = r.nextInt(40, 71);
            } else scanTicksLeft--;

            float hp = mob.getHeadPitch();
            mob.setHeadPitch(hp + (0f - hp) * 0.12f);

            float curY = mob.getEyeYaw();
            float curP = mob.getEyePitch();
            mob.setEye(curY + (scanEyeYaw - curY) * 0.12f, curP + (scanEyePitch - curP) * 0.12f);
        }
    }
}
