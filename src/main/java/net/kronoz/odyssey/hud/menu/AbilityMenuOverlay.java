package net.kronoz.odyssey.hud.menu;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public class AbilityMenuOverlay implements HudRenderCallback {

    private static final Identifier FRAMES = Identifier.of("odyssey", "textures/gui/menu/select");


    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter renderTickCounter) {

        int x = 0;
        int y = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            x = width / 2;
            y = height;
        }


        // LET ME COOK DARKFOX HOL UP
        // ok. - Dark


    }
}