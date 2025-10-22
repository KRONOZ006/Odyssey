package net.kronoz.odyssey.systems.cam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kronoz.odyssey.systems.cam.RapidShake;

@Environment(EnvType.CLIENT)
public final class ClientFx {
    private ClientFx() {}

    /** Call once when a sequence begins (e.g., Arcangel SHOOT start). */
    public static void rapidShakeStart(float baseIntensity01, int durationTicks) {
        // Configure a crunchy feel; tweak if you want
        RapidShake.configure(
                baseIntensity01, // baseIntensity (0..1)
                50f,             // Hz (target picks per second)
                0.3f,           // maxHoriz (blocks)
                0.3f,          // maxVert (blocks)
                5.25f,           // maxRollDeg
                10.0f            // snappiness
        );
        RapidShake.enableTimed(baseIntensity01, durationTicks);
    }

    /** Short spike on impact. */
    public static void rapidShakePulse(float addIntensity01, int durationTicks) {
        RapidShake.pulse(addIntensity01, durationTicks);
    }
}
