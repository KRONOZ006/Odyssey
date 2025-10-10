package net.kronoz.odyssey.systems.slide;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SlideEndS2C() implements CustomPayload {
    public static final Id<SlideEndS2C> ID = new Id<>(Identifier.of("odyssey", "slide_end"));
    public static final PacketCodec<RegistryByteBuf, SlideEndS2C> CODEC = PacketCodec.unit(new SlideEndS2C());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
