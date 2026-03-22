package net.kronoz.odyssey.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ResetFadeS2CPayload(int fadeInTicks, int holdTicks, int fadeOutTicks) implements CustomPayload {
    public static final Id<ResetFadeS2CPayload> ID = new Id<>(Identifier.of("odyssey", "reset_fade_s2c"));
    public static final PacketCodec<RegistryByteBuf, ResetFadeS2CPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.fadeInTicks);
                buf.writeVarInt(payload.holdTicks);
                buf.writeVarInt(payload.fadeOutTicks);
            },
            buf -> new ResetFadeS2CPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
