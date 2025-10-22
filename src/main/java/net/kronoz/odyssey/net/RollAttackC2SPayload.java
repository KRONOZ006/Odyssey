package net.kronoz.odyssey.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RollAttackC2SPayload(float lx, float ly, float lz, float damage) implements CustomPayload {
    public static final Id<RollAttackC2SPayload> ID = new Id<>(Identifier.of("odyssey","roll_attack_c2s"));
    public static final PacketCodec<RegistryByteBuf, RollAttackC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, RollAttackC2SPayload::lx,
            PacketCodecs.FLOAT, RollAttackC2SPayload::ly,
            PacketCodecs.FLOAT, RollAttackC2SPayload::lz,
            PacketCodecs.FLOAT, RollAttackC2SPayload::damage,
            RollAttackC2SPayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}