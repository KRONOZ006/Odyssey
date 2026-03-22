package net.kronoz.odyssey.systems.reset;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ResetLinkStorage extends PersistentState {
    public static final String KEY = "odyssey_reset_links";
    private static final Type<ResetLinkStorage> TYPE = new Type<>(
            ResetLinkStorage::new,
            ResetLinkStorage::fromNbt,
            null
    );

    private final Long2LongOpenHashMap zoneToRespawn = new Long2LongOpenHashMap();

    public ResetLinkStorage() {
        zoneToRespawn.defaultReturnValue(Long.MIN_VALUE);
    }

    public static ResetLinkStorage get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, KEY);
    }

    private static ResetLinkStorage fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ResetLinkStorage storage = new ResetLinkStorage();
        NbtList list = nbt.getList("links", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            long zone = entry.getLong("zone");
            long respawn = entry.getLong("respawn");
            storage.zoneToRespawn.put(zone, respawn);
        }
        return storage;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        zoneToRespawn.long2LongEntrySet().forEach(entry -> {
            NbtCompound link = new NbtCompound();
            link.putLong("zone", entry.getLongKey());
            link.putLong("respawn", entry.getLongValue());
            list.add(link);
        });
        nbt.put("links", list);
        return nbt;
    }

    public void link(BlockPos zonePos, BlockPos respawnPos) {
        zoneToRespawn.put(zonePos.asLong(), respawnPos.asLong());
        markDirty();
    }

    public void unlinkZone(BlockPos zonePos) {
        if (zoneToRespawn.remove(zonePos.asLong()) != Long.MIN_VALUE) {
            markDirty();
        }
    }

    public int unlinkRespawn(BlockPos respawnPos) {
        long target = respawnPos.asLong();
        List<Long> toRemove = new ArrayList<>();
        zoneToRespawn.long2LongEntrySet().forEach(entry -> {
            if (entry.getLongValue() == target) {
                toRemove.add(entry.getLongKey());
            }
        });
        for (long key : toRemove) {
            zoneToRespawn.remove(key);
        }
        if (!toRemove.isEmpty()) {
            markDirty();
        }
        return toRemove.size();
    }

    public @Nullable BlockPos linkedRespawn(BlockPos zonePos) {
        long linked = zoneToRespawn.get(zonePos.asLong());
        return linked == Long.MIN_VALUE ? null : BlockPos.fromLong(linked);
    }
}
