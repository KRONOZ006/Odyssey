package net.kronoz.odyssey.systems.cinematics.track;


import net.kronoz.odyssey.systems.cinematics.api.Easing;


public record CameraKeyframe(
        double timeSec,
        CameraPose pose,
        Easing easing,
        boolean relativeToPlayer,
        double relFactor
) {}
