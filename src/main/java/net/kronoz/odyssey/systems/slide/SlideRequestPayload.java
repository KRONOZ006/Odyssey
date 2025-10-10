package net.kronoz.odyssey.systems.slide;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SlideRequestPayload() implements CustomPayload {
    public static final Id<SlideRequestPayload> ID = new Id<>(Identifier.of("odyssey", "slide_request"));
    public static final PacketCodec<RegistryByteBuf, SlideRequestPayload> CODEC = PacketCodec.unit(new SlideRequestPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
