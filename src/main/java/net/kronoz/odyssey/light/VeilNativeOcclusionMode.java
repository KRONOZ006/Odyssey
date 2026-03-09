package net.kronoz.odyssey.light;

public final class VeilNativeOcclusionMode {
    private static volatile boolean nativeOcclusionEnabled = false;

    private VeilNativeOcclusionMode() {
    }

    public static boolean isNativeEnabled() {
        return nativeOcclusionEnabled;
    }

    public static void setNativeEnabled(boolean enabled) {
        nativeOcclusionEnabled = enabled;
    }
}
