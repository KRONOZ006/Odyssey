package net.kronoz.odyssey.systems.physics.jetpack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class JetpackExhaustManager {

    private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");

    // sim cadence
    private static final double MAX_FPS = 120.0;
    private static final long   MIN_STEP_NS = (long)(1_000_000_000L / MAX_FPS);

    // when to delete an emitter if nobody pings it and no particles remain
    private static final long EMITTER_IDLE_NS = 1_000_000_000L; // ~1s

    private static final class Emitter {
        final OdysseySmokeField field = new OdysseySmokeField(240, 186, 186, 186);
        Vec3d origin = Vec3d.ZERO;

        long lastTickNs = 0L;
        long lastPingNs = 0L;
        boolean pingedThisFrame = false;

        Emitter() {
            field.setEmitting(true);
        }

        void ping(Vec3d o) {
            origin = o;
            pingedThisFrame = true;
            lastPingNs = System.nanoTime();
            field.setEmitting(true);
        }
    }

    private static final Map<String, Emitter> EMITTERS = new HashMap<>();

    /** Called by the renderer once per frame for EACH booster bone it draws. */
    public static void emit(String stableId, Vec3d originWS) {
        Emitter e = EMITTERS.computeIfAbsent(stableId, k -> new Emitter());
        e.ping(originWS);
    }

    /** Step all emitters (hook from END_CLIENT_TICK). */
    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.world == null) return;

        long now = System.nanoTime();

        Iterator<Map.Entry<String, Emitter>> it = EMITTERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Emitter> entry = it.next();
            Emitter e = entry.getValue();

            if (e.lastTickNs == 0L) e.lastTickNs = now;

            long elapsed = now - e.lastTickNs;
            if (elapsed >= MIN_STEP_NS) {
                double dt = Math.min(elapsed / 1_000_000_000.0, 1.0 / 20.0);

                if (!e.pingedThisFrame) e.field.setEmitting(false); // no ping → stop spawning, keep sim
                e.field.update(dt, e.origin, mc.world, mc.world.getTime());

                e.lastTickNs = now;
                e.pingedThisFrame = false;
            }

            // prune if idle and empty
            if ((now - e.lastPingNs) > EMITTER_IDLE_NS && e.field.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Draw all fields (hook from WorldRenderEvents.AFTER_ENTITIES). */
    public static void renderAll(MatrixStack ms, VertexConsumerProvider vcp, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(WHITE));

        for (Emitter e : EMITTERS.values()) {
            ms.push();
            e.field.render(ms, vc, cam, tickDelta);
            ms.pop();
        }
    }
}
