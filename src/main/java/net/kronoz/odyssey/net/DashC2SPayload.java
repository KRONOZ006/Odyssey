package net.kronoz.odyssey.net;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;

public record DashC2SPayload(float lx, float ly, float lz, float speed, float up) implements CustomPayload {
    public static final CustomPayload.Id<DashC2SPayload> ID = new CustomPayload.Id<>(Identifier.of("odyssey","dash_c2s"));
    public static final PacketCodec<RegistryByteBuf, DashC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, DashC2SPayload::lx,
            PacketCodecs.FLOAT, DashC2SPayload::ly,
            PacketCodecs.FLOAT, DashC2SPayload::lz,
            PacketCodecs.FLOAT, DashC2SPayload::speed,
            PacketCodecs.FLOAT, DashC2SPayload::up,
            DashC2SPayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}