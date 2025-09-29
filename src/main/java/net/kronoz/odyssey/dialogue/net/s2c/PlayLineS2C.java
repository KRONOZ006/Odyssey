package net.kronoz.odyssey.dialogue.net.s2c;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.dialogue.client.DialogueHud;
import net.kronoz.odyssey.dialogue.data.DialogueLine;
import net.kronoz.odyssey.dialogue.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

// Payload SERVEUR -> CLIENT
public record PlayLineS2C(String caption, Optional<Identifier> soundId, int durationMs) implements CustomPayload {
    public static final Id<PlayLineS2C> ID = new Id<>(Packets.PLAY_LINE_ID);
    private static final PacketCodec<ByteBuf, Optional<Identifier>> OPT_ID =
            PacketCodecs.optional(Identifier.PACKET_CODEC);

    public static final PacketCodec<PacketByteBuf, PlayLineS2C> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, PlayLineS2C::caption,
                    OPT_ID,               PlayLineS2C::soundId,
                    PacketCodecs.VAR_INT, PlayLineS2C::durationMs,
                    PlayLineS2C::new
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    // --- Envoi côté serveur
    public static void send(ServerPlayerEntity p, DialogueLine line){
        ServerPlayNetworking.send(p, new PlayLineS2C(
                line.caption(),
                Optional.ofNullable(line.soundId()),
                Math.max(1, line.durationMs())
        ));
    }

    // --- Enregistrement (à appeler depuis Packets.initClient)
    public static void registerClient() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ID, PlayLineS2C::handle);
    }

    private static void handle(PlayLineS2C payload,
                               net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context ctx) {
        var client = ctx.client();
        client.execute(() -> {
            client.inGameHud.setOverlayMessage(Text.literal(payload.caption()), false); // <- overlay au-dessus de la hotbar
            payload.soundId().ifPresent(snd -> {
                var ev = net.minecraft.sound.SoundEvent.of(snd);
                client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(ev, 1.0f));
            });
        });
    }
}
