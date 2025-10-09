package net.kronoz.odyssey.systems.grapple;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class GrappleNetworking {

    public void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(GrapplePayloads.ForcePullC2S.ID, (payload, ctx) ->
                ctx.player().server.execute(() -> GrappleServerLogic.forcePull((ServerPlayerEntity) ctx.player())));

        ServerPlayNetworking.registerGlobalReceiver(GrapplePayloads.DetachC2S.ID, (payload, ctx) ->
                ctx.player().server.execute(() -> GrappleServerLogic.detach((ServerPlayerEntity) ctx.player())));
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(GrapplePayloads.SyncStateS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    if (ctx.client().player == null) return;
                    var st = GrappleState.get(ctx.client().player);
                    st.latched = payload.latched();
                    st.latchedEntityId = payload.latchedEntityId();
                    st.anchorPos = new Vec3d(payload.ax(), payload.ay(), payload.az());
                    st.ropeLength = payload.ropeLen();
                }));
    }

    public static void send(GrapplePayloads.ClientToServer payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendTo(ServerPlayerEntity player, GrapplePayloads.ServerToClient payload) {
        ServerPlayNetworking.send(player, payload);
    }
    public static void sendDetach() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                net.kronoz.odyssey.systems.grapple.GrapplePayloads.DetachC2S.INSTANCE
        );
    }
    public static void sendForcePull() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                net.kronoz.odyssey.systems.grapple.GrapplePayloads.ForcePullC2S.INSTANCE
        );
    }


}
