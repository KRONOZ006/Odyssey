package net.kronoz.odyssey.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.hud.death.DeathUICutscene;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class ExpandingVeilLight {
    private ExpandingVeilLight() {}

    private static final float START_RADIUS = 0.0f;
    private static final float END_RADIUS   = 16.0f;   // target radius after 20s
    private static final float BRIGHTNESS   = 4.0f;    // keep it bright while expanding
    private static final float DURATION_S   = 20.0f;   // seconds
    private static final float CLR_R = 1f, CLR_G = 0f, CLR_B = 0f;

    private static final Map<BlockPos, State> ACTIVE = new HashMap<>();
    private static boolean hooked = false;

    private static final class State {
        final BlockPos pos;
        final long startNanos;
        PointLightData data;
        LightRenderHandle<PointLightData> handle;
        boolean done;
        State(BlockPos pos) {
            this.pos = pos;
            this.startNanos = System.nanoTime();
        }
    }

    public static void trigger(BlockPos pos) {
        ensureHooks();
        if (ACTIVE.containsKey(pos)) return;
        if (!rendererReady()) return;

        State s = new State(pos);
        Vec3d c = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);

        PointLightData pl = new PointLightData()
                .setBrightness(BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setRadius(START_RADIUS);
        pl.setPosition((float) c.x, (float) c.y, (float) c.z);

        LightRenderHandle<PointLightData> h =
                VeilRenderSystem.renderer().getLightRenderer().addLight(pl);

        s.data = pl;
        s.handle = h;
        ACTIVE.put(pos, s);
    }

    private static void ensureHooks() {
        if (hooked) return;
        hooked = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (State s : ACTIVE.values()) {
                if (s.handle != null && s.handle.isValid()) s.handle.close();
            }
            ACTIVE.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.isPaused()) return;
            if (!rendererReady()) return;

            double dt;
            for (State s : ACTIVE.values().toArray(new State[0])) {
                if (s.done || s.data == null || s.handle == null || !s.handle.isValid()) {
                    if (s.handle != null && s.handle.isValid()) s.handle.close();
                    ACTIVE.remove(s.pos);
                    continue;
                }
                long nanos = System.nanoTime() - s.startNanos;
                dt = nanos / 1_000_000_000.0;
                float t = (float)Math.min(1.0, dt / DURATION_S);

                float radius = lerp(START_RADIUS, END_RADIUS, t);
                s.data.setRadius(radius);
                s.handle.markDirty();

                if (t >= 1.0f) {
                    s.done = true;
                    try {
                        DeathUICutscene.start();
                    } catch (Throwable ignored) {}
                    if (s.handle.isValid()) s.handle.close();
                    ACTIVE.remove(s.pos);
                }
            }
        });
    }

    private static boolean rendererReady() {
        return VeilRenderSystem.renderer() != null
                && VeilRenderSystem.renderer().getLightRenderer() != null;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
