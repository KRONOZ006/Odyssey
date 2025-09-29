package net.kronoz.odyssey.dialogue.net.s2c;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.dialogue.client.ChoiceScreen;
import net.kronoz.odyssey.dialogue.data.DialogueChoice;
import net.kronoz.odyssey.dialogue.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

// Payload SERVEUR -> CLIENT
public record ShowChoicesS2C(boolean multi, List<DialogueChoice> choices) implements CustomPayload {
    public static final Id<ShowChoicesS2C> ID = new Id<>(Packets.SHOW_CHOICES_ID);

    // Petit codec DialogueChoice (id + text uniquement côté client)
    private static final PacketCodec<PacketByteBuf, DialogueChoice> CHOICE_CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, DialogueChoice::id,
                    PacketCodecs.STRING, DialogueChoice::text,
                    (id, text) -> new DialogueChoice(id, text, "")
            );

    public static final PacketCodec<PacketByteBuf, ShowChoicesS2C> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.BOOL,                      ShowChoicesS2C::multi,
                    CHOICE_CODEC.collect(PacketCodecs.toList()), ShowChoicesS2C::choices,
                    ShowChoicesS2C::new
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    // --- Envoi côté serveur
    public static void send(ServerPlayerEntity p, boolean multi, List<DialogueChoice> list){
        ServerPlayNetworking.send(p, new ShowChoicesS2C(multi, list));
    }

    // --- Enregistrement (à appeler depuis Packets.initClient)
    public static void registerClient(){
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ID, ShowChoicesS2C::handle);
    }

    // --- Réception côté client
    private static void handle(ShowChoicesS2C payload,
                               net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context ctx){
        var client = ctx.client();
        client.execute(() -> client.setScreen(new net.kronoz.odyssey.dialogue.client.ChoiceScreen(payload.multi(), payload.choices())));
    }
}
