package net.kronoz.odyssey.light;

public final class VeilNativeOcclusionMode {
    private static volatile boolean nativeOcclusionEnabled = true;
    private static volatile boolean nativeOcclusionAvailable = true;

    private VeilNativeOcclusionMode() {
    }

    public static boolean isNativeEnabled() {
        return nativeOcclusionEnabled && nativeOcclusionAvailable;
    }

    public static void setNativeEnabled(boolean enabled) {
        nativeOcclusionEnabled = enabled;
    }

    public static boolean isNativeAvailable() {
        return nativeOcclusionAvailable;
    }

    public static void setNativeAvailable(boolean available) {
        nativeOcclusionAvailable = available;
    }
}
