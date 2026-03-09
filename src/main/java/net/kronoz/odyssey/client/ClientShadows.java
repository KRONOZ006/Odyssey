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
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.light.VeilNativeOcclusionMode;
import com.mojang.blaze3d.platform.GlDebugInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared dynamic-light coordinator:
 * - keeps all Veil lights in a valid runtime range
 * - applies native/cpu occlusion consistently for all light variants
 * - smooths near-camera behavior for very large lights
 */
public final class ClientShadows {
    private static final int MIN_SAMPLE_INTERVAL = 2;
    private static final float MIN_POINT_FACTOR = 0.10f;
    private static final float MIN_AREA_FACTOR = 0.12f;
    private static final float MIN_RADIUS_SCALE = 0.32f;
    private static final float MIN_DISTANCE = 0.45f;
    private static final float POINT_RADIUS_MIN = 0.45f;
    private static final float EPSILON = 0.001f;

    private static final Map<LightRenderHandle<?>, OcclusionState> OCCLUSION_STATE = new IdentityHashMap<>();
    private static long lastProcessedWorldTick = Long.MIN_VALUE;
    private static boolean initialized;
    private static int nativeModeFingerprint = Integer.MIN_VALUE;

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

    private static final class RuntimeSettings {
        final boolean occlusionEnabled;
        final boolean nativeOcclusionEnabled;
        final int quality;
        final float cpuOcclusionRangeSq;
        final float maxPointRadius;
        final float maxAreaDistance;
        final float maxBrightness;
        final int sampleBudget;

        RuntimeSettings(boolean occlusionEnabled,
                        boolean nativeOcclusionEnabled,
                        int quality,
                        float cpuOcclusionRangeSq,
                        float maxPointRadius,
                        float maxAreaDistance,
                        float maxBrightness,
                        int sampleBudget) {
            this.occlusionEnabled = occlusionEnabled;
            this.nativeOcclusionEnabled = nativeOcclusionEnabled;
            this.quality = quality;
            this.cpuOcclusionRangeSq = cpuOcclusionRangeSq;
            this.maxPointRadius = maxPointRadius;
            this.maxAreaDistance = maxAreaDistance;
            this.maxBrightness = maxBrightness;
            this.sampleBudget = sampleBudget;
        }
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
        nativeModeFingerprint = Integer.MIN_VALUE;
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

        int stride = OdysseyConfig.effectiveLightUpdateStride();
        if (stride > 1 && (world.getTime() % stride) != 0) {
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
        RuntimeSettings settings = currentSettings();
        Vec3d cameraPos = cameraPos(client);
        int sampleBudget = settings.sampleBudget;
        Set<LightRenderHandle<?>> seenHandles = Collections.newSetFromMap(new IdentityHashMap<>());
        int activeLights = 0;

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
                activeLights++;
                OcclusionState state = OCCLUSION_STATE.computeIfAbsent(handle, k -> new OcclusionState());
                state.handle = handle;
                state.lastSeenTick = world.getTime();

                if (lightData instanceof PointLightData point) {
                    sampleBudget = processPointLight(world, cameraPos, world.getTime(), point, handle, state, sampleBudget, settings);
                } else if (lightData instanceof AreaLightData area) {
                    sampleBudget = processAreaLight(world, cameraPos, world.getTime(), area, handle, state, sampleBudget, settings);
                }
            }
        }

        cleanupStaleStates(seenHandles, world.getTime());

        if (OdysseyConfig.debugLightState && world.getTime() % 40L == 0L) {
            Odyssey.LOGGER.info(
                    "ClientShadows: activeLights={}, trackedStates={}, nativeOcclusion={}, cpuRange={}",
                    activeLights,
                    OCCLUSION_STATE.size(),
                    settings.nativeOcclusionEnabled,
                    (int) Math.sqrt(settings.cpuOcclusionRangeSq)
            );
        }
    }

    private static int processPointLight(ClientWorld world,
                                         Vec3d cameraPos,
                                         long worldTick,
                                         PointLightData light,
                                         LightRenderHandle<?> handle,
                                         OcclusionState state,
                                         int sampleBudget,
                                         RuntimeSettings settings) {
        boolean dirty = false;

        float currentBrightness = sanitize(light.getBrightness(), 0.0f, settings.maxBrightness);
        float currentRadius = sanitize(light.getRadius(), POINT_RADIUS_MIN, settings.maxPointRadius);

        if (Math.abs(light.getBrightness() - currentBrightness) > EPSILON) {
            light.setBrightness(currentBrightness);
            dirty = true;
        }
        if (Math.abs(light.getRadius() - currentRadius) > EPSILON) {
            light.setRadius(currentRadius);
            dirty = true;
        }

        boolean nativeOcclusion = settings.occlusionEnabled && settings.nativeOcclusionEnabled;
        if (light.isOcclusionEnabled() != nativeOcclusion) {
            light.setOcclusionEnabled(nativeOcclusion);
            dirty = true;
        }

        if (!settings.occlusionEnabled) {
            state.baseBrightness = currentBrightness;
            state.baseRadius = currentRadius;
            state.appliedBrightness = currentBrightness;
            state.appliedRadius = currentRadius;
            state.pointFactor = 1.0f;
            if (dirty) {
                handle.markDirty();
            }
            return sampleBudget;
        }

        if (shouldCaptureBase(currentBrightness, state.appliedBrightness)
                || shouldCaptureBase(currentRadius, state.appliedRadius)
                || Float.isNaN(state.baseBrightness)
                || Float.isNaN(state.baseRadius)) {
            state.baseBrightness = currentBrightness;
            state.baseRadius = currentRadius;
        }

        Vec3d origin = pointPosition(light);
        double cameraDistanceSq = origin.squaredDistanceTo(cameraPos);
        boolean cpuOcclusionActive = cameraDistanceSq <= settings.cpuOcclusionRangeSq;

        if (!cpuOcclusionActive) {
            state.pointFactor = 1.0f;
        } else if (worldTick >= state.nextSampleTick && sampleBudget > 0) {
            float sampledRadius = Math.max(1.0f, Math.min(state.baseRadius, settings.maxPointRadius));
            state.pointFactor = LightOcclusionHelper.pointVisibility(world, origin, sampledRadius, null);
            state.nextSampleTick = worldTick + sampleInterval(sampledRadius, cameraDistanceSq, settings.quality);
            sampleBudget--;
        }

        float factor = cpuOcclusionActive
                ? clamp01(0.30f + 0.70f * state.pointFactor)
                : 1.0f;
        factor = Math.max(MIN_POINT_FACTOR, factor);

        float nearSourceDistanceSq = (state.baseRadius * state.baseRadius) * 0.09f;
        if (cameraDistanceSq <= nearSourceDistanceSq) {
            float nearFloor = 0.66f + (0.06f * settings.quality);
            factor = Math.max(factor, nearFloor);
        }

        float targetBrightness = sanitize(state.baseBrightness * factor, 0.0f, settings.maxBrightness);
        float targetRadius = sanitize(
                state.baseRadius * lerp(MIN_RADIUS_SCALE, 1.0f, factor),
                POINT_RADIUS_MIN,
                settings.maxPointRadius
        );
        targetRadius = stabilizeNearCameraSpan(targetRadius, (float) Math.sqrt(cameraDistanceSq), settings.quality);

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
                                        RuntimeSettings settings) {
        boolean dirty = false;

        float currentBrightness = sanitize(light.getBrightness(), 0.0f, settings.maxBrightness);
        float currentDistance = sanitize(light.getDistance(), MIN_DISTANCE, settings.maxAreaDistance);

        if (Math.abs(light.getBrightness() - currentBrightness) > EPSILON) {
            light.setBrightness(currentBrightness);
            dirty = true;
        }
        if (Math.abs(light.getDistance() - currentDistance) > EPSILON) {
            light.setDistance(currentDistance);
            dirty = true;
        }

        boolean nativeOcclusion = settings.occlusionEnabled && settings.nativeOcclusionEnabled;
        if (light.isOcclusionEnabled() != nativeOcclusion) {
            light.setOcclusionEnabled(nativeOcclusion);
            dirty = true;
        }

        if (!settings.occlusionEnabled) {
            state.baseBrightness = currentBrightness;
            state.baseDistance = currentDistance;
            state.appliedBrightness = currentBrightness;
            state.appliedDistance = currentDistance;
            state.pointFactor = 1.0f;
            state.beamFactor = 1.0f;
            if (dirty) {
                handle.markDirty();
            }
            return sampleBudget;
        }

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
        boolean cpuOcclusionActive = cameraDistanceSq <= settings.cpuOcclusionRangeSq;

        if (!cpuOcclusionActive) {
            state.pointFactor = 1.0f;
            state.beamFactor = 1.0f;
        } else if (worldTick >= state.nextSampleTick && sampleBudget > 0) {
            float maxDistance = Math.max(1.0f, Math.min(state.baseDistance, settings.maxAreaDistance));
            float beamDistance = LightOcclusionHelper.directionalDistance(world, origin, direction, maxDistance, null);
            state.beamFactor = clamp01(beamDistance / maxDistance);
            float localProbeRadius = Math.max(1.25f, Math.min(8.0f, maxDistance * 0.35f));
            state.pointFactor = LightOcclusionHelper.pointVisibility(world, origin, localProbeRadius, null);
            state.nextSampleTick = worldTick + sampleInterval(localProbeRadius, cameraDistanceSq, settings.quality);
            sampleBudget--;
        }

        float factor = cpuOcclusionActive
                ? clamp01(state.pointFactor * (0.35f + 0.65f * state.beamFactor))
                : 1.0f;
        factor = Math.max(MIN_AREA_FACTOR, factor);

        float nearSourceDistanceSq = (state.baseDistance * state.baseDistance) * 0.06f;
        if (cameraDistanceSq <= nearSourceDistanceSq) {
            float nearFloor = 0.60f + (0.07f * settings.quality);
            factor = Math.max(factor, nearFloor);
        }

        float targetBrightness = sanitize(state.baseBrightness * factor, 0.0f, settings.maxBrightness);
        float targetDistance = sanitize(
                state.baseDistance * (0.25f + 0.75f * state.beamFactor),
                MIN_DISTANCE,
                settings.maxAreaDistance
        );
        targetDistance = stabilizeNearCameraSpan(targetDistance, (float) Math.sqrt(cameraDistanceSq), settings.quality);

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

    private static RuntimeSettings currentSettings() {
        int quality = OdysseyConfig.qualityLevel(OdysseyConfig.lightQuality);
        float qualityRangeScale = switch (quality) {
            case 0 -> 0.72f;
            case 1 -> 0.88f;
            case 3 -> 1.16f;
            default -> 1.0f;
        };
        int budgetBase = OdysseyConfig.effectiveMaxOcclusionSamplesPerTick();
        int sampleBudget = switch (quality) {
            case 0 -> Math.max(8, budgetBase / 3);
            case 1 -> Math.max(12, budgetBase / 2);
            case 3 -> Math.min(160, Math.round(budgetBase * 1.35f));
            default -> budgetBase;
        };

        float cpuRange = OdysseyConfig.effectiveCpuOcclusionRange() * qualityRangeScale;
        boolean occlusionEnabled = OdysseyConfig.occlusionEnabled;
        boolean nativeEnabled = occlusionEnabled && VeilNativeOcclusionMode.isNativeEnabled();

        return new RuntimeSettings(
                occlusionEnabled,
                nativeEnabled,
                quality,
                cpuRange * cpuRange,
                OdysseyConfig.effectiveMaxPointRadius(),
                OdysseyConfig.effectiveMaxAreaDistance(),
                OdysseyConfig.effectiveMaxLightBrightness(),
                sampleBudget
        );
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
        return finiteVec(position.x(), position.y(), position.z());
    }

    private static Vec3d areaPosition(AreaLightData light) {
        var position = light.getPosition();
        return finiteVec(position.x, position.y, position.z);
    }

    private static Vec3d areaDirection(AreaLightData light) {
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        light.getOrientation().transform(forward);
        if (!Float.isFinite(forward.x()) || !Float.isFinite(forward.y()) || !Float.isFinite(forward.z())) {
            return new Vec3d(0.0, -1.0, 0.0);
        }
        if (forward.lengthSquared() < 1.0E-6f) {
            return new Vec3d(0.0, -1.0, 0.0);
        }
        forward.normalize();
        return new Vec3d(forward.x(), forward.y(), forward.z());
    }

    private static Vec3d finiteVec(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Vec3d.ZERO;
        }
        return new Vec3d(x, y, z);
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

    private static int sampleInterval(float radius, double cameraDistanceSq, int quality) {
        int base = switch (quality) {
            case 0 -> 6;
            case 1 -> 4;
            case 3 -> 2;
            default -> 3;
        };
        int interval = base + (int) Math.floor(Math.sqrt(cameraDistanceSq) / 18.0);
        if (radius > 10.0f) {
            interval += 2;
        }
        if (radius > 22.0f) {
            interval += 1;
        }

        int maxInterval = switch (quality) {
            case 0 -> 18;
            case 1 -> 14;
            case 3 -> 10;
            default -> 12;
        };
        return Math.max(MIN_SAMPLE_INTERVAL, Math.min(interval, maxInterval));
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

    private static float sanitize(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static float stabilizeNearCameraSpan(float span, float cameraDistance, int quality) {
        if (!Float.isFinite(span) || !Float.isFinite(cameraDistance)) {
            return span;
        }
        float safeDistance = Math.max(0.0f, cameraDistance);
        float softLimit = safeDistance * (2.5f + quality * 0.22f) + 10.0f;
        if (span <= softLimit) {
            return span;
        }
        // Blend toward a safe near-camera bound to avoid giant-light collapse/glitching when the camera is inside.
        return lerp(span, softLimit, 0.42f);
    }

    private static void reset() {
        OCCLUSION_STATE.clear();
        lastProcessedWorldTick = Long.MIN_VALUE;
        nativeModeFingerprint = Integer.MIN_VALUE;
    }

    private static void ensureNativeOcclusionMode() {
        String override = System.getProperty("odyssey.veil.native_occlusion", "auto")
                .trim()
                .toLowerCase(Locale.ROOT);

        int configFingerprint = Objects.hash(
                override,
                OdysseyConfig.occlusionEnabled,
                OdysseyConfig.preferNativeOcclusion
        );
        if (configFingerprint == nativeModeFingerprint) {
            return;
        }
        nativeModeFingerprint = configFingerprint;

        boolean available = true;
        boolean enabled = OdysseyConfig.occlusionEnabled && OdysseyConfig.preferNativeOcclusion;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String vendor = "";
        String renderer = "";
        try {
            vendor = GlDebugInfo.getVendor().toLowerCase(Locale.ROOT);
            renderer = GlDebugInfo.getRenderer().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
        }
        boolean windows = os.contains("win");
        boolean nvidia = vendor.contains("nvidia") || renderer.contains("nvidia");

        if ("on".equals(override) || "true".equals(override) || "1".equals(override)) {
            enabled = true;
        } else if ("off".equals(override) || "false".equals(override) || "0".equals(override)) {
            enabled = false;
        } else if ("auto_safe".equals(override)) {
            // Kept for users who explicitly want conservative behavior on known fragile drivers.
            enabled = !(windows && nvidia);
        }

        VeilNativeOcclusionMode.setNativeAvailable(available);
        VeilNativeOcclusionMode.setNativeEnabled(enabled);

        Odyssey.LOGGER.info(
                "ClientShadows native Veil voxel occlusion: {} (available={}, override={})",
                VeilNativeOcclusionMode.isNativeEnabled() ? "enabled" : "disabled",
                available,
                override
        );
    }
}
