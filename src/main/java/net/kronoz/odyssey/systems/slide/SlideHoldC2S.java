package net.kronoz.odyssey.systems.slide;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SlideHoldC2S(boolean held) implements CustomPayload {
    public static final Id<SlideHoldC2S> ID = new Id<>(Identifier.of("odyssey", "slide_hold"));
    public static final PacketCodec<RegistryByteBuf, SlideHoldC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, SlideHoldC2S::held,
            SlideHoldC2S::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
