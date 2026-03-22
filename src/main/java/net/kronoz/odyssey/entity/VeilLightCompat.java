package net.kronoz.odyssey.entity;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.light.VeilNativeOcclusionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Stable point light compatibility layer for gameplay/entity-driven Veil lights.
 *
 * Design goals:
 * - Light lifetime is producer-driven (TTL/remaining ticks), never camera/occlusion-driven.
 * - Temporary Veil handle invalidation is treated as recoverable; lights are recreated from cached state.
 * - Occlusion changes contribution only (via light settings/ClientShadows), never instance ownership.
 */
public final class VeilLightCompat {
    private static final float EPSILON = 0.001f;
    private static final int RESET_IDLE_TTL_TICKS = 60;

    private static final Map<Integer, ManagedPointLight> LIGHTS = new HashMap<>();
    private static final Map<Integer, PendingUpdate> PENDING = new HashMap<>();

    private static boolean initialized;

    private VeilLightCompat() {
    }

    public static void initClient() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearAll());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
        ClientTickEvents.END_CLIENT_TICK.register(VeilLightCompat::tick);
    }

    public static void updateWithLifetime(int id,
                                          double x,
                                          double y,
                                          double z,
                                          float r,
                                          float g,
                                          float b,
                                          float brightness,
                                          float radius,
                                          int remainingTicks) {
        float clampedBrightness = sanitize(brightness, 0.0f, OdysseyConfig.effectiveMaxLightBrightness());
        float clampedRadius = sanitize(radius, 0.0f, OdysseyConfig.effectiveMaxPointRadius());
        int clampedRemaining = Math.max(0, remainingTicks);

        if (clampedBrightness <= 0.0f || clampedRadius <= 0.0f || clampedRemaining <= 0) {
            remove(id, "inactive-update");
            return;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }

        PendingUpdate pending = new PendingUpdate(
                x,
                y,
                z,
                sanitize(r, 0.0f, 1.0f),
                sanitize(g, 0.0f, 1.0f),
                sanitize(b, 0.0f, 1.0f),
                clampedBrightness,
                clampedRadius,
                clampedRemaining
        );

        ManagedPointLight managed = LIGHTS.computeIfAbsent(id, ignored -> new ManagedPointLight());
        managed.ingest(pending);

        if (!rendererReady()) {
            PENDING.put(id, pending);
            return;
        }

        PENDING.remove(id);
        if (!ensureHandle(managed)) {
            PENDING.put(id, pending);
            return;
        }
        applyToData(managed, nativeOcclusionEnabled());
    }

    public static void update(int id,
                              double x,
                              double y,
                              double z,
                              float r,
                              float g,
                              float b,
                              float brightness,
                              float radius) {
        updateWithLifetime(id, x, y, z, r, g, b, brightness, radius, RESET_IDLE_TTL_TICKS);
    }

    public static void remove(int id, String reason) {
        ManagedPointLight managed = LIGHTS.remove(id);
        if (managed != null) {
            managed.close();
        }
        PENDING.remove(id);
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            clearAll();
            return;
        }

        boolean rendererReady = rendererReady();
        if (rendererReady && !PENDING.isEmpty()) {
            List<Map.Entry<Integer, PendingUpdate>> toApply = List.copyOf(PENDING.entrySet());
            for (Map.Entry<Integer, PendingUpdate> entry : toApply) {
                PendingUpdate update = entry.getValue();
                if (update == null || update.remainingTicks <= 0 || update.brightness <= 0.0f || update.radius <= 0.0f) {
                    PENDING.remove(entry.getKey());
                    continue;
                }
                updateWithLifetime(
                        entry.getKey(),
                        update.x,
                        update.y,
                        update.z,
                        update.r,
                        update.g,
                        update.b,
                        update.brightness,
                        update.radius,
                        update.remainingTicks
                );
            }
        } else if (!rendererReady && !PENDING.isEmpty()) {
            Iterator<Map.Entry<Integer, PendingUpdate>> pendingIterator = PENDING.entrySet().iterator();
            while (pendingIterator.hasNext()) {
                Map.Entry<Integer, PendingUpdate> entry = pendingIterator.next();
                PendingUpdate current = entry.getValue();
                if (current == null) {
                    pendingIterator.remove();
                    continue;
                }
                int remaining = current.remainingTicks - 1;
                if (remaining <= 0) {
                    pendingIterator.remove();
                } else {
                    entry.setValue(current.withRemaining(remaining));
                }
            }
        }

        boolean nativeOcclusion = nativeOcclusionEnabled();
        Iterator<Map.Entry<Integer, ManagedPointLight>> iterator = LIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ManagedPointLight> entry = iterator.next();
            ManagedPointLight managed = entry.getValue();
            if (managed == null) {
                iterator.remove();
                continue;
            }

            managed.idleTicks--;
            managed.remainingTicks--;

            if (managed.idleTicks <= 0 || managed.remainingTicks <= 0 || managed.targetBrightness <= 0.0f || managed.targetRadius <= 0.0f) {
                managed.close();
                iterator.remove();
                PENDING.remove(entry.getKey());
                continue;
            }

            if (!rendererReady) {
                continue;
            }

            if (!ensureHandle(managed)) {
                continue;
            }
            applyToData(managed, nativeOcclusion);
        }
    }

    private static void clearAll() {
        for (ManagedPointLight managed : LIGHTS.values()) {
            if (managed != null) {
                managed.close();
            }
        }
        LIGHTS.clear();
        PENDING.clear();
    }

    private static boolean ensureHandle(ManagedPointLight managed) {
        if (managed == null || !rendererReady()) {
            return false;
        }

        if (managed.handle != null && managed.handle.isValid() && managed.data != null) {
            return true;
        }

        if (managed.handle != null && managed.handle.isValid()) {
            managed.handle.close();
        }

        PointLightData data = new PointLightData()
                .setBrightness(managed.targetBrightness)
                .setColor(managed.targetR, managed.targetG, managed.targetB)
                .setRadius(managed.targetRadius)
                .setOcclusionEnabled(nativeOcclusionEnabled())
                .setPosition(managed.targetX, managed.targetY, managed.targetZ);

        LightRenderHandle<PointLightData> handle = VeilRenderSystem.renderer()
                .getLightRenderer()
                .addLight(data);

        managed.data = data;
        managed.handle = handle;
        managed.lastAppliedBrightness = Float.NaN;
        managed.lastAppliedRadius = Float.NaN;
        managed.lastAppliedR = Float.NaN;
        managed.lastAppliedG = Float.NaN;
        managed.lastAppliedB = Float.NaN;
        managed.lastAppliedOcclusion = !data.isOcclusionEnabled();
        managed.lastAppliedX = Double.NaN;
        managed.lastAppliedY = Double.NaN;
        managed.lastAppliedZ = Double.NaN;
        return handle != null && handle.isValid();
    }

    private static void applyToData(ManagedPointLight managed, boolean nativeOcclusion) {
        if (managed == null || managed.data == null || managed.handle == null || !managed.handle.isValid()) {
            return;
        }

        boolean dirty = false;

        if (Math.abs(managed.lastAppliedBrightness - managed.targetBrightness) > EPSILON) {
            managed.data.setBrightness(managed.targetBrightness);
            managed.lastAppliedBrightness = managed.targetBrightness;
            dirty = true;
        }
        if (Math.abs(managed.lastAppliedRadius - managed.targetRadius) > EPSILON) {
            managed.data.setRadius(managed.targetRadius);
            managed.lastAppliedRadius = managed.targetRadius;
            dirty = true;
        }
        if (Math.abs(managed.lastAppliedR - managed.targetR) > EPSILON
                || Math.abs(managed.lastAppliedG - managed.targetG) > EPSILON
                || Math.abs(managed.lastAppliedB - managed.targetB) > EPSILON) {
            managed.data.setColor(managed.targetR, managed.targetG, managed.targetB);
            managed.lastAppliedR = managed.targetR;
            managed.lastAppliedG = managed.targetG;
            managed.lastAppliedB = managed.targetB;
            dirty = true;
        }
        if (managed.lastAppliedOcclusion != nativeOcclusion) {
            managed.data.setOcclusionEnabled(nativeOcclusion);
            managed.lastAppliedOcclusion = nativeOcclusion;
            dirty = true;
        }
        if (Math.abs(managed.lastAppliedX - managed.targetX) > 1.0E-5
                || Math.abs(managed.lastAppliedY - managed.targetY) > 1.0E-5
                || Math.abs(managed.lastAppliedZ - managed.targetZ) > 1.0E-5) {
            managed.data.setPosition(managed.targetX, managed.targetY, managed.targetZ);
            managed.lastAppliedX = managed.targetX;
            managed.lastAppliedY = managed.targetY;
            managed.lastAppliedZ = managed.targetZ;
            dirty = true;
        }

        if (dirty) {
            managed.handle.markDirty();
        }
    }

    private static boolean rendererReady() {
        return VeilRenderSystem.renderer() != null && VeilRenderSystem.renderer().getLightRenderer() != null;
    }

    private static boolean nativeOcclusionEnabled() {
        return OdysseyConfig.occlusionEnabled && VeilNativeOcclusionMode.isNativeEnabled();
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

    private static final class ManagedPointLight {
        private PointLightData data;
        private LightRenderHandle<PointLightData> handle;

        private double targetX;
        private double targetY;
        private double targetZ;
        private float targetR;
        private float targetG;
        private float targetB;
        private float targetBrightness;
        private float targetRadius;
        private int idleTicks;
        private int remainingTicks;

        private float lastAppliedBrightness = Float.NaN;
        private float lastAppliedRadius = Float.NaN;
        private float lastAppliedR = Float.NaN;
        private float lastAppliedG = Float.NaN;
        private float lastAppliedB = Float.NaN;
        private double lastAppliedX = Double.NaN;
        private double lastAppliedY = Double.NaN;
        private double lastAppliedZ = Double.NaN;
        private boolean lastAppliedOcclusion;

        private void ingest(PendingUpdate update) {
            this.targetX = update.x;
            this.targetY = update.y;
            this.targetZ = update.z;
            this.targetR = update.r;
            this.targetG = update.g;
            this.targetB = update.b;
            this.targetBrightness = update.brightness;
            this.targetRadius = update.radius;
            this.idleTicks = RESET_IDLE_TTL_TICKS;
            this.remainingTicks = update.remainingTicks;
        }

        private void close() {
            if (handle != null && handle.isValid()) {
                handle.close();
            }
            handle = null;
            data = null;
        }
    }

    private static final class PendingUpdate {
        private final double x;
        private final double y;
        private final double z;
        private final float r;
        private final float g;
        private final float b;
        private final float brightness;
        private final float radius;
        private final int remainingTicks;

        private PendingUpdate(double x,
                              double y,
                              double z,
                              float r,
                              float g,
                              float b,
                              float brightness,
                              float radius,
                              int remainingTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.brightness = brightness;
            this.radius = radius;
            this.remainingTicks = remainingTicks;
        }

        private PendingUpdate withRemaining(int remainingTicks) {
            return new PendingUpdate(x, y, z, r, g, b, brightness, radius, remainingTicks);
        }
    }
}
