package net.kronoz.odyssey.systems.reset;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kronoz.odyssey.init.ModBlocks;
import net.kronoz.odyssey.net.ResetFadeS2CPayload;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ResetZoneSystem {
    private static final int TRIGGER_INTERVAL_TICKS = 2;
    private static final int RESET_COOLDOWN_TICKS = 30;
    private static final int FADE_IN_TICKS = 12;
    private static final int HOLD_TICKS = 8;
    private static final int FADE_OUT_TICKS = 10;
    private static final int TELEPORT_DELAY_TICKS = 14;

    private static final Map<UUID, PendingReset> PENDING = new HashMap<>();
    private static final Object2LongOpenHashMap<UUID> COOLDOWN_UNTIL = new Object2LongOpenHashMap<>();

    private ResetZoneSystem() {
    }

    public static void init() {
        COOLDOWN_UNTIL.defaultReturnValue(Long.MIN_VALUE);
        ServerTickEvents.END_WORLD_TICK.register(ResetZoneSystem::onWorldTick);
    }

    public static void link(ServerWorld world, BlockPos zonePos, BlockPos respawnPos) {
        ResetLinkStorage.get(world).link(zonePos.toImmutable(), respawnPos.toImmutable());
    }

    public static void unlinkZone(ServerWorld world, BlockPos zonePos) {
        ResetLinkStorage.get(world).unlinkZone(zonePos.toImmutable());
    }

    public static int unlinkRespawn(ServerWorld world, BlockPos respawnPos) {
        return ResetLinkStorage.get(world).unlinkRespawn(respawnPos.toImmutable());
    }

    private static void onWorldTick(ServerWorld world) {
        if (world == null) {
            return;
        }
        processPendingTeleports(world);
        if (world.getTime() % TRIGGER_INTERVAL_TICKS != 0) {
            return;
        }

        ResetLinkStorage storage = ResetLinkStorage.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isPlayerEligible(player, world.getTime())) {
                continue;
            }

            BlockPos zone = findTriggeredZone(player, storage);
            if (zone == null) {
                continue;
            }
            BlockPos target = storage.linkedRespawn(zone);
            if (target == null) {
                continue;
            }
            scheduleReset(player, world, zone, target);
        }
    }

    private static boolean isPlayerEligible(ServerPlayerEntity player, long worldTime) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        UUID id = player.getUuid();
        if (PENDING.containsKey(id)) {
            return false;
        }
        return worldTime >= COOLDOWN_UNTIL.getLong(id);
    }

    private static @Nullable BlockPos findTriggeredZone(PlayerEntity player, ResetLinkStorage storage) {
        Box box = player.getBoundingBox().expand(0.02);
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY + 0.45);
        int maxZ = (int) Math.floor(box.maxZ);

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        ServerWorld world = (ServerWorld) player.getWorld();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    if (!world.getBlockState(mutable).isOf(ModBlocks.RESET_ZONE)) {
                        continue;
                    }
                    BlockPos zone = mutable.toImmutable();
                    if (storage.linkedRespawn(zone) != null) {
                        return zone;
                    }
                }
            }
        }
        return null;
    }

    private static void scheduleReset(ServerPlayerEntity player, ServerWorld world, BlockPos zone, BlockPos target) {
        long now = world.getTime();
        long executeAt = now + TELEPORT_DELAY_TICKS;
        PendingReset pending = new PendingReset(world.getRegistryKey().getValue().toString(), zone.toImmutable(), target.toImmutable(), executeAt);
        PENDING.put(player.getUuid(), pending);
        COOLDOWN_UNTIL.put(player.getUuid(), now + RESET_COOLDOWN_TICKS);
        ServerPlayNetworking.send(player, new ResetFadeS2CPayload(FADE_IN_TICKS, HOLD_TICKS, FADE_OUT_TICKS));
    }

    private static void processPendingTeleports(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, PendingReset>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingReset> entry = iterator.next();
            PendingReset pending = entry.getValue();
            if (!world.getRegistryKey().getValue().toString().equals(pending.dimensionKey)) {
                continue;
            }
            if (now < pending.executeAtTick) {
                continue;
            }

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            iterator.remove();
            if (player == null || !player.isAlive()) {
                continue;
            }
            teleportPlayer(world, player, pending.targetPos);
        }
    }

    private static void teleportPlayer(ServerWorld world, ServerPlayerEntity player, BlockPos targetPos) {
        if (world == null || player == null || targetPos == null) {
            return;
        }
        if (world.getBlockState(targetPos).isAir() && world.getBlockState(targetPos.up()).isAir()) {
            // Keep target as-is for ghost respawn points; otherwise nudge above solid marker.
        }
        double tx = targetPos.getX() + 0.5;
        double ty = targetPos.getY() + 0.10;
        double tz = targetPos.getZ() + 0.5;
        player.teleport(world, tx, ty, tz, player.getYaw(), player.getPitch());
        player.setVelocity(0.0, 0.0, 0.0);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
    }

    private record PendingReset(String dimensionKey, BlockPos zonePos, BlockPos targetPos, long executeAtTick) {
    }
}
