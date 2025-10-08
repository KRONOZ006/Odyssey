package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.Collection;
import java.util.LinkedHashMap;
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
        records.put(rec.id, rec);
        markDirty();
    }

    public void remove(UUID id) {
        if (records.remove(id) != null) markDirty();
    }

    public WireRecord get(UUID id) { return records.get(id); }

    public Collection<WireRecord> all() { return records.values(); }
}