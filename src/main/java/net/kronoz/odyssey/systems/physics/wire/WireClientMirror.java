package net.kronoz.odyssey.systems.physics.wire;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WireClientMirror {
    private static final double FULL_SIM_DISTANCE_SQ = 72.0 * 72.0;
    private static final double MAX_RENDER_DISTANCE_SQ = 168.0 * 168.0;
    private static final Map<UUID, WireRecord> RECORDS = new LinkedHashMap<>();

    private WireClientMirror() {
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void applySnapshot(Collection<WireRecord> records) {
        RECORDS.clear();
        for (WireRecord record : records) {
            RECORDS.put(record.id, record);
            WireManager.ensureFromRecordClient(record);
        }
        WireManager.syncToIds(RECORDS.keySet());
    }

    public static void applySnapshotNbt(@Nullable NbtCompound payload) {
        if (payload == null) {
            reset();
            return;
        }
        NbtList list = payload.getList("wires", NbtCompound.COMPOUND_TYPE);
        ArrayList<WireRecord> decoded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            decoded.add(WireRecord.fromNbt(list.getCompound(i)));
        }
        applySnapshot(decoded);
    }

    public static void upsert(WireRecord record) {
        if (record == null) {
            return;
        }
        RECORDS.put(record.id, record);
        WireManager.ensureFromRecordClient(record);
    }

    public static void remove(UUID id) {
        if (id == null) {
            return;
        }
        RECORDS.remove(id);
        WireManager.remove(id);
    }

    public static void renderAll(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || RECORDS.isEmpty()) {
            return;
        }
        net.minecraft.util.math.Vec3d camera = mc.gameRenderer.getCamera().getPos();
        long worldTime = mc.world.getTime();

        for (WireRecord record : RECORDS.values()) {
            Vec3dEndpoints endpoints = endpoints(record);
            net.minecraft.util.math.Vec3d midpoint = endpoints.a.add(endpoints.b).multiply(0.5);
            double distanceSq = midpoint.squaredDistanceTo(camera);
            if (distanceSq > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }
            if (distanceSq > FULL_SIM_DISTANCE_SQ) {
                long phase = (worldTime + Integer.toUnsignedLong(record.id.hashCode())) & 1L;
                if (phase != 0L) {
                    continue;
                }
            }
            boolean aPinned = record.aPinned && !mc.world.getBlockState(record.a.pos).isAir();
            boolean bPinned = record.bPinned && !mc.world.getBlockState(record.b.pos).isAir();
            WireManager.ensure(record.id, WireDef.defaultCable(record.defId), endpoints.a, endpoints.b);
            WireManager.stepAndRender(
                    record.id,
                    endpoints.a, aPinned,
                    endpoints.b, bPinned,
                    matrices,
                    consumers,
                    light,
                    OverlayTexture.DEFAULT_UV
            );
        }
    }

    private static Vec3dEndpoints endpoints(WireRecord record) {
        return new Vec3dEndpoints(
                WireToolMath.anchorCenter(record.a),
                WireToolMath.anchorCenter(record.b)
        );
    }

    private static void reset() {
        RECORDS.clear();
        WireManager.clearAllClient();
    }

    private record Vec3dEndpoints(net.minecraft.util.math.Vec3d a, net.minecraft.util.math.Vec3d b) {
    }
}
