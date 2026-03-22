package net.kronoz.odyssey.block.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.light.VeilNativeOcclusionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class DecalLightClient {
    private static final Map<Integer, LightRenderHandle<PointLightData>> HANDLES = new HashMap<>();
    private static final Map<Integer, PointLightData> DATA = new HashMap<>();
    private static Identifier lastWorldId = null;

    public static void initClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearAll());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> pruneNow()); // safety on region unloads

        WorldRenderEvents.START.register(ctx -> {
            if (!RenderSystem.isOnRenderThread()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld w = mc.world;

            if (w == null) { clearAll(); return; }

            Identifier wid = w.getRegistryKey().getValue();
            if (lastWorldId == null || !lastWorldId.equals(wid)) {
                clearAll();
                lastWorldId = wid;
            }

            if (mc.player == null || !mc.player.isAlive()) {
                clearAll();
                return;
            }

            pruneNow();
        });
    }

    public static void update(int id, double x, double y, double z,
                              float r, float g, float b,
                              float brightness, float radius) {
        if (!RenderSystem.isOnRenderThread()) return;
        brightness = sanitize(brightness, 0.0f, OdysseyConfig.effectiveMaxLightBrightness());
        radius = sanitize(radius, 0.0f, OdysseyConfig.effectiveMaxPointRadius());
        if (brightness <= 0f || radius <= 0f) { remove(id); return; }

        var renderer = (VeilRenderSystem.renderer() != null) ? VeilRenderSystem.renderer().getLightRenderer() : null;
        if (renderer == null) return;

        PointLightData pl = DATA.get(id);
        LightRenderHandle<PointLightData> handle = HANDLES.get(id);
        boolean nativeOcclusion = OdysseyConfig.occlusionEnabled && VeilNativeOcclusionMode.isNativeEnabled();
        float cr = sanitize(r, 0.0f, 1.0f);
        float cg = sanitize(g, 0.0f, 1.0f);
        float cb = sanitize(b, 0.0f, 1.0f);

        if (pl == null || handle == null || !handle.isValid()) {
            pl = new PointLightData()
                    .setBrightness(brightness)
                    .setColor(cr, cg, cb)
                    .setRadius(radius)
                    .setOcclusionEnabled(nativeOcclusion);
            pl.setPosition((float) x, (float) y, (float) z);
            handle = renderer.addLight(pl);
            DATA.put(id, pl);
            HANDLES.put(id, handle);
            return;
        }

        pl.setBrightness(brightness);
        pl.setColor(cr, cg, cb);
        pl.setRadius(radius);
        if (pl.isOcclusionEnabled() != nativeOcclusion) {
            pl.setOcclusionEnabled(nativeOcclusion);
        }
        pl.setPosition((float) x, (float) y, (float) z);
        handle.markDirty();
    }

    public static void remove(int id) {
        LightRenderHandle<PointLightData> h = HANDLES.remove(id);
        if (h != null && h.isValid()) h.close();
        DATA.remove(id);
    }

    private static void clearAll() {
        for (LightRenderHandle<PointLightData> h : HANDLES.values()) {
            if (h != null && h.isValid()) h.close();
        }
        HANDLES.clear();
        DATA.clear();
    }

    private static void pruneNow() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        if (w == null) { clearAll(); return; }

        Iterator<Map.Entry<Integer, LightRenderHandle<PointLightData>>> it = HANDLES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, LightRenderHandle<PointLightData>> e = it.next();
            int id = e.getKey();
            LightRenderHandle<PointLightData> h = e.getValue();

            Entity ent = w.getEntityById(id);
            boolean dead = (ent == null) || ent.isRemoved();

            if (dead) {
                if (h != null && h.isValid()) h.close();
                it.remove();
                DATA.remove(id);
                continue;
            }

            if (h == null || !h.isValid()) {
                PointLightData data = DATA.get(id);
                var renderer = (VeilRenderSystem.renderer() != null) ? VeilRenderSystem.renderer().getLightRenderer() : null;
                if (data != null && renderer != null) {
                    e.setValue(renderer.addLight(data));
                }
            }
        }
    }

    private DecalLightClient() {}

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
}

