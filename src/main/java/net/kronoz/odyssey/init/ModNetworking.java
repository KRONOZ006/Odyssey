package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.net.BodyModPackets;
import net.kronoz.odyssey.net.DashC2SPayload;
import net.kronoz.odyssey.movement.DashHandler;
import net.kronoz.odyssey.systems.data.BodyPartRegistry;
import net.minecraft.entity.player.PlayerEntity;

public final class ModNetworking {
    public static void init() {
        BodyModPackets.registerTypes();

        ServerPlayNetworking.registerGlobalReceiver(BodyModPackets.ApplyPartC2S.ID, (payload, context) -> {
            var server = context.player().getServer();
            if (server == null) return;
            server.execute(() -> {
                var def = BodyPartRegistry.get(payload.partId);
                if (def != null) {
                    var c = ModComponents.BODY.get(context.player());
                    c.setPart(payload.slot, payload.partId);
                    c.sync(context.player());
                }
            });
        });
        PayloadTypeRegistry.playC2S().register(DashC2SPayload.ID, DashC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DashC2SPayload.ID, (payload, ctx) -> {
            var player = ctx.player();
            DashHandler.onDashPacket(player, payload);
        });
        ServerPlayNetworking.registerGlobalReceiver(BodyModPackets.ClearSlotC2S.ID, (payload, context) -> {
            var server = context.player().getServer();
            if (server == null) return;
            server.execute(() -> {
                var c = ModComponents.BODY.get(context.player());
                c.clearSlot(payload.slot);
                c.sync(context.player());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BodyModPackets.SyncBodyS2C.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                PlayerEntity p = client.world != null ? client.world.getPlayerByUuid(payload.player) : null;
                if (p != null) ModComponents.BODY.get(p).clientApply(payload.equipped);
            });
        });
    }
    public static void send(DashC2SPayload payload) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }
}
