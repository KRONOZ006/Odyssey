package net.kronoz.odyssey;

import eu.midnightdust.lib.config.MidnightConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionTypes;

public class OdysseyClient implements ClientModInitializer {

    private static final Identifier NOISE = Identifier.of(Odyssey.MODID, "noise");
    private static final Identifier DARK  = Identifier.of(Odyssey.MODID, "dark");

    private boolean darkAdded = false;

    @Override
    public void onInitializeClient() {
        net.kronoz.odyssey.dialogue.client.DialogueClient.init();
        MidnightConfig.init("odyssey", OdysseyConfig.class);

        // Always add noise on join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();
            ppm.add(NOISE);
            darkAdded = false; // reset when joining
        });

        // Check each tick what dimension we’re in
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();

            boolean inEnd = client.world.getDimensionEntry().matchesId(DimensionTypes.THE_END_ID);

            if (inEnd && !darkAdded) {
                ppm.add(DARK);
                darkAdded = true;
            } else if (!inEnd && darkAdded) {
                ppm.remove(DARK);
                darkAdded = false;
            }
        });
    }
}
