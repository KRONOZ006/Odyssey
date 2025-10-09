package net.kronoz.odyssey.systems.grapple;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class GrapplePayloads {
    private static final String MODID = "odyssey";

    public sealed interface ClientToServer extends CustomPayload permits FlingC2S, DetachC2S {}
    public static final class FlingC2S implements ClientToServer {
        public static final Id<FlingC2S> ID = new Id<>(Identifier.of(MODID, "grapple_fling"));
        public static final FlingC2S INSTANCE = new FlingC2S();
        public static final PacketCodec<PacketByteBuf, FlingC2S> CODEC = PacketCodec.unit(INSTANCE);
        private FlingC2S() {}
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static final class DetachC2S implements ClientToServer {
        public static final Id<DetachC2S> ID = new Id<>(Identifier.of(MODID, "grapple_detach"));
        public static final DetachC2S INSTANCE = new DetachC2S();
        public static final PacketCodec<PacketByteBuf, DetachC2S> CODEC = PacketCodec.unit(INSTANCE);
        private DetachC2S() {}
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }


    public sealed interface ServerToClient extends CustomPayload permits SyncStateS2C {}
    public record SyncStateS2C(boolean latched, int latchedEntityId, double ax, double ay, double az, double ropeLen)
            implements ServerToClient {
        public static final Id<SyncStateS2C> ID = new Id<>(Identifier.of(MODID, "grapple_state"));
        public static final PacketCodec<PacketByteBuf, SyncStateS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL, SyncStateS2C::latched,
                PacketCodecs.INTEGER, SyncStateS2C::latchedEntityId,
                PacketCodecs.DOUBLE, SyncStateS2C::ax,
                PacketCodecs.DOUBLE, SyncStateS2C::ay,
                PacketCodecs.DOUBLE, SyncStateS2C::az,
                PacketCodecs.DOUBLE, SyncStateS2C::ropeLen,
                SyncStateS2C::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(FlingC2S.ID, FlingC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(DetachC2S.ID, DetachC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncStateS2C.ID, SyncStateS2C.CODEC);
    }
}
