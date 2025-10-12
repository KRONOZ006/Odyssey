package net.kronoz.odyssey.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossHudUpdatePayload(int entityId, String title, float hp, float maxHp) implements CustomPayload {
    public static final Id<BossHudUpdatePayload> ID = new Id<>(Identifier.of("odyssey","boss_hud_update"));
    public static final PacketCodec<RegistryByteBuf, BossHudUpdatePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, BossHudUpdatePayload::entityId,
                    PacketCodecs.STRING,  BossHudUpdatePayload::title,
                    PacketCodecs.FLOAT,   BossHudUpdatePayload::hp,
                    PacketCodecs.FLOAT,   BossHudUpdatePayload::maxHp,
                    BossHudUpdatePayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
