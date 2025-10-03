package net.kronoz.odyssey;

import eu.midnightdust.lib.config.MidnightConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.block.custom.SimpleBlockLightManager;
import net.kronoz.odyssey.client.ClientElevatorAssist;
import net.kronoz.odyssey.command.CineCommand;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.entity.MapBlockEntityRenderer;
import net.kronoz.odyssey.init.ModBlocks;
import net.kronoz.odyssey.init.ModEntityRenderers;
import net.kronoz.odyssey.systems.cinematics.CineClient;
import net.kronoz.odyssey.systems.dialogue.client.DialogueClient;
import net.kronoz.odyssey.systems.physics.DustManager;
import net.kronoz.odyssey.systems.physics.LightDustPinger;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionTypes;

public class OdysseyClient implements ClientModInitializer {

    private static final Identifier NOISE = Identifier.of(Odyssey.MODID, "noise");
    private static final Identifier DARK  = Identifier.of(Odyssey.MODID, "dark");


    private boolean darkAdded = false;

    @Override
    public void onInitializeClient() {
        ClientElevatorAssist.init();
        CineClient.init();
        CineCommand.register();
        SimpleBlockLightManager.initClient();
        ModEntityRenderers.register();
        DialogueClient.init();
        MidnightConfig.init("odyssey", OdysseyConfig.class);




        DustManager.INSTANCE.installHooks();
        new LightDustPinger().install();


        BlockEntityRendererFactories.register(ModBlocks.MAP_BLOCK_ENTITY, MapBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MAP_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LIGHT1, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FACILITY_REBAR_BLOCK, RenderLayer.getCutout());


        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();
            ppm.add(Identifier.of(Odyssey.MODID, "clouds"));
            ppm.add(Identifier.of(Odyssey.MODID, "fog"));
            ppm.add(Identifier.of(Odyssey.MODID, "bloom"));
            ppm.add(NOISE);
            darkAdded = false;
        });

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
