package net.kronoz.odyssey.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.OdysseyDataGenerator;
import net.kronoz.odyssey.systems.cinematics.runtime.CutsceneManager;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class CineNetworking {
    private CineNetworking(){}

    /* =====================  PAYLOADS  ===================== */

    public record PlayPayload(String sceneId, double speed) implements CustomPayload {
        public static final Id<PlayPayload> ID = new Id<>(Identifier.of(Odyssey.MODID, "play"));
        public static final PacketCodec<RegistryByteBuf, PlayPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, PlayPayload::sceneId,
                        PacketCodecs.DOUBLE, PlayPayload::speed,
                        PlayPayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record StopPayload() implements CustomPayload {
        public static final Id<StopPayload> ID = new Id<>(Identifier.of(Odyssey.MODID, "stop"));

        public static final PacketCodec<RegistryByteBuf, StopPayload> CODEC =
                PacketCodec.of((buf, value) -> {  }, buf -> new StopPayload());

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }



    public record SpeedPayload(double speed) implements CustomPayload {
        public static final Id<SpeedPayload> ID = new Id<>(Identifier.of(Odyssey.MODID, "speed"));
        public static final PacketCodec<RegistryByteBuf, SpeedPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.DOUBLE, SpeedPayload::speed,
                        SpeedPayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /* =====================  REGISTRATION  ===================== */

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(PlayPayload.ID, PlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StopPayload.ID, StopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedPayload.ID, SpeedPayload.CODEC);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(PlayPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (CutsceneManager.I.play(payload.sceneId())) {
                    CutsceneManager.I.setSpeed(payload.speed());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(StopPayload.ID, (payload, context) ->
                context.client().execute(CutsceneManager.I::stop)
        );

        ClientPlayNetworking.registerGlobalReceiver(SpeedPayload.ID, (payload, context) ->
                context.client().execute(() -> CutsceneManager.I.setSpeed(payload.speed()))
        );
    }

    /* =====================  SERVER SEND HELPERS  ===================== */

    public static void playAll(MinecraftServer server, String id, double speed) {
        var payload = new PlayPayload(id, speed);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void stopAll(MinecraftServer server) {
        var payload = new StopPayload();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void speedAll(MinecraftServer server, double speed) {
        var payload = new SpeedPayload(speed);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
