package net.kronoz.odyssey;

import eu.midnightdust.lib.config.MidnightConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.kronoz.odyssey.block.custom.SimpleBlockLightManager;
import net.kronoz.odyssey.client.ClientElevatorAssist;
import net.kronoz.odyssey.command.CineCommand;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.entity.MapBlockEntityRenderer;
import net.kronoz.odyssey.entity.sentinel.SentinelLightClient;
import net.kronoz.odyssey.entity.sentinel.SentinelRenderer;
import net.kronoz.odyssey.entity.sentry.SentryRenderer;
import net.kronoz.odyssey.init.*;
import net.kronoz.odyssey.particle.SentryShieldFullParticle;
import net.kronoz.odyssey.systems.cinematics.CineClient;
import net.kronoz.odyssey.systems.dialogue.client.DialogueClient;
import net.kronoz.odyssey.systems.physics.DustManager;
import net.kronoz.odyssey.systems.physics.LightDustPinger;
import net.kronoz.odyssey.systems.physics.jetpack.JetpackSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class OdysseyClient implements ClientModInitializer {

    private static final Identifier FOG = Identifier.of(Odyssey.MODID, "fog");
    private boolean fogadded = false;


    @Override
    public void onInitializeClient() {
        ClientElevatorAssist.init();
        CineClient.init();
        CineCommand.register();
        SimpleBlockLightManager.initClient();
        SentinelLightClient.initClient();
        ModEntityRenderers.register();
        DialogueClient.init();
        MidnightConfig.init("odyssey", OdysseyConfig.class);
        JetpackSystem.INSTANCE.install(ModItems.JETPACK);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc ->
                net.kronoz.odyssey.systems.physics.jetpack.JetpackExhaustManager.tick(mc));

        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MatrixStack ms = ctx.matrixStack();
            VertexConsumerProvider vcp = ctx.consumers();
            float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
            ms.push();
            net.kronoz.odyssey.systems.physics.jetpack.JetpackExhaustManager.renderAll(ms, vcp, td);
            ms.pop();
        });


        DustManager.INSTANCE.installHooks();
        new LightDustPinger().install();

        EntityRendererRegistry.register(ModEntities.SENTRY, SentryRenderer::new);
        EntityRendererRegistry.register(ModEntities.SENTINEL, SentinelRenderer::new);

        BlockEntityRendererFactories.register(ModBlocks.MAP_BLOCK_ENTITY, MapBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MAP_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ALARM, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LIGHT1, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FACILITY_REBAR_BLOCK, RenderLayer.getCutout());



        ParticleFactoryRegistry.getInstance().register(ModParticles.SENTRY_SHIELD_FULL_PARTICLE, SentryShieldFullParticle.Factory::new);


        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();
            ppm.add(Identifier.of(Odyssey.MODID, "bloom"));
            fogadded = false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();

            boolean inVoid = client.world.getRegistryKey().getValue().equals(Identifier.of("odyssey:void"));
            if (inVoid && !fogadded) {
                ppm.add(FOG);
                fogadded = true;
            } else if (!inVoid && fogadded) {
                ppm.remove(FOG);
                fogadded = false;
            }
        });


    }
}
