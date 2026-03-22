package net.kronoz.odyssey.systems.physics.wire;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WireStorage extends PersistentState {
    public static final String KEY = "odyssey_wires";

    private static final Type<WireStorage> TYPE = new Type<>(
            WireStorage::new,
            WireStorage::fromNbt,
            null
    );

    private final Map<UUID, WireRecord> records = new LinkedHashMap<>();
    private final Long2ObjectOpenHashMap<ObjectOpenHashSet<UUID>> wireIdsByAnchorBlock = new Long2ObjectOpenHashMap<>();

    public static WireStorage get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE, KEY);
    }

    public static WireStorage fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        WireStorage storage = new WireStorage();
        NbtList list = nbt.getList("wires", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            WireRecord rec = WireRecord.fromNbt(list.getCompound(i));
            storage.records.put(rec.id, rec);
            storage.indexRecord(rec);
        }
        return storage;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (WireRecord rec : records.values()) list.add(rec.toNbt());
        nbt.put("wires", list);
        return nbt;
    }

    public void put(WireRecord rec) {
        WireRecord previous = records.put(rec.id, rec);
        if (previous != null) {
            deindexRecord(previous);
        }
        indexRecord(rec);
        markDirty();
    }

    public void remove(UUID id) {
        WireRecord removed = records.remove(id);
        if (removed != null) {
            deindexRecord(removed);
            markDirty();
        }
    }

    public WireRecord get(UUID id) { return records.get(id); }

    public Collection<WireRecord> all() { return records.values(); }

    public List<WireRecord> attachedTo(BlockPos pos) {
        ObjectOpenHashSet<UUID> ids = wireIdsByAnchorBlock.get(pos.asLong());
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        ArrayList<WireRecord> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            WireRecord record = records.get(id);
            if (record != null) {
                out.add(record);
            }
        }
        return out;
    }

    public boolean hasEquivalent(WireAnchor a, WireAnchor b) {
        ObjectOpenHashSet<UUID> candidates = wireIdsByAnchorBlock.get(a.pos.asLong());
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (UUID id : candidates) {
            WireRecord existing = records.get(id);
            if (existing == null) {
                continue;
            }
            if (matchesPair(existing.a, existing.b, a, b) || matchesPair(existing.a, existing.b, b, a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPair(WireAnchor a1, WireAnchor b1, WireAnchor a2, WireAnchor b2) {
        return a1.pos.equals(a2.pos)
                && a1.face == a2.face
                && a1.offsetWorld.squaredDistanceTo(a2.offsetWorld) < 1.0E-6
                && b1.pos.equals(b2.pos)
                && b1.face == b2.face
                && b1.offsetWorld.squaredDistanceTo(b2.offsetWorld) < 1.0E-6;
    }

    private void indexRecord(WireRecord record) {
        index(record.a.pos, record.id);
        index(record.b.pos, record.id);
    }

    private void deindexRecord(WireRecord record) {
        deindex(record.a.pos, record.id);
        deindex(record.b.pos, record.id);
    }

    private void index(BlockPos pos, UUID id) {
        wireIdsByAnchorBlock.computeIfAbsent(pos.asLong(), k -> new ObjectOpenHashSet<>()).add(id);
    }

    private void deindex(BlockPos pos, UUID id) {
        ObjectOpenHashSet<UUID> ids = wireIdsByAnchorBlock.get(pos.asLong());
        if (ids == null) {
            return;
        }
        ids.remove(id);
        if (ids.isEmpty()) {
            wireIdsByAnchorBlock.remove(pos.asLong());
        }
    }
}
