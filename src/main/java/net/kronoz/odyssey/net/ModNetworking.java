package net.kronoz.odyssey.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.cca.BodyModComponent;
import net.kronoz.odyssey.cca.ModComponents;
import net.kronoz.odyssey.data.BodyPartRegistry;
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
}
