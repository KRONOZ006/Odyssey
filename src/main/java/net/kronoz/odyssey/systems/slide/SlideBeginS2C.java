package net.kronoz.odyssey.systems.slide;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SlideBeginS2C(int durationTicks, float intensity) implements CustomPayload {
    public static final Id<SlideBeginS2C> ID = new Id<>(Identifier.of("odyssey", "slide_begin"));
    public static final PacketCodec<RegistryByteBuf, SlideBeginS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, SlideBeginS2C::durationTicks,
            PacketCodecs.FLOAT, SlideBeginS2C::intensity,
            SlideBeginS2C::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
