package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.UUID;

public final class WireBridge {
    private static final String BASE = "net.kronoz.odyssey.systems.physics.wire";

    private static Class<?> clsWireDef;
    private static Class<?> clsWireToolState;
    private static Class<?> clsWireWorldRenderer;

    private static MethodHandle mhWireDefCtor;
    private static MethodHandle mhEnsureV4, mhEnsureV6, mhEnsureVMany;
    private static MethodHandle mhStepV8, mhStepVMany;
    private static MethodHandle mhInitRenderer;

    public record Def(int segments, float thickness, float stiffness, float damping, float gravity, float drag, float maxStretch) {}

    public static void initRenderer() {
        try {
            if (clsWireWorldRenderer == null) clsWireWorldRenderer = Class.forName(BASE + ".WireWorldRenderer");
            if (mhInitRenderer == null) {
                Method m = findStatic(clsWireWorldRenderer, "init");
                if (m != null) mhInitRenderer = MethodHandles.lookup().unreflect(m);
            }
            if (mhInitRenderer != null) mhInitRenderer.invoke();
        } catch (Throwable ignored) {}
    }

    /** One-shot: ensure + step + render. Retourne true si la wire a réellement été utilisée. */
    public static boolean ensureAndStep(UUID id, Def def, Vec3d a, Vec3d b, MatrixStack matrices, VertexConsumerProvider buffers) {
        try {
            lazyLoad();
            Object wireDef = newWireDef(def);

            boolean ensured = false;
            // ensure(UUID, WireDef, Vec3d, Vec3d)
            if (mhEnsureV4 == null) {
                Method m = findStatic(clsWireToolState, "ensure", UUID.class, clsWireDef, Vec3d.class, Vec3d.class);
                if (m != null) mhEnsureV4 = MethodHandles.lookup().unreflect(m);
            }
            if (mhEnsureV4 != null) {
                mhEnsureV4.invoke(id, wireDef, a, b);
                ensured = true;
            } else {
                // ensure(UUID, WireDef, Vec3d, boolean, Vec3d, boolean)
                if (mhEnsureV6 == null) {
                    Method m = findStatic(clsWireToolState, "ensure", UUID.class, clsWireDef, Vec3d.class, boolean.class, Vec3d.class, boolean.class);
                    if (m != null) mhEnsureV6 = MethodHandles.lookup().unreflect(m);
                }
                if (mhEnsureV6 != null) {
                    mhEnsureV6.invoke(id, wireDef, a, false, b, true);
                    ensured = true;
                } else {
                    // fallback: première ensure(...) statique trouvée (même “14 args”)
                    if (mhEnsureVMany == null) {
                        for (Method m : clsWireToolState.getDeclaredMethods()) {
                            if (!isStatic(m) || !m.getName().equals("ensure")) continue;
                            mhEnsureVMany = MethodHandles.lookup().unreflect(m);
                            break;
                        }
                    }
                    if (mhEnsureVMany != null) {
                        Object[] args = synthesizeEnsureArgs(mhEnsureVMany.type().parameterArray(), id, wireDef, a, b);
                        if (args != null) {
                            mhEnsureVMany.invokeWithArguments(args);
                            ensured = true;
                        }
                    }
                }
            }
            if (!ensured) return false;

            // stepAndRender(UUID, Vec3d, boolean, Vec3d, boolean, MatrixStack, VertexConsumerProvider, int)
            if (mhStepV8 == null) {
                Method m = findStatic(clsWireToolState, "stepAndRender",
                        UUID.class, Vec3d.class, boolean.class, Vec3d.class, boolean.class,
                        MatrixStack.class, VertexConsumerProvider.class, int.class);
                if (m != null) mhStepV8 = MethodHandles.lookup().unreflect(m);
            }
            if (mhStepV8 != null) {
                int light = 0x00F000F0;
                mhStepV8.invoke(id, a, false, b, true, matrices, buffers, light);
                return true;
            }

            // fallback: n’importe quelle stepAndRender(...)
            if (mhStepVMany == null) {
                for (Method m : clsWireToolState.getDeclaredMethods()) {
                    if (!isStatic(m) || !m.getName().equals("stepAndRender")) continue;
                    mhStepVMany = MethodHandles.lookup().unreflect(m);
                    break;
                }
            }
            if (mhStepVMany != null) {
                Object[] args = synthesizeStepArgs(mhStepVMany.type().parameterArray(), id, a, false, b, true, matrices, buffers);
                if (args != null) {
                    mhStepVMany.invokeWithArguments(args);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /* ===== internals ===== */

    private static void lazyLoad() throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        if (clsWireToolState == null) clsWireToolState = Class.forName(BASE + ".WireToolState");
        if (clsWireDef == null) clsWireDef = Class.forName(BASE + ".WireDef");
        if (mhWireDefCtor == null) {
            try {
                mhWireDefCtor = MethodHandles.lookup().unreflectConstructor(
                        clsWireDef.getDeclaredConstructor(int.class, float.class, float.class, float.class, float.class, float.class, float.class)
                );
            } catch (NoSuchMethodException e) {
                mhWireDefCtor = null; // builder chain fallback
            }
        }
    }

    private static Object newWireDef(Def def) throws Throwable {
        if (mhWireDefCtor != null) {
            return mhWireDefCtor.invoke(def.segments(), def.thickness(), def.stiffness(), def.damping(), def.gravity(), def.drag(), def.maxStretch());
        }
        Object obj = clsWireDef.getDeclaredConstructor().newInstance();
        chain(obj, "segments", def.segments());
        chain(obj, "thickness", def.thickness());
        chain(obj, "stiffness", def.stiffness());
        chain(obj, "damping", def.damping());
        chain(obj, "gravity", def.gravity());
        chain(obj, "drag", def.drag());
        chain(obj, "maxStretch", def.maxStretch());
        return obj;
    }

    private static void chain(Object obj, String methodName, Object arg) {
        try { obj.getClass().getMethod(methodName, arg.getClass()).invoke(obj, arg); }
        catch (Throwable ignored) {}
    }

    private static boolean isStatic(Method m) { return (m.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0; }

    private static Method findStatic(Class<?> owner, String name, Class<?>... types) {
        try { return owner.getMethod(name, types); }
        catch (NoSuchMethodException e) { return null; }
    }

    private static Object[] synthesizeEnsureArgs(Class<?>[] params, UUID id, Object wireDef, Vec3d a, Vec3d b) {
        Object[] out = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            if (p == UUID.class) out[i] = id;
            else if (p.isInstance(wireDef)) out[i] = wireDef;
            else if (p == Vec3d.class) out[i] = firstMissing(out, Vec3d.class) ? a : b;
            else if (p == boolean.class) out[i] = (i % 2 != 0);
            else if (p == int.class) out[i] = 0;
            else if (p == float.class) out[i] = 0f;
            else if (p == double.class) out[i] = 0.0;
            else if (p == Identifier.class) out[i] = Identifier.of("odyssey","textures/misc/rope.png");
            else out[i] = null;
        }
        return out;
    }

    private static Object[] synthesizeStepArgs(Class<?>[] params, UUID id, Vec3d a, boolean pinA, Vec3d b, boolean pinB, MatrixStack matrices, VertexConsumerProvider buffers) {
        Object[] out = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            if (p == UUID.class) out[i] = id;
            else if (p == Vec3d.class) out[i] = firstMissing(out, Vec3d.class) ? a : b;
            else if (p == boolean.class) out[i] = firstMissingBool(out) ? pinA : pinB;
            else if (p == MatrixStack.class) out[i] = matrices;
            else if (p == VertexConsumerProvider.class) out[i] = buffers;
            else if (p == Identifier.class) out[i] = Identifier.of("odyssey","textures/misc/rope.png");
            else if (p == int.class) out[i] = 0x00F000F0;
            else if (p == float.class) out[i] = 0f;
            else if (p == double.class) out[i] = 0.0;
            else out[i] = null;
        }
        return out;
    }

    private static boolean firstMissing(Object[] arr, Class<?> type) {
        for (Object o : arr) if (o != null && type.isInstance(o)) return false;
        return true;
    }
    private static boolean firstMissingBool(Object[] arr) {
        for (Object o : arr) if (o instanceof Boolean) return false;
        return true;
    }
}
