package net.kronoz.odyssey.systems.cinematics.runtime;

import net.kronoz.odyssey.systems.cinematics.track.CameraPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;


public final class CutscenePlayer {
    private final Cutscene cutscene;
    private double t;
    private boolean playing;

    private double speed = 1.0;

    private boolean anchorCaptured = false;
    private Vec3d anchorPos = Vec3d.ZERO;
    private float anchorYaw = 0f, anchorPitch = 0f;

    public CutscenePlayer(Cutscene cutscene){
        this.cutscene = cutscene;
    }

    public void play(){
        t = 0.0;
        playing = true;
        anchorCaptured = false;
    }

    public void stop(){ playing = false; }

    public void seek(double timeSec){ t = Math.max(0.0, timeSec); }

    public void setSpeed(double speed){ this.speed = Math.max(0.001, speed); }

    public double getSpeed(){ return speed; }

    private void ensureAnchor(){
        if(anchorCaptured) return;
        if(cutscene.mode() != Cutscene.CameraMode.ABSOLUTE || !cutscene.anchorToStart()) return;

        var mc = MinecraftClient.getInstance();
        if(mc == null) return;
        Camera cam = mc.gameRenderer.getCamera();
        anchorPos = cam.getPos();
        anchorYaw = cam.getYaw();
        anchorPitch = cam.getPitch();
        anchorCaptured = true;
    }

    public void tick(double dtSec){
        if(!playing) return;

        t += dtSec * speed;

        double trackDur = cutscene.durationSec() > 0 ? Math.min(cutscene.durationSec(), cutscene.camera().duration())
                : cutscene.camera().duration();
        if(t >= trackDur){
            t = trackDur;
            playing = false;
        }

        ensureAnchor();

        CameraPose pose = cutscene.camera().sample(t);

        var ctrl = CameraOverrideController.I;
        ctrl.active = true;
        ctrl.fov = pose.fov();
        ctrl.lockX = pose.lockX();
        ctrl.lockY = pose.lockY();
        ctrl.lockZ = pose.lockZ();

        if(cutscene.mode() == Cutscene.CameraMode.ADDITIVE_FOLLOW){
            ctrl.mode = CameraOverrideController.Mode.ADDITIVE_FOLLOW;
            ctrl.offsetX = pose.position().x;
            ctrl.offsetY = pose.position().y;
            ctrl.offsetZ = pose.position().z;
            ctrl.yawOffset = pose.yaw();
            ctrl.pitchOffset = pose.pitch();
        } else {
            ctrl.mode = CameraOverrideController.Mode.ABSOLUTE;

            Vec3d basePos = anchorCaptured ? anchorPos : MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            float baseYaw = anchorCaptured ? anchorYaw : MinecraftClient.getInstance().gameRenderer.getCamera().getYaw();
            float basePitch = anchorCaptured ? anchorPitch : MinecraftClient.getInstance().gameRenderer.getCamera().getPitch();

            ctrl.posX = basePos.x + pose.position().x;
            ctrl.posY = basePos.y + pose.position().y;
            ctrl.posZ = basePos.z + pose.position().z;

            ctrl.yaw = baseYaw + pose.yaw();
            ctrl.pitch = basePitch + pose.pitch();
        }
    }

    public boolean isPlaying(){ return playing; }
}
