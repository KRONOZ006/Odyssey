package net.kronoz.odyssey;

import com.mojang.blaze3d.systems.RenderSystem;
import eu.midnightdust.lib.config.MidnightConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.kronoz.odyssey.block.custom.SimpleBlockLightManager;
import net.kronoz.odyssey.client.ClientElevatorAssist;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.entity.MapBlockEntityRenderer;
import net.kronoz.odyssey.entity.apostasy.ApostasyRenderer;
import net.kronoz.odyssey.entity.projectile.LaserProjectileEntity;
import net.kronoz.odyssey.entity.projectile.LaserProjectileRenderer;
import net.kronoz.odyssey.entity.sentinel.SentinelLightClient;
import net.kronoz.odyssey.entity.sentinel.SentinelRenderer;
import net.kronoz.odyssey.entity.sentry.SentryRenderer;
import net.kronoz.odyssey.init.*;
import net.kronoz.odyssey.item.client.renderer.GrappleHookRenderer;
import net.kronoz.odyssey.particle.SentryShieldFullParticle;
import net.kronoz.odyssey.systems.dialogue.client.DialogueClient;
import net.kronoz.odyssey.systems.grapple.GrappleNetworking;
import net.kronoz.odyssey.systems.physics.DustManager;
import net.kronoz.odyssey.systems.physics.LightDustPinger;
import net.kronoz.odyssey.systems.physics.jetpack.JetpackSystem;
import net.kronoz.odyssey.systems.physics.wire.*;
import net.kronoz.odyssey.systems.slide.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class OdysseyClient implements ClientModInitializer {

    private static final Identifier FOG = Identifier.of(Odyssey.MODID, "fog");
    private boolean fogadded = false;

    private static final int DURATION = 14;
    private static KeyBinding SLIDE_KEY;

    @Override
    public void onInitializeClient() {
        ClientElevatorAssist.init();
        WireWorldRenderer.init();
        WireClientMirror.init();
        SimpleBlockLightManager.initClient();
        SentinelLightClient.initClient();
        ModEntityRenderers.register();
        DialogueClient.init();
        MidnightConfig.init("odyssey", OdysseyConfig.class);
        JetpackSystem.INSTANCE.install(ModItems.JETPACK);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (net.kronoz.odyssey.client.cs.CutsceneRecorder.I.isPreviewActive()) {
                net.kronoz.odyssey.client.cs.CutsceneRecorder.I.tickPreview();
            }
            net.kronoz.odyssey.systems.physics.jetpack.JetpackExhaustManager.tick(client);
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MatrixStack ms = ctx.matrixStack();
            VertexConsumerProvider vcp = ctx.consumers();
            float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
            ms.push();
            net.kronoz.odyssey.systems.physics.jetpack.JetpackExhaustManager.renderAll(ms, vcp, td);
            ms.pop();
        });
        SLIDE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.odyssey.slide", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.movement"));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.isPaused()) return;

            while (SLIDE_KEY.wasPressed()) {
                ClientPlayNetworking.send(new SlideRequestPayload());
                SlideClientState.begin(DURATION);
            }
            SlideClientState.clientTick();
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
        GrappleNetworking.registerClient();
        GrappleHookRenderer.register();
        GrappleNetworking.registerClient();
        ParticleFactoryRegistry.getInstance().register(ModParticles.SENTRY_SHIELD_FULL_PARTICLE, SentryShieldFullParticle.Factory::new);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var ppm = VeilRenderSystem.renderer().getPostProcessingManager();
            ppm.add(Identifier.of(Odyssey.MODID, "bloom"));
            fogadded = false;
        });
    }
}