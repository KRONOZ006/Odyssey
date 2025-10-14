package net.kronoz.odyssey.entity;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class VeilLightCompat {
    private static final boolean DEBUG = true;

    private static final Map<Integer, LightRenderHandle<PointLightData>> HANDLES = new HashMap<>();
    private static final Map<Integer, PointLightData> DATA = new HashMap<>();
    private static final Map<Integer, Integer> IDLE_TTL = new HashMap<>();
    private static final Map<Integer, Integer> ABS_REMAINING = new HashMap<>();

    private static final int RESET_TTL_TICKS = 60;

    public static void initClient() {
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> clearAll("join"));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> clearAll("disconnect"));
        ClientTickEvents.END_CLIENT_TICK.register(VeilLightCompat::tick);
        log("VeilLightCompat ready");
    }

    public static void updateWithLifetime(int id, double x, double y, double z,
                                          float r, float g, float b,
                                          float brightness, float radius,
                                          int remainingTicks) {
        var renderer = VeilRenderSystem.renderer();
        if (renderer == null || renderer.getLightRenderer() == null) {
            return;
        }

        if (brightness <= 0f || radius <= 0f || remainingTicks <= 0) {
            return;
        }

        PointLightData pl = DATA.get(id);
        LightRenderHandle<PointLightData> handle = HANDLES.get(id);

        if (pl == null || handle == null || !handle.isValid()) {
            pl = new PointLightData().setBrightness(brightness).setColor(r, g, b).setRadius(radius);
            pl.setPosition((float) x, (float) y, (float) z);
            handle = renderer.getLightRenderer().addLight(pl);
            DATA.put(id, pl);
            HANDLES.put(id, handle);
        } else {
            pl.setBrightness(brightness);
            pl.setColor(r, g, b);
            pl.setRadius(radius);
            pl.setPosition((float) x, (float) y, (float) z);
            handle.markDirty();
        }

        IDLE_TTL.put(id, RESET_TTL_TICKS);
        ABS_REMAINING.put(id, remainingTicks);
    }

    public static void update(int id, double x, double y, double z,
                              float r, float g, float b,
                              float brightness, float radius) {
        updateWithLifetime(id, x, y, z, r, g, b, brightness, radius, RESET_TTL_TICKS);
    }

    public static void remove(int id, String reason) {
        LightRenderHandle<PointLightData> h = HANDLES.remove(id);
        if (h != null && h.isValid()) {
            h.close();
        } else {
        }
        DATA.remove(id);
        IDLE_TTL.remove(id);
        ABS_REMAINING.remove(id);
    }

    private static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null) {
            clearAll("no-world");
            return;
        }

        Iterator<Map.Entry<Integer, LightRenderHandle<PointLightData>>> it = HANDLES.entrySet().iterator();
        int alive = 0, closed = 0;

        while (it.hasNext()) {
            var entry = it.next();
            int id = entry.getKey();
            LightRenderHandle<PointLightData> handle = entry.getValue();

            int idle = IDLE_TTL.getOrDefault(id, 0) - 1;
            int abs = ABS_REMAINING.getOrDefault(id, 0) - 1;

            IDLE_TTL.put(id, idle);
            ABS_REMAINING.put(id, abs);

            boolean missingEntity = world.getEntityById(id) == null;
            boolean idleExpired = idle <= 0;
            boolean absExpired = abs <= 0;
            boolean invalidHandle = handle == null || !handle.isValid();

            if (missingEntity || idleExpired || absExpired || invalidHandle) {
                if (handle != null && handle.isValid()) handle.close();
                it.remove();
                DATA.remove(id);
                IDLE_TTL.remove(id);
                ABS_REMAINING.remove(id);
                closed++;
            } else {
                alive++;
            }
        }

        if (DEBUG && (alive > 0 || closed > 0) && mc.world.getTime() % 20L == 0L) {
        }
    }

    private static void clearAll(String why) {
        for (var h : HANDLES.values()) {
            if (h != null && h.isValid()) h.close();
        }
        int n = HANDLES.size();
        HANDLES.clear();
        DATA.clear();
        IDLE_TTL.clear();
        ABS_REMAINING.clear();
    }

    private static void log(String s) {
    }

    private VeilLightCompat() {}
}
