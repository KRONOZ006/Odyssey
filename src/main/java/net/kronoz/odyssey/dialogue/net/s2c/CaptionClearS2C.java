package net.kronoz.odyssey.dialogue.net.s2c;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public record CaptionClearS2C() implements CustomPayload {
    public static final Id<CaptionClearS2C> ID = new Id<>(Identifier.of("odyssey","caption_clear"));

    public static final PacketCodec<RegistryByteBuf, CaptionClearS2C> CODEC =
            PacketCodec.unit(new CaptionClearS2C());

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static void registerClient() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ID, CaptionClearS2C::handle);
    }

    private static void handle(CaptionClearS2C payload, ClientPlayNetworking.Context ctx){
        var client = ctx.client();
        client.execute(() -> client.inGameHud.setOverlayMessage(Text.empty(), false));
    }
}
