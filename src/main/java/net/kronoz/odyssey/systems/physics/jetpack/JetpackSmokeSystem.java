package net.kronoz.odyssey.systems.physics.jetpack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JetpackSmokeSystem {

    private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
    private static final RenderLayer LAYER = RenderLayer.getEntityTranslucent(WHITE);

    private static final Map<String, Emitter> EMITTERS = new LinkedHashMap<>();

    private static final double MAX_FPS = 120.0;
    private static final long   MIN_STEP_NS = (long)(1_000_000_000L / MAX_FPS);

    private static final double IDLE_TIMEOUT_S = 2.0; // stop emitting after this if not refreshed; field still simulates to death
    private static final double DIE_AFTER_S    = 8.0; // hard cap to cull abandoned emitters once empty

    private JetpackSmokeSystem() {}

    public static void emit(String emitterId, Vec3d originWS, Vec3d axisWS) {
        long now = System.nanoTime();
        Emitter e = EMITTERS.get(emitterId);
        if (e == null) {
            e = new Emitter();
            // match your current tuned defaults in OdysseySmokeField ctor:
            e.field = new OdysseySmokeField(500); // capacity—adjust if you like
            EMITTERS.put(emitterId, e);
        }
        e.lastEmitWorldPos = originWS;
        e.axisWS = axisWS;
        e.lastSeenNs = now;
        e.emitting = true;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        long now = System.nanoTime();
        double worldTime = mc.world.getTime();

        Iterator<Map.Entry<String, Emitter>> it = EMITTERS.entrySet().iterator();
        while (it.hasNext()) {
            Emitter em = it.next().getValue();

            if (em.lastStepNs == 0L) {
                em.lastStepNs = now;
                continue;
            }
            long elapsed = now - em.lastStepNs;
            if (elapsed < MIN_STEP_NS) continue; // fps cap

            double dt = Math.min(elapsed / 1_000_000_000.0, 1.0/20.0);
            em.lastStepNs = now;

            boolean recentlySeen = (now - em.lastSeenNs) < (long)(IDLE_TIMEOUT_S * 1_000_000_000L);
            em.emitting = recentlySeen;

            if (em.emitting && em.lastEmitWorldPos != null && em.axisWS != null) {
                em.field.setEmitting(true);
                em.field.setAxis(em.axisWS); // lets the plume know the thrust direction
                em.field.update(dt, em.lastEmitWorldPos, mc.world, (long)worldTime);
            } else {
                em.field.setEmitting(false);
                // continue sim with last origin so particles finish naturally
                if (em.lastEmitWorldPos != null) {
                    em.field.update(dt, em.lastEmitWorldPos, mc.world, (long)worldTime);
                }
            }

            boolean empty = em.field.isEmpty();
            boolean tooOld = (now - em.lastSeenNs) > (long)(DIE_AFTER_S * 1_000_000_000L);
            if (empty && tooOld) {
                it.remove();
            }
        }
    }

    public static void renderAll(MatrixStack ms, VertexConsumerProvider vcp, float tickDelta) {
        if (EMITTERS.isEmpty()) return;
        VertexConsumer vc = vcp.getBuffer(LAYER);
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        for (Emitter e : EMITTERS.values()) {
            // render in camera-relative coords so nothing follows the camera
            e.field.render(ms, vc, cam, tickDelta);
        }
    }

    private static final class Emitter {
        OdysseySmokeField field;
        Vec3d lastEmitWorldPos;
        Vec3d axisWS;
        long lastStepNs;
        long lastSeenNs;
        boolean emitting;
    }
}
