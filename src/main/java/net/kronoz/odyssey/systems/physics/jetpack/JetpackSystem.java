package net.kronoz.odyssey.systems.physics.jetpack;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class JetpackSystem {
    public static final JetpackSystem INSTANCE = new JetpackSystem();

    private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
    private static final String BONE1="booster_1", BONE2="booster_2", BONE3="booster_3";

    public static final class Emitter {
        public final JetpackSmokeField field;
        public Vec3d b1 = null, b2 = null, b3 = null;
        public boolean active = false;
        public Emitter() { field = new JetpackSmokeField(new JetpackSmokeField.Settings()); }
    }

    private final Map<Integer, Emitter> byPlayer = new Object2ObjectOpenHashMap<>();

    public void install(Item jetpackItem) {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null || mc.player == null) return;

            var p = mc.player;
            Emitter em = byPlayer.computeIfAbsent(p.getId(), k -> new Emitter());

            // check worn
            ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
            boolean wearing = chest != null && !chest.isEmpty() && chest.getItem() == jetpackItem;

            // hold jump to thrust
            boolean thrust = wearing && mc.options.jumpKey.isPressed();

            em.active = thrust;

            // upward thrust & slight forward control
            if (thrust) {
                Vec3d vel = p.getVelocity();
                double targetUp = 0.9;
                double add = Math.max(0, targetUp - vel.y);
                p.setVelocity(vel.add(0, Math.min(0.12, add), 0));
                p.fallDistance = 0f;
            }

            // compute bone directions/emission
            Vec3d up = new Vec3d(0,1,0);
            double dt = 1.0/20.0;

            if (em.active) {
                Vec3d viewBack = p.getRotationVector().multiply(-1.0).add(0, -0.5, 0).normalize();
                // if bones not set externally, fallback to offsets behind chest
                Vec3d base = p.getPos().add(0, p.getStandingEyeHeight()*0.5, 0);
                Vec3d right = viewBack.crossProduct(up).normalize();

                Vec3d fb1 = em.b1 != null ? em.b1 : base.add(right.multiply(0.22)).add(viewBack.multiply(0.25));
                Vec3d fb2 = em.b2 != null ? em.b2 : base.add(viewBack.multiply(0.28));
                Vec3d fb3 = em.b3 != null ? em.b3 : base.add(right.multiply(-0.22)).add(viewBack.multiply(0.25));

                // emit fast
                double rate = 1.0/20.0; // time slice
                double want = em.field.cfg.emitPerSecond * rate;
                em.field.burst(want/3.0, fb1, viewBack);
                em.field.burst(want/3.0, fb2, viewBack);
                em.field.burst(want/3.0, fb3, viewBack);

                // strong up-attractor to make plume curve upward
                em.field.update(dt, mc.world, mc.world.getTime(), up);
            } else {
                // still update lingering smoke
                em.field.update(dt, mc.world, mc.world.getTime(), up.multiply(0.5));
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;

            MatrixStack ms = ctx.matrixStack();
            VertexConsumerProvider vcp = ctx.consumers();
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
            var cam = ctx.camera().getPos();
            float tickDelta = ctx.camera().getLastTickDelta();

            ms.push();
            ms.translate(-cam.x, -cam.y, -cam.z);

            for (Emitter em : byPlayer.values()) {
                em.field.render(ms, vc, Vec3d.ZERO, tickDelta, mc.world);
            }

            ms.pop();
        });
    }

    // Call this from your model renderer/anim callback each frame to provide bone world positions.
    public void setBoneWorldPos(int playerId, String boneName, Vec3d worldPos) {
        Emitter em = byPlayer.get(playerId);
        if (em == null) return;
        if (BONE1.equals(boneName)) em.b1 = worldPos;
        else if (BONE2.equals(boneName)) em.b2 = worldPos;
        else if (BONE3.equals(boneName)) em.b3 = worldPos;
    }
}
