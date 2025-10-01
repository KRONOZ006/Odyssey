package net.kronoz.odyssey.dialogue.net.s2c;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

public record CaptionPlayS2C(String caption, Optional<Identifier> soundId, int durationTicks) implements CustomPayload {
    public static final Id<CaptionPlayS2C> ID = new Id<>(Identifier.of("odyssey", "caption_play"));

    private static final PacketCodec<ByteBuf, Optional<Identifier>> OPT_ID =
            PacketCodecs.optional(Identifier.PACKET_CODEC);

    public static final PacketCodec<RegistryByteBuf, CaptionPlayS2C> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, CaptionPlayS2C::caption,
                    OPT_ID,               CaptionPlayS2C::soundId,
                    PacketCodecs.VAR_INT, CaptionPlayS2C::durationTicks,
                    CaptionPlayS2C::new
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static void registerClient() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ID, CaptionPlayS2C::handle);
    }

    private static void handle(CaptionPlayS2C payload, ClientPlayNetworking.Context ctx) {
        var client = ctx.client();
        client.execute(() -> {
            client.inGameHud.setOverlayMessage(Text.literal(payload.caption), false);
            payload.soundId.ifPresent(snd -> {
                var ev = SoundEvent.of(snd);
                client.getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f));
            });
        });
    }
}
