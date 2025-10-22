package net.kronoz.odyssey.item.custom;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FirstHoldState extends PersistentState {
    public static final String KEY = "odyssey_cannedfood_first_hold";

    public static final PersistentState.Type<FirstHoldState> TYPE =
            new PersistentState.Type<>(FirstHoldState::new, FirstHoldState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private final Set<UUID> heard = new HashSet<>();

    public static FirstHoldState get(ServerWorld world) {
        PersistentStateManager mgr = world.getServer().getOverworld().getPersistentStateManager();
        return mgr.getOrCreate(TYPE, KEY);
    }

    public boolean hasHeard(UUID uuid) {
        return heard.contains(uuid);
    }

    public void markHeard(UUID uuid) {
        if (heard.add(uuid)) markDirty();
    }

    public FirstHoldState() {}

    public static FirstHoldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        FirstHoldState s = new FirstHoldState();
        NbtList list = nbt.getList("players", NbtElement.INT_ARRAY_TYPE);
        for (int i = 0; i < list.size(); i++) {
            UUID u = NbtHelper.toUuid(list.get(i));
            s.heard.add(u);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (UUID u : heard) {
            list.add(NbtHelper.fromUuid(u));
        }
        nbt.put("players", list);
        return nbt;
    }
}
