package net.kronoz.odyssey.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class OdysseyConfig extends MidnightConfig {
    private static final String CAT_SHADERS = "shaders";
    private static final String CAT_FOG = "fog";
    private static final String CAT_LIGHTS = "lights";
    private static final String CAT_FIRST_PERSON = "first_person";

    @Comment(centered = true) public static Comment shadersComment;
    @Entry(category = CAT_SHADERS) public static boolean enableBloom = true;
    @Entry(category = CAT_SHADERS) public static boolean enableFogPost = true;
    @Entry(category = CAT_SHADERS, isSlider = true, min = 0, max = 3, precision = 0)
    public static int shaderQuality = 2;

    @Comment(centered = true) public static Comment fogComment;
    @Entry(category = CAT_FOG) public static boolean fogVoidOnly = true;
    @Entry(category = CAT_FOG, isSlider = true, min = 0, max = 3, precision = 0)
    public static int fogQuality = 2;
    @Entry(category = CAT_FOG, isSlider = true, min = 1, max = 10, precision = 0)
    public static int fogUpdateStride = 2;

    @Comment(centered = true) public static Comment lightsComment;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 0, max = 3, precision = 0)
    public static int lightQuality = 2;
    @Entry(category = CAT_LIGHTS) public static boolean occlusionEnabled = true;
    @Entry(category = CAT_LIGHTS) public static boolean preferNativeOcclusion = true;
    @Entry(category = CAT_LIGHTS) public static boolean occludeWithEntities = true;
    @Entry(category = CAT_LIGHTS) public static boolean occludeWithModelGeometry = true;
    @Entry(category = CAT_LIGHTS) public static boolean entityShadowingEnabled = true;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 0, max = 3, precision = 0)
    public static int entityShadowQuality = 2;
    @Entry(category = CAT_LIGHTS) public static boolean directionalSunLightEnabled = true;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 0, max = 3, precision = 0)
    public static int directionalShadowQuality = 2;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 1, max = 20, precision = 0)
    public static int directionalUpdateStride = 2;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 0, max = 8, precision = 1)
    public static float directionalDayBrightness = 1.6f;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 0, max = 3, precision = 1)
    public static float directionalNightBrightness = 0.35f;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 24, max = 192, precision = 0)
    public static int cpuOcclusionRange = 96;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 8, max = 128, precision = 0)
    public static int maxOcclusionSamplesPerTick = 26;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 16, max = 200, precision = 0)
    public static int maxPointLightRadius = 84;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 16, max = 260, precision = 0)
    public static int maxAreaLightDistance = 124;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 1, max = 30, precision = 1)
    public static float maxLightBrightness = 16.0f;
    @Entry(category = CAT_LIGHTS, isSlider = true, min = 1, max = 6, precision = 0)
    public static int lightUpdateStride = 1;
    @Entry(category = CAT_LIGHTS) public static boolean debugLightState = false;

    @Comment(centered = true) public static Comment firstPersonComment;
    @Entry(category = CAT_FIRST_PERSON) public static boolean enableFirstPersonOverride = true;
    @Entry(category = CAT_FIRST_PERSON) public static boolean renderArmOverlay = true;

    @Entry(category = CAT_FIRST_PERSON) public static float heldBaseX = 0.525f;
    @Entry(category = CAT_FIRST_PERSON) public static float heldBaseY = -0.32894737f;
    @Entry(category = CAT_FIRST_PERSON) public static float heldBaseZ = -0.90250003f;
    @Entry(category = CAT_FIRST_PERSON) public static float heldRotX = -40.263157f;
    @Entry(category = CAT_FIRST_PERSON) public static float heldRotY = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float heldRotZ = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float heldScale = 0.5276316f;

    @Entry(category = CAT_FIRST_PERSON) public static float swingX = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float swingY = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float swingZ = 0.3f;
    @Entry(category = CAT_FIRST_PERSON) public static float equipX = 0.07657895f;
    @Entry(category = CAT_FIRST_PERSON) public static float equipY = 0.07263158f;
    @Entry(category = CAT_FIRST_PERSON) public static float equipZ = 0.000000007450581f;
    @Entry(category = CAT_FIRST_PERSON) public static float dropYMax = 0.049736843f;
    @Entry(category = CAT_FIRST_PERSON) public static float pushZMax = 0.049473684f;
    @Entry(category = CAT_FIRST_PERSON) public static float inwardXMax = 0.049736843f;
    @Entry(category = CAT_FIRST_PERSON) public static float swingIntensity = 0.47368422f;
    @Entry(category = CAT_FIRST_PERSON) public static float equipIntensity = 0.37105262f;

    @Entry(category = CAT_FIRST_PERSON) public static float pitchXDeg = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float swingRotXDeg = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float swingRotYDeg = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float swingRotZDeg = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float equipRollDeg = 0f;

    @Entry(category = CAT_FIRST_PERSON) public static float overlayBaseX = 0.2631579f;
    @Entry(category = CAT_FIRST_PERSON) public static float overlayBaseY = -0.22105263f;
    @Entry(category = CAT_FIRST_PERSON) public static float overlayBaseZ = -0.3489474f;
    @Entry(category = CAT_FIRST_PERSON) public static float overlayScale = 0.7486842f;

    @Entry(category = CAT_FIRST_PERSON) public static float armBaseX = 0.22894737f;
    @Entry(category = CAT_FIRST_PERSON) public static float armBaseY = -0.17894737f;
    @Entry(category = CAT_FIRST_PERSON) public static float armBaseZ = -0.27789477f;
    @Entry(category = CAT_FIRST_PERSON) public static float armScale = 0.68157893f;
    @Entry(category = CAT_FIRST_PERSON) public static float armRotX = 3.7894738f;
    @Entry(category = CAT_FIRST_PERSON) public static float armRotY = -7.5789475f;
    @Entry(category = CAT_FIRST_PERSON) public static float armRotZ = 16.578947f;
    @Entry(category = CAT_FIRST_PERSON) public static float armSwingRotXDeg = 38.842106f;
    @Entry(category = CAT_FIRST_PERSON) public static float armSwingRotYDeg = 21.157894f;
    @Entry(category = CAT_FIRST_PERSON) public static float armSwingRotZDeg = 0f;
    @Entry(category = CAT_FIRST_PERSON) public static float armEquipRollDeg = 15.098684f;
    @Entry(category = CAT_FIRST_PERSON) public static float armPitchXDeg = 44.88158f;

    public static int qualityLevel(int raw) {
        return Math.max(0, Math.min(3, raw));
    }

    public static int effectiveLightUpdateStride() {
        return Math.max(1, Math.min(6, lightUpdateStride));
    }

    public static int effectiveFogUpdateStride() {
        return Math.max(1, Math.min(10, fogUpdateStride));
    }

    public static int effectiveMaxOcclusionSamplesPerTick() {
        return Math.max(8, Math.min(128, maxOcclusionSamplesPerTick));
    }

    public static float effectiveCpuOcclusionRange() {
        return Math.max(24.0f, Math.min(192.0f, cpuOcclusionRange));
    }

    public static float effectiveMaxPointRadius() {
        return Math.max(16.0f, Math.min(200.0f, maxPointLightRadius));
    }

    public static float effectiveMaxAreaDistance() {
        return Math.max(16.0f, Math.min(260.0f, maxAreaLightDistance));
    }

    public static float effectiveMaxLightBrightness() {
        return Math.max(1.0f, Math.min(30.0f, maxLightBrightness));
    }

    public static int effectiveEntityShadowQuality() {
        return qualityLevel(entityShadowQuality);
    }

    public static int effectiveDirectionalShadowQuality() {
        return qualityLevel(directionalShadowQuality);
    }

    public static int effectiveDirectionalUpdateStride() {
        return Math.max(1, Math.min(20, directionalUpdateStride));
    }

    public static float effectiveDirectionalDayBrightness() {
        return Math.max(0.0f, Math.min(8.0f, directionalDayBrightness));
    }

    public static float effectiveDirectionalNightBrightness() {
        return Math.max(0.0f, Math.min(3.0f, directionalNightBrightness));
    }
}
