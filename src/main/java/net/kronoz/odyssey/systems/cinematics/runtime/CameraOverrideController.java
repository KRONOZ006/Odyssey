package net.kronoz.odyssey.systems.cinematics.runtime;

public final class CameraOverrideController {
    public enum Mode { ABSOLUTE, ADDITIVE_FOLLOW }

    public static final CameraOverrideController I = new CameraOverrideController();

    public volatile boolean active = false;

    public volatile Mode mode = Mode.ADDITIVE_FOLLOW;

    public volatile double posX, posY, posZ;
    public volatile float yaw, pitch;

    public volatile double offsetX, offsetY, offsetZ;
    public volatile float yawOffset, pitchOffset;

    public volatile float fov = 70f;

    public volatile boolean lockX, lockY, lockZ;

    private CameraOverrideController(){}

    public void clear(){
        active = false;
        mode = Mode.ADDITIVE_FOLLOW;
        posX = posY = posZ = 0.0;
        yaw = pitch = 0f;
        offsetX = offsetY = offsetZ = 0.0;
        yawOffset = pitchOffset = 0f;
        fov = 70f;
        lockX = lockY = lockZ = false;
    }
}
