package net.kronoz.odyssey.systems.cinematics.runtime;

import net.kronoz.odyssey.systems.cinematics.api.Easing;
import net.kronoz.odyssey.systems.cinematics.track.CameraKeyframe;
import net.kronoz.odyssey.systems.cinematics.track.CameraPose;
import net.kronoz.odyssey.systems.cinematics.track.CameraTrack;
import net.minecraft.util.math.Vec3d;

public final class BootstrapScenes {
    private BootstrapScenes(){}

    public static void registerAll(){
        CameraTrack intro = new CameraTrack()
                .add(new CameraKeyframe(0.0, new CameraPose(new Vec3d(0, 2.2, -6),  0f,  10f, 0f, 70f, false,false,false), Easing.OUT_CUBIC,   true, 1.0))
                .add(new CameraKeyframe(3.0, new CameraPose(new Vec3d(0, 2.0, -2), 20f,   5f, 0f, 60f, false,false,false), Easing.IN_OUT_SINE, true, 1.0))
                .add(new CameraKeyframe(6.0, new CameraPose(new Vec3d(0.6,1.9,-0.8),80f,   2f, 0f, 50f, true,false,false),  Easing.OUT_QUAD,    true, 1.0));
        CutsceneManager.I.register(new Cutscene(
                "intro", intro, 6.0,
                true,
                false,
                false,
                Cutscene.CameraMode.ADDITIVE_FOLLOW,
                false
        ));

        CameraTrack lookdown10 = new CameraTrack()
                .add(new CameraKeyframe(
                        0.0,
                        new CameraPose(new Vec3d(0,0,0), 0f, 0f, 0f, 70f, false,false,false),
                        Easing.LINEAR,
                        false, 1.0
                ))
                .add(new CameraKeyframe(
                        2.0,
                        new CameraPose(new Vec3d(0,0,0), 0f, 60f, 0f, 70f, false,false,false),
                        Easing.IN_OUT_SINE,
                        false, 1.0
                ))
                .add(new CameraKeyframe(
                        3.0,
                        new CameraPose(new Vec3d(0,0,0), 0f, 0f, 0f, 70f, false,false,false),
                        Easing.IN_OUT_SINE,
                        false, 1.0
                ));

        CutsceneManager.I.register(new Cutscene(
                "lookdown10",
                lookdown10,
                3.0,
                true,
                true,
                true,
                Cutscene.CameraMode.ABSOLUTE,
                true
        ));
    }
}
