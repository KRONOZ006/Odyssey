package net.kronoz.odyssey.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.client.render.light.renderer.LightTypeRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.light.VeilNativeOcclusionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight dynamic-light occlusion coordinator.
 * This keeps Veil's native light pipeline, enables Veil voxel occlusion,
 * and adds a cached CPU-side visibility factor to reduce wall bleeding.
 */
public final class ClientShadows {
    private static final int MAX_SAMPLES_PER_TICK = 26;
    private static final int BASE_SAMPLE_INTERVAL = 3;
    private static final int MAX_SAMPLE_INTERVAL = 12;
    private static final float CPU_OCCLUSION_RANGE = 96.0f;
    private static final float CPU_OCCLUSION_RANGE_SQ = CPU_OCCLUSION_RANGE * CPU_OCCLUSION_RANGE;
    private static final float MIN_POINT_FACTOR = 0.10f;
    private static final float MIN_AREA_FACTOR = 0.12f;
    private static final float MIN_RADIUS_SCALE = 0.32f;
    private static final float MIN_DISTANCE = 0.45f;
    private static final float POINT_RADIUS_MIN = 0.45f;
    private static final float EPSILON = 0.001f;

    private static final Map<LightRenderHandle<?>, OcclusionState> OCCLUSION_STATE = new IdentityHashMap<>();
    private static long lastProcessedWorldTick = Long.MIN_VALUE;
    private static boolean initialized;
    private static boolean nativeModeResolved;

    private static final class OcclusionState {
        LightRenderHandle<?> handle;
        long lastSeenTick;
        long nextSampleTick;

        float baseBrightness = Float.NaN;
        float baseRadius = Float.NaN;
        float baseDistance = Float.NaN;

        float appliedBrightness = Float.NaN;
        float appliedRadius = Float.NaN;
        float appliedDistance = Float.NaN;

        float pointFactor = 1.0f;
        float beamFactor = 1.0f;
    }

    private ClientShadows() {
    }

    public static void initClient() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(ClientShadows::tick);
        Odyssey.LOGGER.info("ClientShadows dynamic-light occlusion enabled.");
    }

    public static void refreshShadowConfig() {
        // Compatibility no-op. Dynamic-light settings are driven directly at runtime.
    }

    public static float samplePointVisibility(ClientWorld world, Vec3d origin, float radius, @Nullable Entity ignored) {
        return LightOcclusionHelper.pointVisibility(world, origin, radius, ignored);
    }

    public static float sampleDirectionalDistance(ClientWorld world, Vec3d origin, Vec3d direction, float maxDistance, @Nullable Entity ignored) {
        return LightOcclusionHelper.directionalDistance(world, origin, direction, maxDistance, ignored);
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            reset();
            return;
        }

        if (world.getTime() == lastProcessedWorldTick) {
            return;
        }
        lastProcessedWorldTick = world.getTime();

        var renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }

        LightRenderer lightRenderer = renderer.getLightRenderer();
        if (lightRenderer == null) {
            return;
        }

        ensureNativeOcclusionMode();
        boolean nativeOcclusionEnabled = VeilNativeOcclusionMode.isNativeEnabled();
        Vec3d cameraPos = cameraPos(client);
        int sampleBudget = MAX_SAMPLES_PER_TICK;
        Set<LightRenderHandle<?>> seenHandles = Collections.newSetFromMap(new IdentityHashMap<>());

        for (LightTypeRenderer<?> typeRenderer : lightRenderer.getRenderers().values()) {
            for (LightRenderHandle<?> handle : typeRenderer.getLights()) {
                if (handle == null || !handle.isValid()) {
                    continue;
                }

                Object lightData = handle.getLightData();
                if (!(lightData instanceof PointLightData) && !(lightData instanceof AreaLightData)) {
                    continue;
                }

                seenHandles.add(handle);
                OcclusionState state = OCCLUSION_STATE.computeIfAbsent(handle, k -> new OcclusionState());
                state.handle = handle;
                state.lastSeenTick = world.getTime();

                if (lightData instanceof PointLightData point) {
                    sampleBudget = processPointLight(world, cameraPos, world.getTime(), point, handle, state, sampleBudget, nativeOcclusionEnabled);
                } else if (lightData instanceof AreaLightData area) {
                    sampleBudget = processAreaLight(world, cameraPos, world.getTime(), area, handle, state, sampleBudget, nativeOcclusionEnabled);
                }
            }
        }

        cleanupStaleStates(seenHandles, world.getTime());
    }

    private static int processPointLight(ClientWorld world,
                                         Vec3d cameraPos,
                                         long worldTick,
                                         PointLightData light,
                                         LightRenderHandle<?> handle,
                                         OcclusionState state,
                                         int sampleBudget,
                                         boolean nativeOcclusionEnabled) {
        boolean dirty = false;

        if (light.isOcclusionEnabled() != nativeOcclusionEnabled) {
            light.setOcclusionEnabled(nativeOcclusionEnabled);
            dirty = true;
        }

        float currentBrightness = Math.max(0.0f, light.getBrightness());
        float currentRadius = Math.max(POINT_RADIUS_MIN, light.getRadius());
        if (shouldCaptureBase(currentBrightness, state.appliedBrightness)
                || shouldCaptureBase(currentRadius, state.appliedRadius)
                || Float.isNaN(state.baseBrightness)
                || Float.isNaN(state.baseRadius)) {
            state.baseBrightness = currentBrightness;
            state.baseRadius = currentRadius;
        }

        Vec3d origin = pointPosition(light);
        double cameraDistanceSq = origin.squaredDistanceTo(cameraPos);
        boolean cpuOcclusionActive = cameraDistanceSq <= CPU_OCCLUSION_RANGE_SQ;

        if (!cpuOcclusionActive) {
            state.pointFactor = 1.0f;
        } else if (worldTick >= state.nextSampleTick && sampleBudget > 0) {
            float sampledRadius = Math.max(1.0f, state.baseRadius);
            state.pointFactor = LightOcclusionHelper.pointVisibility(world, origin, sampledRadius, null);
            state.nextSampleTick = worldTick + sampleInterval(sampledRadius, cameraDistanceSq);
            sampleBudget--;
        }

        float factor = cpuOcclusionActive
                ? clamp01(0.30f + 0.70f * state.pointFactor)
                : 1.0f;
        factor = Math.max(MIN_POINT_FACTOR, factor);

        float targetBrightness = state.baseBrightness * factor;
        float targetRadius = Math.max(POINT_RADIUS_MIN, state.baseRadius * lerp(MIN_RADIUS_SCALE, 1.0f, factor));

        if (Math.abs(light.getBrightness() - targetBrightness) > EPSILON) {
            light.setBrightness(targetBrightness);
            dirty = true;
        }
        if (Math.abs(light.getRadius() - targetRadius) > EPSILON) {
            light.setRadius(targetRadius);
            dirty = true;
        }

        if (dirty) {
            handle.markDirty();
        }

        state.appliedBrightness = targetBrightness;
        state.appliedRadius = targetRadius;
        return sampleBudget;
    }

    private static int processAreaLight(ClientWorld world,
                                        Vec3d cameraPos,
                                        long worldTick,
                                        AreaLightData light,
                                        LightRenderHandle<?> handle,
                                        OcclusionState state,
                                        int sampleBudget,
                                        boolean nativeOcclusionEnabled) {
        boolean dirty = false;

        if (light.isOcclusionEnabled() != nativeOcclusionEnabled) {
            light.setOcclusionEnabled(nativeOcclusionEnabled);
            dirty = true;
        }

        float currentBrightness = Math.max(0.0f, light.getBrightness());
        float currentDistance = Math.max(MIN_DISTANCE, light.getDistance());
        if (shouldCaptureBase(currentBrightness, state.appliedBrightness)
                || shouldCaptureBase(currentDistance, state.appliedDistance)
                || Float.isNaN(state.baseBrightness)
                || Float.isNaN(state.baseDistance)) {
            state.baseBrightness = currentBrightness;
            state.baseDistance = currentDistance;
        }

        Vec3d origin = areaPosition(light);
        Vec3d direction = areaDirection(light);
        double cameraDistanceSq = origin.squaredDistanceTo(cameraPos);
        boolean cpuOcclusionActive = cameraDistanceSq <= CPU_OCCLUSION_RANGE_SQ;

        if (!cpuOcclusionActive) {
            state.pointFactor = 1.0f;
            state.beamFactor = 1.0f;
        } else if (worldTick >= state.nextSampleTick && sampleBudget > 0) {
            float maxDistance = Math.max(1.0f, state.baseDistance);
            float beamDistance = LightOcclusionHelper.directionalDistance(world, origin, direction, maxDistance, null);
            state.beamFactor = clamp01(beamDistance / maxDistance);
            float localProbeRadius = Math.max(1.25f, Math.min(8.0f, state.baseDistance * 0.35f));
            state.pointFactor = LightOcclusionHelper.pointVisibility(world, origin, localProbeRadius, null);
            state.nextSampleTick = worldTick + sampleInterval(localProbeRadius, cameraDistanceSq);
            sampleBudget--;
        }

        float factor = cpuOcclusionActive
                ? clamp01(
                state.pointFactor
                        * (0.35f + 0.65f * state.beamFactor))
                : 1.0f;
        factor = Math.max(MIN_AREA_FACTOR, factor);

        float targetBrightness = state.baseBrightness * factor;
        float targetDistance = Math.max(MIN_DISTANCE, state.baseDistance * (0.25f + 0.75f * state.beamFactor));

        if (Math.abs(light.getBrightness() - targetBrightness) > EPSILON) {
            light.setBrightness(targetBrightness);
            dirty = true;
        }
        if (Math.abs(light.getDistance() - targetDistance) > EPSILON) {
            light.setDistance(targetDistance);
            dirty = true;
        }

        if (dirty) {
            handle.markDirty();
        }

        state.appliedBrightness = targetBrightness;
        state.appliedDistance = targetDistance;
        return sampleBudget;
    }

    private static void cleanupStaleStates(Set<LightRenderHandle<?>> seenHandles, long worldTick) {
        Iterator<Map.Entry<LightRenderHandle<?>, OcclusionState>> iterator = OCCLUSION_STATE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LightRenderHandle<?>, OcclusionState> entry = iterator.next();
            OcclusionState state = entry.getValue();
            boolean stale = state == null
                    || state.handle == null
                    || !state.handle.isValid()
                    || !seenHandles.contains(state.handle)
                    || (worldTick - state.lastSeenTick) > 5L;
            if (stale) {
                iterator.remove();
            }
        }
    }

    private static Vec3d pointPosition(PointLightData light) {
        var position = light.getPosition();
        return new Vec3d(position.x(), position.y(), position.z());
    }

    private static Vec3d areaPosition(AreaLightData light) {
        var position = light.getPosition();
        return new Vec3d(position.x, position.y, position.z);
    }

    private static Vec3d areaDirection(AreaLightData light) {
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        light.getOrientation().transform(forward);
        if (forward.lengthSquared() < 1.0E-6f) {
            return new Vec3d(0.0, -1.0, 0.0);
        }
        forward.normalize();
        return new Vec3d(forward.x(), forward.y(), forward.z());
    }

    private static Vec3d cameraPos(MinecraftClient client) {
        if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            return client.gameRenderer.getCamera().getPos();
        }
        if (client != null && client.player != null) {
            return client.player.getEyePos();
        }
        return Vec3d.ZERO;
    }

    private static boolean shouldCaptureBase(float currentValue, float appliedValue) {
        return Float.isNaN(appliedValue) || Math.abs(currentValue - appliedValue) > 0.002f;
    }

    private static int sampleInterval(float radius, double cameraDistanceSq) {
        int interval = BASE_SAMPLE_INTERVAL;
        interval += (int) Math.floor(Math.sqrt(cameraDistanceSq) / 18.0);
        if (radius > 10.0f) {
            interval += 2;
        }
        if (radius > 22.0f) {
            interval += 1;
        }
        if (interval < 2) {
            return 2;
        }
        return Math.min(interval, MAX_SAMPLE_INTERVAL);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float value) {
        if (value <= 0.0f) {
            return 0.0f;
        }
        if (value >= 1.0f) {
            return 1.0f;
        }
        return value;
    }

    private static void reset() {
        OCCLUSION_STATE.clear();
        lastProcessedWorldTick = Long.MIN_VALUE;
        nativeModeResolved = false;
    }

    private static void ensureNativeOcclusionMode() {
        if (nativeModeResolved) {
            return;
        }
        nativeModeResolved = true;

        String override = System.getProperty("odyssey.veil.native_occlusion", "auto")
                .trim()
                .toLowerCase(Locale.ROOT);
        boolean enabled;
        if ("on".equals(override) || "true".equals(override) || "1".equals(override)) {
            enabled = true;
        } else if ("off".equals(override) || "false".equals(override) || "0".equals(override)) {
            enabled = false;
        } else {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String vendor = safeGlString(GL11C.GL_VENDOR).toLowerCase(Locale.ROOT);
            String renderer = safeGlString(GL11C.GL_RENDERER).toLowerCase(Locale.ROOT);
            boolean windows = os.contains("win");
            boolean nvidia = vendor.contains("nvidia") || renderer.contains("nvidia");
            // VoxelShadowGrid 3D texture upload is unstable on some NVIDIA+Windows setups.
            enabled = !(windows && nvidia);
        }

        VeilNativeOcclusionMode.setNativeEnabled(enabled);
        Odyssey.LOGGER.info(
                "ClientShadows native Veil voxel occlusion: {} (override={})",
                enabled ? "enabled" : "disabled",
                override
        );
    }

    private static String safeGlString(int key) {
        try {
            String value = GL11C.glGetString(key);
            return value != null ? value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
