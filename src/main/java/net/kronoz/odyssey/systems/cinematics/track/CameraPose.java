package net.kronoz.odyssey.systems.cinematics.track;

import net.minecraft.util.math.Vec3d;
public record CameraPose(
        Vec3d position,
        float yaw,
        float pitch,
        float roll,
        float fov,
        boolean lockX, boolean lockY, boolean lockZ
) {}
