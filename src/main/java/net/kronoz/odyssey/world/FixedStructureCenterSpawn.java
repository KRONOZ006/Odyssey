package net.kronoz.odyssey.world;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class FixedStructureCenterSpawn {
    private FixedStructureCenterSpawn() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(FixedStructureCenterSpawn::applyWorldSpawnIfSaved);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            teleportToSavedCenter(server, p, true);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            MinecraftServer server = newP.getServer();
            if (server != null) teleportToSavedCenter(server, newP, true);
        });
    }

    private static void applyWorldSpawnIfSaved(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        var psm = overworld.getPersistentStateManager();
        var state = psm.getOrCreate(FixedStructurePlacerOverworld.StructuresPlacedState.TYPE,
                                    FixedStructurePlacerOverworld.StructuresPlacedState.KEY);
        if (state.hasSpawn()) {
            overworld.setSpawnPos(new BlockPos(state.spawnX, state.spawnY, state.spawnZ), 0.0f);
        }
    }

    private static void teleportToSavedCenter(MinecraftServer server, ServerPlayerEntity player, boolean giveLevitation) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        var psm = overworld.getPersistentStateManager();
        var state = psm.getOrCreate(FixedStructurePlacerOverworld.StructuresPlacedState.TYPE,
                                    FixedStructurePlacerOverworld.StructuresPlacedState.KEY);
        if (!state.hasSpawn()) return;

        double x = state.spawnX + 0.5;
        double y = state.spawnY + 0.1;
        double z = state.spawnZ + 0.5;

        player.fallDistance = 0.0f;
        player.teleport(overworld, x, y, z, player.getYaw(), player.getPitch());

        if (giveLevitation) {
            // 2.5s levitation (50 ticks*? nah, 2.5s=50 ticks? correction: 20 ticks per second -> 2.5s = 50 ticks)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 50, 0, false, false));
        }
    }
}
