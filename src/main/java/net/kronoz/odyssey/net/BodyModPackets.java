package net.kronoz.odyssey.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kronoz.odyssey.Odyssey;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BodyModPackets {

    // --- Codecs custom (évite les constantes manquantes) ---
    public static final PacketCodec<RegistryByteBuf, Identifier> IDENT_CODEC = new PacketCodec<>() {
        @Override public void encode(RegistryByteBuf buf, Identifier value) { buf.writeIdentifier(value); }
        @Override public Identifier decode(RegistryByteBuf buf) { return buf.readIdentifier(); }
    };
    public static final PacketCodec<RegistryByteBuf, UUID> UUID_CODEC = new PacketCodec<>() {
        @Override public void encode(RegistryByteBuf buf, UUID value) { buf.writeUuid(value); }
        @Override public UUID decode(RegistryByteBuf buf) { return buf.readUuid(); }
    };
    public static final PacketCodec<RegistryByteBuf, Map<String, Identifier>> MAP_STRING_IDENT_CODEC =
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, IDENT_CODEC);

    // --- Payloads ---

    public static final class ApplyPartC2S implements CustomPayload {
        public static final Id<ApplyPartC2S> ID = new Id<>(Odyssey.id("apply_part"));
        public static final PacketCodec<RegistryByteBuf, ApplyPartC2S> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, p -> p.slot,
                        IDENT_CODEC,          p -> p.partId,
                        ApplyPartC2S::new);

        public final String slot;
        public final Identifier partId;
        public ApplyPartC2S(String slot, Identifier partId){ this.slot = slot; this.partId = partId; }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final class ClearSlotC2S implements CustomPayload {
        public static final Id<ClearSlotC2S> ID = new Id<>(Odyssey.id("clear_slot"));
        public static final PacketCodec<RegistryByteBuf, ClearSlotC2S> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, p -> p.slot, ClearSlotC2S::new);

        public final String slot;
        public ClearSlotC2S(String slot){ this.slot = slot; }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final class SyncBodyS2C implements CustomPayload {
        public static final Id<SyncBodyS2C> ID = new Id<>(Odyssey.id("sync_body"));
        public static final PacketCodec<RegistryByteBuf, SyncBodyS2C> CODEC =
                PacketCodec.tuple(
                        UUID_CODEC,               p -> p.player,
                        MAP_STRING_IDENT_CODEC,   p -> p.equipped,
                        SyncBodyS2C::new);

        public final UUID player;
        public final Map<String, Identifier> equipped;
        public SyncBodyS2C(UUID player, Map<String, Identifier> equipped){
            this.player = player; this.equipped = equipped;
        }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerTypes(){
        PayloadTypeRegistry.playC2S().register(ApplyPartC2S.ID, ApplyPartC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearSlotC2S.ID, ClearSlotC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBodyS2C.ID, SyncBodyS2C.CODEC);
    }
}
