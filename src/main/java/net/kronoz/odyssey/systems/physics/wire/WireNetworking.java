package net.kronoz.odyssey.systems.physics.wire;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.UUID;

public final class WireNetworking {
    private static final Identifier SNAPSHOT_ID = Identifier.of("odyssey", "wire_snapshot_s2c");
    private static final Identifier UPSERT_ID = Identifier.of("odyssey", "wire_upsert_s2c");
    private static final Identifier REMOVE_ID = Identifier.of("odyssey", "wire_remove_s2c");

    private WireNetworking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(SnapshotPayload.ID, SnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpsertPayload.ID, UpsertPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemovePayload.ID, RemovePayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.getWorld() instanceof ServerWorld serverWorld) {
                sendSnapshot(handler.player, WireStorage.get(serverWorld).all());
            }
        });
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SnapshotPayload.ID, (payload, context) ->
                context.client().execute(() -> WireClientMirror.applySnapshotNbt(payload.nbt()))
        );
        ClientPlayNetworking.registerGlobalReceiver(UpsertPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    NbtCompound nbt = payload.nbt();
                    if (nbt != null && !nbt.isEmpty()) {
                        WireClientMirror.upsert(WireRecord.fromNbt(nbt));
                    }
                })
        );
        ClientPlayNetworking.registerGlobalReceiver(RemovePayload.ID, (payload, context) ->
                context.client().execute(() -> WireClientMirror.remove(payload.id()))
        );
    }

    public static void sendSnapshot(ServerPlayerEntity player, Collection<WireRecord> records) {
        if (player == null || records == null) {
            return;
        }
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (WireRecord record : records) {
            list.add(record.toNbt());
        }
        root.put("wires", list);
        ServerPlayNetworking.send(player, new SnapshotPayload(root));
    }

    public static void broadcastUpsert(ServerWorld world, WireRecord record) {
        if (world == null || record == null) {
            return;
        }
        UpsertPayload payload = new UpsertPayload(record.toNbt());
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void broadcastRemove(ServerWorld world, UUID id) {
        if (world == null || id == null) {
            return;
        }
        RemovePayload payload = new RemovePayload(id);
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public record SnapshotPayload(NbtCompound nbt) implements CustomPayload {
        public static final Id<SnapshotPayload> ID = new Id<>(SNAPSHOT_ID);
        public static final PacketCodec<RegistryByteBuf, SnapshotPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeNbt(payload.nbt),
                buf -> {
                    NbtCompound nbt = buf.readNbt();
                    return new SnapshotPayload(nbt == null ? new NbtCompound() : nbt);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpsertPayload(NbtCompound nbt) implements CustomPayload {
        public static final Id<UpsertPayload> ID = new Id<>(UPSERT_ID);
        public static final PacketCodec<RegistryByteBuf, UpsertPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeNbt(payload.nbt),
                buf -> {
                    NbtCompound nbt = buf.readNbt();
                    return new UpsertPayload(nbt == null ? new NbtCompound() : nbt);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RemovePayload(UUID id) implements CustomPayload {
        public static final Id<RemovePayload> ID = new Id<>(REMOVE_ID);
        public static final PacketCodec<RegistryByteBuf, RemovePayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeUuid(payload.id),
                buf -> new RemovePayload(buf.readUuid())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
