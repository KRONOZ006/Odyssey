package net.kronoz.odyssey.systems.grapple;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.kronoz.odyssey.item.client.renderer.GrappleHookRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class GrappleClient implements ClientModInitializer {
    private static final Identifier ROPE_TEX = Identifier.of("odyssey","textures/misc/rope.png");

    @Override
    public void onInitializeClient() {
        GrappleHookRenderer.register();

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;

            // Find our hook entity owned by player (simple: search nearby grapple hooks)
            GrappleHookEntityView hook = GrappleHookEntityView.find(mc);
            if (hook == null || !hook.latched()) return;

            MatrixStack ms = ctx.matrixStack();
            var buffers = mc.getBufferBuilders().getEntityVertexConsumers();

            Vec3d a = mc.player.getCameraPosVec(mc.getRenderTickCounter().getTickDelta(true));
            Vec3d b = hook.anchor();

            drawTexturedRope(ms, buffers, a, b, ctx.camera().getPos());
            buffers.draw();
        });
    }

    // minimal “view” finder to avoid hard dep
    private record GrappleHookEntityView(Vec3d anchor, boolean latched) {
        static GrappleHookEntityView find(MinecraftClient mc) {
            var player = mc.player;
            if (player == null) return null;
            var world = mc.world;
            if (world == null) return null;
            // Scan small radius for our hook (cheap and fine)
            var box = player.getBoundingBox().expand(64);
            for (var e : world.getOtherEntities(player, box)) {
                if (e.getType().toString().contains("grapple_hook")) {
                    try {
                        var fLatched = e.getClass().getField("latched");
                        var fAnchor  = e.getClass().getField("anchor");
                        boolean latched = fLatched.getBoolean(e);
                        Vec3d anchor = (Vec3d) fAnchor.get(e);
                        if (latched) return new GrappleHookEntityView(anchor, true);
                    } catch (Throwable ignored) {}
                }
            }
            return null;
        }
    }

    private static void drawTexturedRope(MatrixStack ms, VertexConsumerProvider.Immediate buffers, Vec3d a, Vec3d b, Vec3d cam) {
        Vec3d dir = b.subtract(a);
        double len = dir.length();
        if (len < 0.01) return;
        dir = dir.normalize();

        Vec3d up = Math.abs(dir.dotProduct(new Vec3d(0,1,0))) > 0.98 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
        Vec3d side = dir.crossProduct(up).normalize().multiply(0.04);

        Vec3d v0 = a.add(side), v1 = a.subtract(side);
        Vec3d v2 = b.add(side), v3 = b.subtract(side);

        RenderSystem.setShaderTexture(0, ROPE_TEX);
        RenderSystem.disableDepthTest();

        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float u0 = 0f, u1 = (float)(len * 2.0);
        float v0uv = 0f, v1uv = 1f;

        buf.vertex(ms.peek().getPositionMatrix(), (float)(v0.x - cam.x), (float)(v0.y - cam.y), (float)(v0.z - cam.z)).texture(u0, v0uv).color(255,255,255,255);
        buf.vertex(ms.peek().getPositionMatrix(), (float)(v1.x - cam.x), (float)(v1.y - cam.y), (float)(v1.z - cam.z)).texture(u0, v1uv).color(255,255,255,255);
        buf.vertex(ms.peek().getPositionMatrix(), (float)(v3.x - cam.x), (float)(v3.y - cam.y), (float)(v3.z - cam.z)).texture(u1, v1uv).color(255,255,255,255);
        buf.vertex(ms.peek().getPositionMatrix(), (float)(v2.x - cam.x), (float)(v2.y - cam.y), (float)(v2.z - cam.z)).texture(u1, v0uv).color(255,255,255,255);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
    }
}
