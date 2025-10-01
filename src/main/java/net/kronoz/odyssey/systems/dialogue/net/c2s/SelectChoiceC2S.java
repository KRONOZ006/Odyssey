package net.kronoz.odyssey.systems.dialogue.net.c2s;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.systems.dialogue.net.Packets;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

// Payload CLIENT -> SERVEUR
public record SelectChoiceC2S(String choiceId) implements CustomPayload {
    public static final Id<SelectChoiceC2S> ID = new Id<>(Packets.SELECT_CHOICE_ID);
    public static final PacketCodec<PacketByteBuf, SelectChoiceC2S> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, SelectChoiceC2S::choiceId, SelectChoiceC2S::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    // --- Envoi côté client
    public static void sendClient(String choiceId) {
        ClientPlayNetworking.send(new SelectChoiceC2S(choiceId));
    }

    // --- Enregistrement (à appeler depuis Packets.initCommon)
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, SelectChoiceC2S::handle);
    }

    // --- Réception côté serveur (contexte Fabric 1.21.1)
    private static void handle(SelectChoiceC2S payload,
                               net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context ctx) {
        var player = ctx.player();
        var server = player.getServer();
        if (server == null) return;
        server.execute(() -> net.kronoz.odyssey.systems.dialogue.server.DialogueManager.selectChoice(player, payload.choiceId()));
    }
}
