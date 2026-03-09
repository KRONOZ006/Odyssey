package net.kronoz.odyssey.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public final class FixedStructureCenterSpawn {
    private static final String FIRST_JOIN_KEY = "odyssey_first_join_spawn";
    private static final String FIRST_JOIN_TAG = "odyssey:first_join_spawn_done";

    private FixedStructureCenterSpawn() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(FixedStructureCenterSpawn::applyWorldSpawnIfSaved);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            handleFirstJoin(server, player);
        });
    }

    private static void applyWorldSpawnIfSaved(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        var state = FixedStructurePlacerOverworld.getState(overworld);
        BlockPos spawn = FixedStructurePlacerOverworld.FORCED_SPAWN;
        state.spawnX = spawn.getX();
        state.spawnY = spawn.getY();
        state.spawnZ = spawn.getZ();
        state.markDirty();
        overworld.getPersistentStateManager().save();

        overworld.setSpawnPos(spawn, 0.0f);
    }

    private static void handleFirstJoin(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null || player == null) return;

        FirstJoinSpawnState state = getFirstJoinState(overworld);
        String uuid = player.getUuidAsString();
        if (state.hasJoined(uuid) || player.getCommandTags().contains(FIRST_JOIN_TAG)) {
            return;
        }

        state.markJoined(uuid);
        state.markDirty();
        player.addCommandTag(FIRST_JOIN_TAG);
        overworld.getPersistentStateManager().save();

        teleportToSavedCenter(server, player, true);
    }

    private static FirstJoinSpawnState getFirstJoinState(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(FirstJoinSpawnState.TYPE, FIRST_JOIN_KEY);
    }

    private static void teleportToSavedCenter(MinecraftServer server, ServerPlayerEntity player, boolean giveLevitation) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null || player == null) return;

        var structureState = FixedStructurePlacerOverworld.getState(overworld);
        int sx = structureState.hasSpawn() ? structureState.spawnX : FixedStructurePlacerOverworld.FORCED_SPAWN.getX();
        int sy = structureState.hasSpawn() ? structureState.spawnY : FixedStructurePlacerOverworld.FORCED_SPAWN.getY();
        int sz = structureState.hasSpawn() ? structureState.spawnZ : FixedStructurePlacerOverworld.FORCED_SPAWN.getZ();

        double x = sx + 0.5;
        double y = sy + 0.1;
        double z = sz + 0.5;

        player.fallDistance = 0.0f;
        player.teleport(overworld, x, y, z, player.getYaw(), player.getPitch());

        if (giveLevitation) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 50, 0, false, false));
        }
    }

    private static final class FirstJoinSpawnState extends PersistentState {
        private static final Type<FirstJoinSpawnState> TYPE =
                new Type<>(FirstJoinSpawnState::new, FirstJoinSpawnState::fromNbt, null);

        private final Set<String> joinedPlayers = new HashSet<>();

        private boolean hasJoined(String uuid) {
            return joinedPlayers.contains(uuid);
        }

        private void markJoined(String uuid) {
            joinedPlayers.add(uuid);
        }

        private static FirstJoinSpawnState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            FirstJoinSpawnState state = new FirstJoinSpawnState();
            NbtList list = nbt.getList("joined", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                state.joinedPlayers.add(list.getString(i));
            }
            return state;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            NbtList list = new NbtList();
            for (String uuid : joinedPlayers) {
                list.add(NbtString.of(uuid));
            }
            nbt.put("joined", list);
            return nbt;
        }
    }
}
