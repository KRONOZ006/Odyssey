package net.kronoz.odyssey.hud.death;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class DeathUICutscene implements HudRenderCallback {
    private static boolean active = false;
    private static long startMs;
    private static boolean hudWasHidden = false;
    private static boolean savedHudHidden = false;

    private static final int FRAME_COUNT = 21;
    private static final Identifier[] FRAMES = new Identifier[FRAME_COUNT];

    private static final float FADE_WHITE_IN = 2.0f;
    private static final float A_LOOP_DURATION = 3.0f;
    private static final float A_LOOP_PERIOD = 0.08f;
    private static final float TO_BLACK = 1.2f;
    private static final float TO_WHITE = 1.2f;
    private static final float FRAME_DURATION = 0.10f;

    private static float time() { return (Util.getMeasuringTimeMs() - startMs) / 1000.0f; }

    private static float tW1() { return FADE_WHITE_IN; }
    private static float tA0() { return tW1(); }
    private static float tA1() { return tA0() + A_LOOP_DURATION; }
    private static float tBLK1() { return tA1() + TO_BLACK; }
    private static float tW2() { return tBLK1() + TO_WHITE; }
    private static float tSEQ() { return tW2() + 18 * FRAME_DURATION; }

    static {
        for (int i = 0; i < FRAME_COUNT; i++) {
            FRAMES[i] = Identifier.of("odyssey", "textures/gui/death/ui_of_death" + i + ".png");
        }
    }

    private DeathUICutscene() {}

    public static void register() {
        HudRenderCallback.EVENT.register(new DeathUICutscene());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active) return;
            if (client.options != null) client.options.hudHidden = true;
            if (client.currentScreen != null) client.setScreen(null);
        });
    }

    public static void start() {
        active = true;
        startMs = Util.getMeasuringTimeMs();
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            if (!savedHudHidden) {
                hudWasHidden = mc.options.hudHidden;
                savedHudHidden = true;
            }
            mc.options.hudHidden = true;
            if (mc.currentScreen != null) mc.setScreen(null);
        }
    }

    private static void finish() {
        active = false;
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null && savedHudHidden) mc.options.hudHidden = hudWasHidden;
        savedHudHidden = false;
        if (mc != null && mc.currentScreen != null) mc.setScreen(null);
    }

    public static boolean isActive() { return active; }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter renderTickCounter) {
        if (!active) return;

        final float t = time();
        final int sw = ctx.getScaledWindowWidth();
        final int sh = ctx.getScaledWindowHeight();

        if (t <= tW1()) {
            float a = ease(t / FADE_WHITE_IN);
            ctx.fill(0, 0, sw, sh, ((int)(a * 255f) << 24) | 0xFFFFFF);
        } else if (t <= tA1()) {
            ctx.fill(0, 0, sw, sh, 0xFFFFFFFF);
            float loopT = t - tA0();
            int toggles = (int)Math.floor(loopT / A_LOOP_PERIOD);
            int frame = (toggles % 2 == 0) ? 18 : 19;
            drawCentered(ctx, FRAMES[frame], sw, sh, 0.2f);
        } else if (t <= tBLK1()) {
            ctx.fill(0, 0, sw, sh, 0xFFFFFFFF);
            float a = ease((t - tA1()) / TO_BLACK);
            ctx.fill(0, 0, sw, sh, ((int)(a * 255f) << 24));
        } else if (t <= tW2()) {
            ctx.fill(0, 0, sw, sh, 0xFF000000);
            float a = ease((t - tBLK1()) / TO_WHITE);
            ctx.fill(0, 0, sw, sh, ((int)(a * 255f) << 24) | 0xFFFFFF);
        } else if (t <= tSEQ()) {
            ctx.fill(0, 0, sw, sh, 0xFFFFFFFF);
            float local = t - tW2();
            int idx = MathHelper.clamp((int)Math.floor(local / FRAME_DURATION), 0, 17);
            drawCentered(ctx, FRAMES[idx], sw, sh, 0.2f);
        } else {
            finish();
        }
    }

    private static void drawCentered(DrawContext ctx, Identifier tex, int sw, int sh, float baseScale) {
        final int SRC_W = 256, SRC_H = 80;
        final float aspect = (float) SRC_W / SRC_H;
        float base = baseScale * Math.min(sw, sh);
        float desiredH = base;
        float desiredW = base * aspect;
        if (desiredW > sw * 0.75f) {
            desiredW = sw * 0.75f;
            desiredH = desiredW / aspect;
        }
        if (desiredH > sh * 0.75f) {
            desiredH = sh * 0.75f;
            desiredW = desiredH * aspect;
        }
        int scaleByH = Math.max(1, (int)Math.floor(desiredH / SRC_H));
        int scaleByW = Math.max(1, (int)Math.floor(desiredW / SRC_W));
        int scale = Math.max(1, Math.min(scaleByH, scaleByW));
        int fw = SRC_W * scale;
        int fh = SRC_H * scale;
        int x = (sw - fw) / 2;
        int y = (sh - fh) / 2;
        ctx.drawTexture(tex, x, y, 0f, 0f, fw, fh, fw, fh);
    }

    private static float ease(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
