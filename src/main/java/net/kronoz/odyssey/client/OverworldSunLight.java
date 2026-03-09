package net.kronoz.odyssey.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Adds a directional Veil light that tracks the overworld celestial cycle.
 * This gives consistent sun/moon shadow direction without affecting non-overworld dimensions.
 */
public final class OverworldSunLight {
    private static LightRenderHandle<DirectionalLightData> handle;
    private static DirectionalLightData lightData;
    private static long lastTick = Long.MIN_VALUE;
    private static Vec3d smoothedDirection = new Vec3d(0.0, -1.0, 0.0);

    private OverworldSunLight() {
    }

    public static void initClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(OverworldSunLight::tick);
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            reset();
            return;
        }
        if (!OdysseyConfig.directionalSunLightEnabled) {
            clear();
            return;
        }
        if (world.getRegistryKey() != World.OVERWORLD) {
            clear();
            return;
        }

        int stride = effectiveStride();
        if (stride > 1 && (world.getTime() % stride) != 0) {
            return;
        }
        if (world.getTime() == lastTick) {
            return;
        }
        lastTick = world.getTime();

        var renderer = VeilRenderSystem.renderer();
        if (renderer == null || renderer.getLightRenderer() == null) {
            clear();
            return;
        }

        if (handle == null || !handle.isValid() || lightData == null) {
            lightData = new DirectionalLightData();
            handle = renderer.getLightRenderer().addLight(lightData);
            smoothedDirection = new Vec3d(0.0, -1.0, 0.0);
        }

        float tickDelta = client.getRenderTickCounter().getTickDelta(false);
        CelestialSample sample = sampleDirection(world, tickDelta);
        float quality = OdysseyConfig.effectiveDirectionalShadowQuality();
        float smoothFactor = switch ((int) quality) {
            case 0 -> 0.18f;
            case 1 -> 0.28f;
            case 3 -> 0.56f;
            default -> 0.40f;
        };
        smoothedDirection = lerp(smoothedDirection, sample.direction, smoothFactor).normalize();

        float weatherAttenuation = 1.0f - (world.getRainGradient(tickDelta) * 0.45f + world.getThunderGradient(tickDelta) * 0.30f);
        float ambientFactor = 1.0f - (world.getAmbientDarkness() / 11.0f);
        float dayFactor = clamp01(Math.max(sample.dayFactor, ambientFactor));
        float brightness = MathHelper.lerp(dayFactor,
                OdysseyConfig.effectiveDirectionalNightBrightness(),
                OdysseyConfig.effectiveDirectionalDayBrightness()) * weatherAttenuation;

        Vec3d color = sampleColor(sample, dayFactor);
        lightData
                .setDirection((float) smoothedDirection.x, (float) smoothedDirection.y, (float) smoothedDirection.z)
                .setColor((float) color.x, (float) color.y, (float) color.z)
                .setBrightness(Math.max(0.0f, brightness));
        handle.markDirty();
    }

    private static CelestialSample sampleDirection(ClientWorld world, float tickDelta) {
        float skyAngle = world.getSkyAngle(tickDelta);
        double cycle = skyAngle * Math.PI * 2.0;

        // "sunVector" points from the world toward the sun.
        double elevation = Math.sin(cycle);
        double azimuth = cycle + (Math.PI * 0.5);
        Vec3d sunVector = new Vec3d(Math.cos(azimuth), elevation, Math.sin(azimuth)).normalize();
        Vec3d dayLightDirection = sunVector.multiply(-1.0);
        Vec3d moonLightDirection = sunVector;

        float dayFactor = clamp01((float) ((elevation + 0.12) / 1.12));
        Vec3d direction = dayFactor > 0.02f ? dayLightDirection : moonLightDirection;
        return new CelestialSample(direction, dayFactor, elevation);
    }

    private static Vec3d sampleColor(CelestialSample sample, float dayFactor) {
        double sunsetBlend = clamp01((float) (1.0 - Math.abs(sample.elevation) * 2.1)) * dayFactor;
        Vec3d dayBase = new Vec3d(1.00, 0.95, 0.88);
        Vec3d sunset = new Vec3d(1.00, 0.72, 0.46);
        Vec3d night = new Vec3d(0.52, 0.60, 0.78);
        Vec3d dayColor = lerp(dayBase, sunset, (float) sunsetBlend);
        return lerp(night, dayColor, dayFactor);
    }

    private static int effectiveStride() {
        int base = OdysseyConfig.effectiveDirectionalUpdateStride();
        return switch (OdysseyConfig.effectiveDirectionalShadowQuality()) {
            case 0 -> Math.max(base, 8);
            case 1 -> Math.max(base, 4);
            case 3 -> Math.max(1, base - 1);
            default -> base;
        };
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, float t) {
        return new Vec3d(
                MathHelper.lerp(t, (float) a.x, (float) b.x),
                MathHelper.lerp(t, (float) a.y, (float) b.y),
                MathHelper.lerp(t, (float) a.z, (float) b.z)
        );
    }

    private static float clamp01(float value) {
        return MathHelper.clamp(value, 0.0f, 1.0f);
    }

    private static void clear() {
        if (handle != null && handle.isValid()) {
            handle.close();
        }
        handle = null;
        lightData = null;
    }

    private static void reset() {
        clear();
        lastTick = Long.MIN_VALUE;
        smoothedDirection = new Vec3d(0.0, -1.0, 0.0);
    }

    private record CelestialSample(Vec3d direction, float dayFactor, double elevation) {
    }
}
