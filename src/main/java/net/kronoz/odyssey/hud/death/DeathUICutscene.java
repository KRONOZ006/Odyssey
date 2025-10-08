package net.kronoz.odyssey.hud.death;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class DeathUICutscene implements HudRenderCallback {
    private static boolean active = false;
    private static long startMs;

    private static final int FRAME_COUNT = 20;
    private static final Identifier[] FRAMES = new Identifier[FRAME_COUNT];

    private static final float FADE_BLACK_IN   = 2.0f;

    private static final float SHAKE_TIME      = 1.0f;
    private static final float FRAME_DURATION  = 0.10f;

    private static final int   WHITE_START_IDX = 11;
    private static final float WHITE_FADE_TIME = (17 - 11) * FRAME_DURATION;

    private static final float LOOP_DELAY      = 1.0f;
    private static final float LOOP_DURATION   = 5.0f;
    private static final float LOOP_PERIOD     = 0.10f;

    private static final float WHITE_FADE_OUT  = 8.0f;

    private static float whiteStartTime() { return FADE_BLACK_IN + (WHITE_START_IDX * FRAME_DURATION); }
    private static float whiteFullTime()  { return whiteStartTime() + WHITE_FADE_TIME; }
    private static float loopStartTime()  { return whiteFullTime() + LOOP_DELAY; }
    private static float loopEndTime()    { return loopStartTime() + LOOP_DURATION; }
    private static float totalTime()      { return loopEndTime() + WHITE_FADE_OUT; }

    static {
        for (int i = 0; i < FRAME_COUNT; i++) {
            FRAMES[i] = Identifier.of("odyssey", "textures/gui/death/ui_of_death" + i + ".png");
        }
    }

    private DeathUICutscene() {}

    public static void register() {
        HudRenderCallback.EVENT.register(new DeathUICutscene());
    }

    public static void start() {
        active = true;
        startMs = Util.getMeasuringTimeMs();
    }

    public static boolean isActive() { return active; }

    private static float t() { return (Util.getMeasuringTimeMs() - startMs) / 1000.0f; }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter renderTickCounter) {
        if (!active) return;

        final float time = t();
        final int sw = ctx.getScaledWindowWidth();
        final int sh = ctx.getScaledWindowHeight();

        float blackAlpha = time <= FADE_BLACK_IN ? ease(time / FADE_BLACK_IN) : 1f;
        if (blackAlpha > 0f) {
            ctx.fill(0, 0, sw, sh, ((int)(blackAlpha * 255f) << 24));
        }

        final float afterFade = Math.max(0f, time - FADE_BLACK_IN);
        final int seqIdx = MathHelper.clamp((int)Math.floor(afterFade / FRAME_DURATION), 0, 17);
        final boolean showSeq = (time > FADE_BLACK_IN) && (time < whiteFullTime());

        float shake = 0f;
        if (showSeq && afterFade <= SHAKE_TIME) {
            shake = 0.5f - (afterFade / SHAKE_TIME);
        }
        if (showSeq) {
            drawCenteredFramePixelPerfect(ctx, FRAMES[seqIdx], sw, sh, shake);
        }

        float whiteAlpha;
        if (time < whiteStartTime()) {
            whiteAlpha = 0f;
        } else if (time < whiteFullTime()) {
            whiteAlpha = MathHelper.clamp((time - whiteStartTime()) / WHITE_FADE_TIME, 0f, 1f);
        } else if (time < loopEndTime()) {
            whiteAlpha = 1f;
        } else if (time < totalTime()) {
            whiteAlpha = 1f - MathHelper.clamp((time - loopEndTime()) / WHITE_FADE_OUT, 0f, 1f);
        } else {
            whiteAlpha = 0f;
        }

        if (whiteAlpha > 0f) {
            int a = (int)(whiteAlpha * 255f);
            int argbWhite = (a << 24) | 0xFFFFFF;
            ctx.fill(0, 0, sw, sh, argbWhite);
        }

        if (time >= loopStartTime() && time < loopEndTime()) {
            float loopT = time - loopStartTime();
            int toggles = (int)Math.floor((loopT) / LOOP_PERIOD);
            int frame18or19 = (toggles % 2 == 0) ? 18 : 19;

            drawCenteredFramePixelPerfect(ctx, FRAMES[frame18or19], sw, sh, 0f);
        }

        if (time >= totalTime()) {
            active = false;
        }
    }

    private static void drawCenteredFramePixelPerfect(DrawContext ctx, Identifier tex, int sw, int sh, float shake) {
        final int SRC_W = 256, SRC_H = 80;
        final float aspect = (float) SRC_W / SRC_H;

        float base = 0.2f * Math.min(sw, sh);
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

        float t = (Util.getMeasuringTimeMs() % 1000L) / 1000f;
        float amp = 8f * Math.max(0f, Math.min(1f, shake));
        int jitterX = Math.round((float)(Math.sin(t * Math.PI * 10.0) * amp));
        int jitterY = Math.round((float)(Math.cos(t * Math.PI * 12.0) * amp));

        int x = (sw - fw) / 2 + jitterX;
        int y = (sh - fh) / 2 + jitterY;

        ctx.drawTexture(tex, x, y, 0f, 0f, fw, fh, fw, fh);
    }

    private static float ease(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }


}