package net.kronoz.odyssey.systems.cinematics.api;

import net.kronoz.odyssey.systems.cinematics.api.Easing;
import net.kronoz.odyssey.systems.cinematics.api.EasingUtil;

public final class RotationTweener {
    private final float startYaw;
    private final float startPitch;
    private final float endYaw;
    private final float endPitch;
    private final int durationTicks;
    private final Easing easing;
    private int age;

    public RotationTweener(float startYaw, float startPitch, float endYaw, float endPitch, int durationTicks, Easing easing) {
        this.startYaw = startYaw;
        this.startPitch = startPitch;
        this.endYaw = endYaw;
        this.endPitch = endPitch;
        this.durationTicks = Math.max(1, durationTicks);
        this.easing = easing;
        this.age = 0;
    }

    public boolean isDone() {
        return age >= durationTicks;
    }

    public void tick(java.util.function.BiConsumer<Float, Float> yawPitchSetter) {
        float t = Math.min(1f, age / (float) durationTicks);
        float easedYaw = EasingUtil.easeAngleDeg(easing, startYaw, endYaw, t);
        float easedPitch = EasingUtil.easeAngleDeg(easing, startPitch, endPitch, t);
        yawPitchSetter.accept(easedYaw, easedPitch);
        age++;
    }
}

// Example usage:
// what ya wanna do for basic easings
//RotationTweener tweener = new RotationTweener(
//        entity.getYaw(), entity.getPitch(),  // start yaw/pitch
//        targetYaw, targetPitch,              // target yaw/pitch
//        40,                                  // duration in ticks (2 seconds at 20 TPS)
//        Easing.IN_OUT_SINE                   // easing type
//);
//
// in a tick handler or goal tick
//if (!tweener.isDone()) {
//        tweener.tick((yaw, pitch) -> {
//        entity.setYaw(yaw);
//        entity.setPitch(pitch);
//    });
//            }

//gecko
//float t = Math.min(1f, (agePartial + tickDelta) / durationTicks);
//float easedYaw = EasingUtil.easeAngleDeg(Easing.IN_OUT_QUART, startYaw, endYaw, t);
//
// Apply the eased rotation to your bone (GeckoLib uses radians)
//bone.setRotY((float) Math.toRadians(easedYaw));
