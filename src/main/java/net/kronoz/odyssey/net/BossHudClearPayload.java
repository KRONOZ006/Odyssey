package net.kronoz.odyssey.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossHudClearPayload() implements CustomPayload {
    public static final Id<BossHudClearPayload> ID = new Id<>(Identifier.of("odyssey","boss_hud_clear"));
    public static final PacketCodec<RegistryByteBuf, BossHudClearPayload> CODEC =
            PacketCodec.unit(new BossHudClearPayload());

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
