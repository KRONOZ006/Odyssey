package net.kronoz.odyssey;

import eu.midnightdust.lib.config.MidnightConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kronoz.odyssey.block.custom.SimpleBlockLightManager;
import net.kronoz.odyssey.client.ClientElevatorAssist;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.entity.GroundDecalRenderer;
import net.kronoz.odyssey.entity.MapBlockEntityRenderer;
import net.kronoz.odyssey.entity.apostasy.ApostasyRenderer;
import net.kronoz.odyssey.entity.projectile.LaserProjectileEntity;
import net.kronoz.odyssey.entity.projectile.LaserProjectileRenderer;
import net.kronoz.odyssey.entity.sentinel.SentinelLightClient;
import net.kronoz.odyssey.entity.sentinel.SentinelRenderer;
import net.kronoz.odyssey.entity.sentry.SentryRenderer;
import net.kronoz.odyssey.hud.bosshud.BossHudClient;
import net.kronoz.odyssey.hud.death.DeathUICutscene;
import net.kronoz.odyssey.init.*;
import net.kronoz.odyssey.item.client.renderer.GrappleHookRenderer;
import net.kronoz.odyssey.net.BossHudClearPayload;
import net.kronoz.odyssey.net.BossHudUpdatePayload;
import net.kronoz.odyssey.net.CineNetworking;
import net.kronoz.odyssey.particle.SentryShieldFullParticle;
import net.kronoz.odyssey.systems.cam.ShakeEvents;
import net.kronoz.odyssey.systems.cinematics.runtime.BootstrapScenes;
import net.kronoz.odyssey.systems.cinematics.runtime.CutsceneManager;
import net.kronoz.odyssey.systems.dialogue.client.DialogueClient;
import net.kronoz.odyssey.systems.grapple.GrappleNetworking;
import net.kronoz.odyssey.systems.physics.dust.DustManager;
import net.kronoz.odyssey.systems.physics.dust.LightDustPinger;
import net.kronoz.odyssey.systems.physics.jetpack.JetpackSystem;
import net.kronoz.odyssey.systems.physics.wire.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;


public class OdysseyClient implements ClientModInitializer {


    private static final Identifier FOG = Identifier.of(Odyssey.MODID, "fog");
    private static final Identifier BLOOM = Identifier.of(Odyssey.MODID, "bloom");
    private boolean fogadded = false;


    private KeyBinding flingKey;
    private static final WireBridge.Def ROPE = new WireBridge.Def(
            14,     // segments
            0.035f, // thickness
            0.85f,  // stiffness
            0.18f,  // damping
            0.00f,  // gravity
            0.02f,  // drag
            1.08f   // maxStretch
    );

    @Override
    public void onInitializeClient() {
        ShakeEvents.registerClient();
        ClientElevatorAssist.init();
        WireWorldRenderer.init();
        WireClientMirror.init();
        SimpleBlockLightManager.initClient();
        SentinelLightClient.initClient();
        ModEntityRenderers.register();
        DialogueClient.init();
        MidnightConfig.init("odyssey", OdysseyConfig.class);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (net.kronoz.odyssey.client.cs.CutsceneRecorder.I.isPreviewActive()) {
                net.kronoz.odyssey.client.cs.CutsceneRecorder.I.tickPreview();
            }
        });
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
        EntityRendererRegistry.register(ModEntities.GROUND_DECAL, GroundDecalRenderer::new);

        DeathUICutscene.register();

        GrappleHookRenderer.register();

        WireBridge.initRenderer(); // initialise WireWorldRenderer si dispo


        DustManager.INSTANCE.installHooks();
        new LightDustPinger().install();
        EntityRendererRegistry.register(ModEntities.LASER_PROJECTILE, ctx -> new EntityRenderer<LaserProjectileEntity>(ctx) {
            @Override
            public void render(LaserProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {}
            @Override
            public Identifier getTexture(LaserProjectileEntity entity) { return null; }
        });
        EntityRendererRegistry.register(ModEntities.APOSTASY, ApostasyRenderer::new);
        EntityRendererRegistry.register(ModEntities.SENTRY, SentryRenderer::new);
        EntityRendererRegistry.register(ModEntities.SENTINEL, SentinelRenderer::new);
        EntityRendererRegistry.register(ModEntities.LASER_PROJECTILE, LaserProjectileRenderer::new);

        BlockEntityRendererFactories.register(ModBlocks.MAP_BLOCK_ENTITY, MapBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MAP_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ALARM, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LIGHT1, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FACILITY_REBAR_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ENERGY_BARRIER, RenderLayer.getCutout());

        GrappleNetworking.registerClient();
        GrappleHookRenderer.register();
        GrappleNetworking.registerClient();

        PayloadTypeRegistry.playS2C().register(BossHudUpdatePayload.ID, BossHudUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossHudClearPayload.ID,  BossHudClearPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(BossHudUpdatePayload.ID, (payload, ctx) -> {
            var c = ctx.client();
            c.execute(() -> BossHudClient.put(payload.entityId(), payload.title(), payload.hp(), payload.maxHp()));
        });
        ClientPlayNetworking.registerGlobalReceiver(BossHudClearPayload.ID, (payload, ctx) -> {
            var c = ctx.client();
            c.execute(BossHudClient::clear);
        });
        BossHudClient.register();
        ParticleFactoryRegistry.getInstance().register(ModParticles.SENTRY_SHIELD_FULL_PARTICLE, SentryShieldFullParticle.Factory::new);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();
            ppm.add(1, BLOOM);
            fogadded = false;


        });
        BootstrapScenes.registerAll();
        CineNetworking.registerClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> CutsceneManager.I.clientTick());


        ClientTickEvents.END_CLIENT_TICK.register(client -> {


            if (client.player == null || client.world == null) return;

            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();

            boolean inVoid = client.world.getRegistryKey().getValue().equals(Identifier.of("odyssey:void"));
            if (inVoid && !fogadded) {
                ppm.add(2,FOG);
                fogadded = true;
            } else if (!inVoid && fogadded) {
                ppm.remove(FOG);
                fogadded = false;
            }
        });



        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (type == EntityType.PLAYER && renderer instanceof PlayerEntityRenderer per) {
                FeatureRendererContext<PlayerEntity, PlayerEntityModel<PlayerEntity>> castCtx =
                        (FeatureRendererContext<PlayerEntity, PlayerEntityModel<PlayerEntity>>) (FeatureRendererContext<?, ?>) per;


            }
        });
    }
    private static UUID nameId(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}