package net.kronoz.odyssey.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public final class VeilLightCompat {
    private static MethodHandle mhSubmit;
    private static boolean tried;

    private static void ensureInit() {
        if (tried) return;
        tried = true;
        try {
            String[] candidates = new String[] {
                "dev.kosmx.veil.api.client.lighting.LightRenderer",
                "gg.moonflower.veil.api.client.light.LightRenderer",
                "dev.melvinj.veil.client.light.LightRenderer"
            };
            MethodHandles.Lookup lk = MethodHandles.lookup();
            for (String cn : candidates) {
                try {
                    Class<?> cls = Class.forName(cn);
                    for (Method m : cls.getDeclaredMethods()) {
                        if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                        String name = m.getName().toLowerCase();
                        if (name.contains("submit") || name.contains("add")) {
                            Class<?>[] p = m.getParameterTypes();
                            if (p.length >= 8) {
                                m.setAccessible(true);
                                mhSubmit = lk.unreflect(m);
                                return;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void emitPointLight(double x, double y, double z,
                                      int r, int g, int b,
                                      float radius, float intensity,
                                      int ttlTicks) {
        ensureInit();
        if (mhSubmit == null) return;

        try {
            Object[] args = new Object[] { x, y, z, r, g, b, radius, intensity, ttlTicks };
            mhSubmit.invokeWithArguments(args);
        } catch (Throwable ignored) {
        }
    }

    private VeilLightCompat() {}
}
