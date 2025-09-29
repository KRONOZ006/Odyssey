package net.kronoz.odyssey.dialogue.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class DialogueHud {
    private static String text = "";
    private static long until = 0L;

    public static void init(){
        HudRenderCallback.EVENT.register(DialogueHud::render);
    }
    public static void pushCaption(String t, int ms){
        text = t;
        until = System.currentTimeMillis() + Math.max(1, ms);
    }

    // Nouvelle signature 1.21.x : (DrawContext, RenderTickCounter)
    private static void render(DrawContext ctx, RenderTickCounter rtc){
        if (text.isEmpty()) return;
        if (System.currentTimeMillis() > until){ text=""; return; }

        var mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int sw = mc.textRenderer.getWidth(text);
        int x = (w - sw) / 2;
        int y = h - 40;

        ctx.fill(x-6, y-6, x+sw+6, y+mc.textRenderer.fontHeight+6, 0x88000000);
        ctx.drawText(mc.textRenderer, text, x, y, 0xFFFFFF, true);
    }
}
