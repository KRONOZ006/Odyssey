package net.kronoz.odyssey.hud.bosshud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class BossHudClient implements HudRenderCallback {
    private static final long TIMEOUT_MS = 1500L;
    private static final long SHAKE_MS = 800L;

    private static final Identifier[] TEX_TIER = new Identifier[]{
            Identifier.of("odyssey","textures/gui/apostasy/apostasy1.png"),
            Identifier.of("odyssey","textures/gui/apostasy/apostasy2.png"),
            Identifier.of("odyssey","textures/gui/apostasy/apostasy3.png"),
            Identifier.of("odyssey","textures/gui/apostasy/apostasy4.png")
    };
    private static final Identifier TEX_FILL = Identifier.of("odyssey","textures/gui/apostasy/apostasy5.png");

    private static final int SRC_W = 274;
    private static final int SRC_H = 144;

    private static volatile boolean active;
    private static volatile float health = 0f;
    private static volatile float maxHealth = 1f;
    private static volatile String title = "Apostasy";
    private static volatile long lastUpdateMs;
    private static float alpha = 0f;

    private static int lastTier = -1;
    private static long shakeUntilMs = 0L;

    public static void register() { HudRenderCallback.EVENT.register(new BossHudClient()); }

    public static void put(int entityId, String ignored, float hp, float maxHp) {
        health = hp;
        maxHealth = Math.max(0.001f, maxHp);
        lastUpdateMs = Util.getMeasuringTimeMs();
        active = true;
        int tierNow = tierFor(health / maxHealth);
        if (tierNow != lastTier) { lastTier = tierNow; shakeUntilMs = lastUpdateMs + SHAKE_MS; }
    }

    public static void clear() { active = false; }

    private static int tierFor(float pct) {
        if (pct >= 0.75f) return 0;
        if (pct >= 0.50f) return 1;
        if (pct >= 0.25f) return 2;
        return 3;
    }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tick) {
        if (!active) { alpha = Math.max(0f, alpha - 0.08f); if (alpha <= 0f) return; }
        else { boolean timedOut = Util.getMeasuringTimeMs() - lastUpdateMs > TIMEOUT_MS; if (timedOut) active = false; alpha = Math.min(1f, alpha + 0.08f); }

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        float scale = 0.5f;

        int fullW = SRC_W;
        int fullH = SRC_H;
        int dw = Math.round(fullW * scale);
        int dh = Math.round(fullH * scale);

        int baseX = (sw - dw) / 2;
        int baseY = 0;

        int sx = 0, sy = 0;
        long now = Util.getMeasuringTimeMs();
        if (now < shakeUntilMs) {
            float t = (shakeUntilMs - now) / (float)SHAKE_MS;
            float amp = 8f * (1f - (t * t));
            sx = Math.toIntExact(Math.round(Math.sin(now * 0.045) * amp));
            sy = Math.toIntExact(Math.round(Math.cos(now * 0.057) * amp));
        }

        float pct = MathHelper.clamp(health / maxHealth, 0f, 1f);
        int tier = Math.max(0, Math.min(3, tierFor(pct)));

        int drawX = baseX + sx;
        int drawY = baseY + sy;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(drawX, drawY, 0);
        ctx.getMatrices().scale(scale, scale, 1f);

        int a = Math.round(alpha * 255f);
        ctx.setShaderColor(1f,1f,1f,alpha);
        ctx.drawTexture(TEX_TIER[tier], 0, 0, 0, 0, fullW, fullH, SRC_W, SRC_H);
        ctx.setShaderColor(1f,1f,1f,1f);

        int fillSrcW = Math.max(0, Math.round(SRC_W * pct));
        if (fillSrcW > 0) ctx.drawTexture(TEX_FILL, 0, 0, 0, 0, fillSrcW, fullH, SRC_W, SRC_H);

        ctx.getMatrices().pop();
    }
}