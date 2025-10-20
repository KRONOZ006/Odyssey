package net.kronoz.odyssey.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SliceAttackC2SPayload(float lx, float ly, float lz, float damage) implements CustomPayload {
    public static final Id<SliceAttackC2SPayload> ID = new Id<>(Identifier.of("odyssey","slice_attack_c2s"));
    public static final PacketCodec<RegistryByteBuf, SliceAttackC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, SliceAttackC2SPayload::lx,
            PacketCodecs.FLOAT, SliceAttackC2SPayload::ly,
            PacketCodecs.FLOAT, SliceAttackC2SPayload::lz,
            PacketCodecs.FLOAT, SliceAttackC2SPayload::damage,
            SliceAttackC2SPayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}