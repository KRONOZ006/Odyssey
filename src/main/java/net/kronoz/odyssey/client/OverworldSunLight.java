package net.kronoz.odyssey.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.light.VeilNativeOcclusionMode;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/**
 * Adds a directional Veil light that tracks the overworld celestial cycle.
 * This gives consistent sun/moon shadow direction without affecting non-overworld dimensions.
 */
public final class OverworldSunLight {
    private static final float DIRECTIONAL_FILL_FACTOR = 0.34f;
    private static final float SHADOW_PROXY_FACTOR = 1.24f;
    private static final float SHADOW_PROXY_MIN_DISTANCE = 72.0f;
    private static final float SHADOW_PROXY_MAX_DISTANCE = 188.0f;
    private static final float SHADOW_PROXY_SOURCE_SCALE = 0.62f;
    private static final float SHADOW_PROXY_SIZE = 1.55f;
    private static final float SHADOW_PROXY_ANGLE = (float) Math.toRadians(86.0);
    private static final Vec3d[] SKY_PROBE_OFFSETS = new Vec3d[] {
            Vec3d.ZERO,
            new Vec3d(0.32, 0.0, 0.0),
            new Vec3d(-0.32, 0.0, 0.0),
            new Vec3d(0.0, 0.0, 0.32),
            new Vec3d(0.0, 0.25, 0.0)
    };

    private static LightRenderHandle<DirectionalLightData> directionalHandle;
    private static DirectionalLightData directionalData;
    private static LightRenderHandle<AreaLightData> shadowProxyHandle;
    private static AreaLightData shadowProxyData;
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

        if (directionalHandle == null || !directionalHandle.isValid() || directionalData == null) {
            directionalData = new DirectionalLightData();
            directionalHandle = renderer.getLightRenderer().addLight(directionalData);
            smoothedDirection = new Vec3d(0.0, -1.0, 0.0);
        }
        if (shadowProxyHandle == null || !shadowProxyHandle.isValid() || shadowProxyData == null) {
            shadowProxyData = new AreaLightData();
            shadowProxyHandle = renderer.getLightRenderer().addLight(shadowProxyData);
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
        Vec3d camera = cameraPos(client);
        float shadowDistance = MathHelper.clamp(
                OdysseyConfig.effectiveMaxAreaDistance() * 1.1f,
                SHADOW_PROXY_MIN_DISTANCE,
                SHADOW_PROXY_MAX_DISTANCE
        );
        float skyVisibility = sampleSkyVisibility(world, camera, smoothedDirection, shadowDistance);

        // Directional fill keeps broad time-of-day response while the proxy area light carries block shadows.
        directionalData
                .setDirection((float) smoothedDirection.x, (float) smoothedDirection.y, (float) smoothedDirection.z)
                .setColor((float) color.x, (float) color.y, (float) color.z)
                .setBrightness(Math.max(0.0f, brightness * DIRECTIONAL_FILL_FACTOR * (0.25f + 0.75f * skyVisibility)));
        directionalHandle.markDirty();

        Vec3d towardLight = smoothedDirection.multiply(-1.0).normalize();
        Vec3d sourcePos = camera.add(towardLight.multiply(shadowDistance * SHADOW_PROXY_SOURCE_SCALE));
        sourcePos = nudgeOutOfSolid(world, sourcePos, towardLight);

        Quaternionf orientation = new Quaternionf().rotationTo(
                0.0f, 0.0f, 1.0f,
                (float) smoothedDirection.x, (float) smoothedDirection.y, (float) smoothedDirection.z
        );
        shadowProxyData
                .setBrightness(Math.max(0.0f, brightness * SHADOW_PROXY_FACTOR * skyVisibility))
                .setColor((float) color.x, (float) color.y, (float) color.z)
                .setDistance(shadowDistance)
                .setSize(SHADOW_PROXY_SIZE, SHADOW_PROXY_SIZE)
                .setAngle(SHADOW_PROXY_ANGLE)
                .setOcclusionEnabled(OdysseyConfig.occlusionEnabled && VeilNativeOcclusionMode.isNativeEnabled());
        shadowProxyData.getPosition().set(sourcePos.x, sourcePos.y, sourcePos.z);
        shadowProxyData.getOrientation().set(orientation);
        shadowProxyHandle.markDirty();
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
        if (directionalHandle != null && directionalHandle.isValid()) {
            directionalHandle.close();
        }
        if (shadowProxyHandle != null && shadowProxyHandle.isValid()) {
            shadowProxyHandle.close();
        }
        directionalHandle = null;
        directionalData = null;
        shadowProxyHandle = null;
        shadowProxyData = null;
    }

    private static void reset() {
        clear();
        lastTick = Long.MIN_VALUE;
        smoothedDirection = new Vec3d(0.0, -1.0, 0.0);
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

    private static float sampleSkyVisibility(ClientWorld world, Vec3d camera, Vec3d lightDirection, float maxDistance) {
        Vec3d towardLight = lightDirection.multiply(-1.0).normalize();
        float sum = 0.0f;
        int hits = 0;
        for (Vec3d offset : SKY_PROBE_OFFSETS) {
            Vec3d origin = camera.add(offset);
            float clear = LightOcclusionHelper.directionalDistance(world, origin, towardLight, maxDistance, null);
            sum += clamp01(clear / maxDistance);
            hits++;
        }
        return hits <= 0 ? 1.0f : clamp01(sum / hits);
    }

    private static Vec3d nudgeOutOfSolid(ClientWorld world, Vec3d sourcePos, Vec3d towardLight) {
        Vec3d step = towardLight.multiply(1.35);
        Vec3d current = sourcePos;
        for (int i = 0; i < 8; i++) {
            BlockPos pos = BlockPos.ofFloored(current);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(world, pos).isEmpty()) {
                break;
            }
            current = current.add(step);
        }
        return current;
    }

    private record CelestialSample(Vec3d direction, float dayFactor, double elevation) {
    }
}
