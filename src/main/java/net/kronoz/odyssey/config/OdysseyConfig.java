package net.kronoz.odyssey.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class OdysseyConfig extends MidnightConfig {
    // OdysseyConfig.java  (seulement les champs + valeurs par défaut)
    @Entry public static boolean enableFirstPersonOverride = true;
    @Entry public static boolean renderArmOverlay = true;

    @Entry public static float heldBaseX = 0.525f;
    @Entry public static float heldBaseY = -0.32894737f;
    @Entry public static float heldBaseZ = -0.90250003f;
    @Entry public static float heldRotX  = -40.263157f;
    @Entry public static float heldRotY  = 0f;
    @Entry public static float heldRotZ  = 0f;
    @Entry public static float heldScale = 0.5276316f;

    @Entry public static float swingX = 0f;
    @Entry public static float swingY = 0f;
    @Entry public static float swingZ = 0.3f;
    @Entry public static float equipX = 0.07657895f;
    @Entry public static float equipY = 0.07263158f;
    @Entry public static float equipZ = 0.000000007450581f; // keep as float
    @Entry public static float dropYMax   = 0.049736843f;
    @Entry public static float pushZMax   = 0.049473684f;
    @Entry public static float inwardXMax = 0.049736843f;
    @Entry public static float swingIntensity = 0.47368422f;
    @Entry public static float equipIntensity = 0.37105262f;

    @Entry public static float pitchXDeg = 0f;
    @Entry public static float swingRotXDeg = 0f;
    @Entry public static float swingRotYDeg = 0f;
    @Entry public static float swingRotZDeg = 0f;
    @Entry public static float equipRollDeg  = 0f;

    @Entry public static float overlayBaseX = 0.2631579f;
    @Entry public static float overlayBaseY = -0.22105263f;
    @Entry public static float overlayBaseZ = -0.3489474f;
    @Entry public static float overlayScale = 0.7486842f;

    @Entry public static float armBaseX = 0.22894737f;
    @Entry public static float armBaseY = -0.17894737f;
    @Entry public static float armBaseZ = -0.27789477f;
    @Entry public static float armScale = 0.68157893f;
    @Entry public static float armRotX  = 3.7894738f;
    @Entry public static float armRotY  = -7.5789475f;
    @Entry public static float armRotZ  = 16.578947f;
    @Entry public static float armSwingRotXDeg = 38.842106f;
    @Entry public static float armSwingRotYDeg = 21.157894f;
    @Entry public static float armSwingRotZDeg = 0f;
    @Entry public static float armEquipRollDeg = 15.098684f;
    @Entry public static float armPitchXDeg    = 44.88158f;

}
