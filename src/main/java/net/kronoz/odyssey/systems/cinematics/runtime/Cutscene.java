package net.kronoz.odyssey.systems.cinematics.runtime;


import net.kronoz.odyssey.systems.cinematics.track.CameraTrack;


public record Cutscene(
        String id,
        CameraTrack camera,
        double durationSec,
        boolean restoreOnEnd,
        boolean lockHud,
        boolean lockInput,
        CameraMode mode,
        boolean anchorToStart
) {
    public enum CameraMode { ADDITIVE_FOLLOW, ABSOLUTE }
}