package net.kronoz.odyssey.hud.death;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.kronoz.odyssey.net.ResetFadeS2CPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class ResetFadeClient implements HudRenderCallback {
    private static boolean active;
    private static long startedMs;
    private static int fadeInTicks;
    private static int holdTicks;
    private static int fadeOutTicks;

    private ResetFadeClient() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(new ResetFadeClient());
        ClientPlayNetworking.registerGlobalReceiver(ResetFadeS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> start(payload.fadeInTicks(), payload.holdTicks(), payload.fadeOutTicks()))
        );
    }

    public static void start(int inTicks, int holdTicksIn, int outTicks) {
        fadeInTicks = Math.max(1, inTicks);
        holdTicks = Math.max(0, holdTicksIn);
        fadeOutTicks = Math.max(1, outTicks);
        startedMs = Util.getMeasuringTimeMs();
        active = true;
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!active) {
            return;
        }

        float t = (Util.getMeasuringTimeMs() - startedMs) / 50.0f;
        float alpha = alphaAt(t);
        if (alpha <= 0.002f) {
            if (t > totalTicks()) {
                active = false;
            }
            return;
        }

        int a = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        context.fill(0, 0, sw, sh, (a << 24));
    }

    private static float alphaAt(float ticks) {
        float inEnd = fadeInTicks;
        float holdEnd = inEnd + holdTicks;
        float outEnd = holdEnd + fadeOutTicks;

        if (ticks <= inEnd) {
            return smooth01(ticks / inEnd);
        }
        if (ticks <= holdEnd) {
            return 1.0f;
        }
        if (ticks <= outEnd) {
            float normalized = (ticks - holdEnd) / Math.max(1.0f, fadeOutTicks);
            return 1.0f - smooth01(normalized);
        }
        return 0.0f;
    }

    private static float totalTicks() {
        return fadeInTicks + holdTicks + fadeOutTicks;
    }

    private static float smooth01(float value) {
        float clamped = MathHelper.clamp(value, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
